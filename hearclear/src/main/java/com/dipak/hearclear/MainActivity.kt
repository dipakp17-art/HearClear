package com.dipak.hearclear

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.*
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: ListeningSettings
    private lateinit var boost: SeekBar
    private lateinit var toggle: Button
    private var startingAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        volumeControlStream = AudioManager.STREAM_MUSIC
        prefs = ListeningSettings(this)
        setContentView(R.layout.activity_hearclear)
        findViewById<Button>(R.id.shareAppButton).setOnClickListener {
            val downloadUrl = "https://github.com/dipakp17-art/HearClear/releases/latest"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Try HearClear for experimental live listening assistance: $downloadUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share HearClear"))
        }
        toggle = findViewById(R.id.startButton)
        toggle.setOnClickListener {
            if (ListeningService.active) {
                stopService(Intent(this, ListeningService::class.java))
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 11)
            } else startListening()
        }
        boost = findViewById(R.id.boostBar)
        configureSlider(boost, R.id.boostValue, "gain", 19, 1, 1, "", "×")
        findViewById<Button>(R.id.boostLess).setOnClickListener { boost.progress = (boost.progress - 1).coerceAtLeast(0) }
        findViewById<Button>(R.id.boostMore).setOnClickListener { boost.progress = (boost.progress + 1).coerceAtMost(19) }
        findViewById<Switch>(R.id.softwareSwitch).apply {
            isChecked = prefs.software
            setOnCheckedChangeListener { _, value -> prefs.software = value }
        }
        findViewById<Switch>(R.id.noiseSwitch).apply {
            isChecked = prefs.androidNs
            setOnCheckedChangeListener { _, value -> prefs.androidNs = value }
        }
        findViewById<Switch>(R.id.rnnoiseSwitch).apply {
            isChecked = prefs.value("rnnoise") == 1
            setOnCheckedChangeListener { _, checked -> prefs.put("rnnoise", if (checked) 1 else 0) }
        }
        configureEar(R.id.leftLevelBar, R.id.leftLevelLabel, "leftLevel", "Left")
        configureEar(R.id.rightLevelBar, R.id.rightLevelLabel, "rightLevel", "Right")
        configureSlider(findViewById(R.id.leftToneBar), R.id.leftToneLabel, "leftTone", 12, 0, -6, "Left speech tone · ", " dB")
        configureSlider(findViewById(R.id.rightToneBar), R.id.rightToneLabel, "rightTone", 12, 0, -6, "Right speech tone · ", " dB")
        section(R.id.earsButton, R.id.earsPanel, "More settings", state?.getBoolean("ears") == true)
        section(R.id.detailsButton, R.id.detailsPanel, "Connection details", state?.getBoolean("details") == true)
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            prefs.reset()
            Toast.makeText(this, "Reset to 1×, neutral ear settings and both filters off.", Toast.LENGTH_LONG).show()
            boost.progress = 0
            findViewById<Switch>(R.id.softwareSwitch).isChecked = false
            findViewById<Switch>(R.id.noiseSwitch).isChecked = false
            findViewById<Switch>(R.id.rnnoiseSwitch).isChecked = false
            findViewById<VerticalLevelControl>(R.id.leftLevelBar).level = 100
            findViewById<VerticalLevelControl>(R.id.rightLevelBar).level = 100
            findViewById<SeekBar>(R.id.leftToneBar).progress = 6
            findViewById<SeekBar>(R.id.rightToneBar).progress = 6
            render()
        }
        findViewById<Button>(R.id.licensesButton).setOnClickListener {
            val licence = resources.openRawResource(R.raw.rnnoise_license).bufferedReader().use { it.readText() }
            android.app.AlertDialog.Builder(this).setTitle("RNNoise • BSD licence")
                .setMessage(licence).setPositiveButton("Close", null).show()
        }
        render()
    }
    private fun configureSlider(bar: SeekBar, labelId: Int, key: String, maximum: Int,
                                fallback: Int, offset: Int, prefix: String, suffix: String) {
        val label = findViewById<TextView>(labelId)
        bar.max = maximum
        bar.progress = (prefs.value(key, fallback) - offset).coerceIn(0, maximum)
        fun display() {
            val value = bar.progress + offset
            label.text = "$prefix$value$suffix"
            bar.contentDescription = when (key) {
                "gain" -> "Sound boost, $value times"
                else -> "$prefix$value$suffix"
            }
        }
        display()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, user: Boolean) {
                prefs.put(key, progress + offset)
                display()
                if (key == "gain") updateBoostButtons()
            }
            override fun onStartTrackingTouch(view: SeekBar) {}
            override fun onStopTrackingTouch(view: SeekBar) {}
        })
    }
    private fun updateBoostButtons() {
        findViewById<Button>(R.id.boostLess).isEnabled = boost.progress > 0
        findViewById<Button>(R.id.boostMore).isEnabled = boost.progress < boost.max
    }
    private fun section(buttonId: Int, panelId: Int, title: String, initiallyOpen: Boolean) {
        val button = findViewById<Button>(buttonId)
        val panel = findViewById<View>(panelId)
        fun show(open: Boolean) {
            panel.visibility = if (open) View.VISIBLE else View.GONE
            button.text = "$title  ${if (open) "▴" else "▾"}"
            button.contentDescription = "$title, ${if (open) "expanded" else "collapsed"}"
        }
        show(initiallyOpen)
        button.setOnClickListener { show(panel.visibility != View.VISIBLE) }
    }
    private fun configureEar(id: Int, labelId: Int, key: String, name: String) {
        val control = findViewById<VerticalLevelControl>(id)
        val label = findViewById<TextView>(labelId)
        fun display(value: Int) {
            label.text = "$name · $value%"
            control.contentDescription = "$name ear level, $value percent"
        }
        control.level = prefs.value(key, 100)
        display(control.level)
        control.onLevelChanged = { value -> prefs.put(key, value); display(value) }
    }
    private fun connectedHeadphones(): AudioDeviceInfo? =
        getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in intArrayOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID) }
            .sortedBy { if (it.type in intArrayOf(3,4,22)) 0 else 1 }.firstOrNull()

    private fun startListening() {
        val headphones = connectedHeadphones()
        if (headphones == null) {
            Toast.makeText(this, "Connect headphones first.", Toast.LENGTH_SHORT).show()
            return
        }
        startingAt = SystemClock.elapsedRealtime()
        runCatching { startForegroundService(Intent(this, ListeningService::class.java)) }
            .onFailure {
                startingAt = 0
                Toast.makeText(this, "Could not start listening: ${it.message}", Toast.LENGTH_LONG).show()
            }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        render()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 11) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
            else Toast.makeText(this, "Microphone permission is needed to hear nearby sound.", Toast.LENGTH_LONG).show()
        }
    }
    private fun render() {
        if (!::toggle.isInitialized) return
        val active = ListeningService.active
        if (active || SystemClock.elapsedRealtime() - startingAt > 3000) startingAt = 0
        val pending = startingAt != 0L
        val headphones = connectedHeadphones()
        val details = ListeningService.diagnostics
        val connectionText = when {
            active -> "Connected · listening"
            headphones != null -> "Headphones connected"
            else -> "Headphones not connected"
        }
        findViewById<TextView>(R.id.connectionStatus).apply {
            if (text.toString() != connectionText) text = connectionText
        }
        findViewById<ImageView>(R.id.headphoneIcon).alpha = if (headphones != null) 1f else 0.35f
        volumeControlStream = AudioManager.STREAM_MUSIC
        findViewById<TextView>(R.id.listeningTitle).text = when {
            active -> "You’re listening"
            pending -> "Starting…"
            else -> "Ready when you are"
        }
        findViewById<TextView>(R.id.listeningHint).text = when {
            active -> "Place the mic near the speaker."
            headphones != null -> "Set a comfortable phone volume."
            else -> "Connect headphones to begin."
        }
        toggle.text = when {
            active -> "Stop listening"
            pending -> "Starting…"
            headphones == null -> "Connect headphones to start"
            else -> "Start listening"
        }
        toggle.isEnabled = active || (!pending && headphones != null)
        toggle.alpha = if (toggle.isEnabled) 1f else 0.55f
        val error = !active && !pending && details.isNotBlank() && !details.startsWith("Stopped") && !details.startsWith("Mic: ")
        findViewById<TextView>(R.id.listeningError).apply {
            visibility = if (error) View.VISIBLE else View.GONE
            val message = if (error) details else ""
            if (text.toString() != message) text = message
        }
        findViewById<TextView>(R.id.diagnosticsText).text = details
        val rnStatus = details.lineSequence().firstOrNull { it.startsWith("RNNoise:") }
        findViewById<TextView>(R.id.rnnoiseHint).text = rnStatus ?:
            "Offline neural noise reduction. Try it alone first; 48 kHz required."

        updateBoostButtons()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("ears", findViewById<View>(R.id.earsPanel).visibility == View.VISIBLE)
        outState.putBoolean("details", findViewById<View>(R.id.detailsPanel).visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); handler.removeCallbacks(poll); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }
}

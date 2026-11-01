package com.dipak.hearclear

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.NoiseSuppressor
import android.os.PowerManager
import android.os.Process
import android.util.Log
import java.util.Locale

/** Standalone capture/playback worker: no classifier, monitor, recording or networking. */
class ListeningEngine(
    private val context: Context,
    private val settings: ListeningSettings,
    private val status: (String) -> Unit,
    private val finished: () -> Unit
) {
    @Volatile private var running = false
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    fun start() {
        if (running) return
        running = true
        worker = Thread({ loop() }, "HearClearAudio").also { it.start() }
    }
    fun stop() {
        running = false
        runCatching { recorder?.stop() } // unblock read; worker owns release
    }
    private fun headphones(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> false
    }
    private fun label(d: AudioDeviceInfo?) = d?.let { "${it.productName} (type ${it.type}, id ${it.id})" } ?: "Unknown / warming up"

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun loop() {
        var record: AudioRecord? = null
        var track: AudioTrack? = null
        var ns: NoiseSuppressor? = null
        var wake: PowerManager.WakeLock? = null
        var neural: RnNoiseDenoiser? = null
        var neuralSetting: Boolean? = null
        var neuralStatus = "off"
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val am = context.getSystemService(AudioManager::class.java)
            val output = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { headphones(it) }
                .sortedBy { if (it.type in setOf(3, 4, 22)) 0 else 1 }.firstOrNull()
                ?: error("Connect wired/USB or Bluetooth media headphones first.")
            var rate = 48000
            for (candidate in listOf(48000, 16000)) {
                val minimum = AudioRecord.getMinBufferSize(candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) continue
                val opened = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum, candidate / 10)) }.getOrNull() ?: continue
                if (opened.state == AudioRecord.STATE_INITIALIZED) { record = opened; rate = candidate; break }
                opened.release()
            }
            val input = record ?: error("Microphone unavailable.")
            recorder = input
            val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Stereo playback unavailable." }
            val player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(minimum, rate * 4 / 25))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track = player
            check(player.setPreferredDevice(output)) { "Headphone route preference was rejected." }
            ns = runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(input.audioSessionId) else null }.getOrNull()
            val pm = context.getSystemService(PowerManager::class.java)
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HearClear:audio")
            wake.acquire()
            val leftProcessor = LiveAssistProcessor(rate)
            val rightProcessor = LiveAssistProcessor(rate)
            val mixer = StereoPcm(rate)
            val mono = ShortArray(rate / 100)
            val left = ShortArray(mono.size)
            val right = ShortArray(mono.size)
            val stereo = ShortArray(mono.size * 2)
            var lastStatus = 0L
            var nsWanted: Boolean? = null
            var nsState = "Unavailable"
            input.startRecording()
            player.play()
            while (running) {
                check(AccountClient.canListen()) { "Account verification expired. Open Account to reconnect or activate." }
                val wanted = settings.androidNs
                if (wanted != nsWanted) {
                    nsWanted = wanted
                    nsState = runCatching {
                        val effect = ns
                        if (effect == null) "Unavailable" else {
                            val result = effect.setEnabled(wanted)
                            "actual ${if (effect.enabled) "on" else "off"}, control ${effect.hasControl()}, request result $result"
                        }
                    }.getOrElse { "Effect error" }
                }
                check(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.id == output.id }) { "Headphones disconnected. Listening stopped." }
                val routed = player.routedDevice
                check(routed == null || headphones(routed)) { "Output changed away from headphones. Listening stopped." }
                val readStart = System.nanoTime()
                val n = input.read(mono, 0, mono.size)
                if (!running) break
                check(n > 0) { "Microphone read failed ($n)." }
                val readMs = (System.nanoTime() - readStart) / 1e6
                val processStart = System.nanoTime()
                val wantNeural = settings.value("rnnoise") == 1
                if (wantNeural != neuralSetting) {
                    neural?.close()
                    neural = null
                    neuralSetting = wantNeural
                    neuralStatus = when {
                        !wantNeural -> "off"
                        rate != 48000 -> "not active: 48 kHz capture is required (current $rate Hz)"
                        else -> runCatching {
                            neural = RnNoiseDenoiser()
                            "on • offline model • adds buffering; end-to-end delay unmeasured"
                        }.getOrElse { "not active: ${it.message}" }
                    }
                }
                // One denoiser before ear-specific processing; the two ears stay time aligned.
                neural?.process(mono, n)

                mono.copyInto(left, endIndex = n); mono.copyInto(right, endIndex = n)
                val gain = settings.gain
                val software = settings.software
                leftProcessor.process(left, n, gain, 0, settings.value("leftTone").coerceIn(-6,6), 0, software)
                rightProcessor.process(right, n, gain, 0, settings.value("rightTone").coerceIn(-6,6), 0, software)
                mixer.mix(left, right, stereo, n, settings.value("leftLevel",100), settings.value("rightLevel",100))
                var offset = 0
                while (running && offset < n * 2) {
                    val written = player.write(stereo, offset, n * 2 - offset, AudioTrack.WRITE_BLOCKING)
                    check(written > 0) { "Headphone write failed ($written)." }
                    offset += written
                }
                val workMs = (System.nanoTime() - processStart) / 1e6
                if (System.currentTimeMillis() - lastStatus >= 500) {
                    lastStatus = System.currentTimeMillis()
                    nsState = runCatching {
                        val effect = ns
                        if (effect == null) "Unavailable" else
                            "actual ${if (effect.enabled) "on" else "off"}, control ${effect.hasControl()}"
                    }.getOrElse { "Status unavailable" }
                    val message = "Mic: ${label(input.routedDevice)}\nOutput: ${label(player.routedDevice)}" +
                        "\nRNNoise: $neuralStatus" +
                        "\nMicrophone: system default (see actual Mic route above)" +
                        "\nSoftware: $software • Android NS requested: $wanted, $nsState" +
                        "\n$rate Hz • boost up to ${gain}× • underruns ${player.underrunCount}" +
                        String.format(Locale.US, "\nRead %.1f ms • processing/write %.1f ms", readMs, workMs) +
                        "\nEnd-to-end acoustic delay: unmeasured. Output is not calibrated to safe ear-level loudness."
                    status(message)
                    if (lastStatus % 5000 < 600) Log.i("HearClearDiagnostics", message)
                }
            }
            status("Stopped.")
        } catch (e: Exception) {
            status(if (running) e.message ?: "Audio failed." else "Stopped.")
        } finally {
            running = false
            runCatching { record?.stop() }
            runCatching { track?.pause(); track?.flush(); track?.release() }
            runCatching { neural?.close() }
            runCatching { ns?.release() }
            runCatching { record?.release() }
            recorder = null
            runCatching { if (wake?.isHeld == true) wake.release() }
            finished()
        }
    }
}




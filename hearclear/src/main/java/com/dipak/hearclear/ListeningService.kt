package com.dipak.hearclear

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class ListeningService : Service() {
    private var engine: ListeningEngine? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (engine != null) return START_NOT_STICKY
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("listening", "Live listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "listening").setContentTitle("HearClear is listening")
            .setContentText("Microphone to headphones • tap Stop to finish")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(91, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(91, notification)
        active = true
        engine = ListeningEngine(applicationContext, ListeningSettings(this),
            { diagnostics = it }, { active = false; stopSelf() }).also { it.start() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        engine?.stop()
        engine = null
        active = false
        super.onDestroy()
    }
    companion object {
        @Volatile var active = false
        @Volatile var diagnostics = "Stopped. Connect headphones, then start."
    }
}


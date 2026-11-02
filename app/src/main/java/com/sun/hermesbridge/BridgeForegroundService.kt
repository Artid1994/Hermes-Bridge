package com.sun.hermesbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class BridgeForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "com.sun.hermesbridge.STOP"
        const val ACTION_KILL = "com.sun.hermesbridge.KILL"
        private const val CHANNEL = "bridge"
    }

    private var server: BridgeServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                server?.stop()
                server = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_KILL -> {
                BridgeState.killed = true
                BridgeState.log("KILL via notification")
                return START_STICKY
            }
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hermes Bridge", NotificationManager.IMPORTANCE_LOW))

        val pf = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val kill = PendingIntent.getService(
            this, 1, Intent(this, BridgeForegroundService::class.java).setAction(ACTION_KILL), pf
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, BridgeForegroundService::class.java).setAction(ACTION_STOP), pf
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hermes Bridge running")
            .setContentText("Local API on 127.0.0.1:${BridgeServer.PORT}")
            .addAction(Notification.Action.Builder(null, "KILL SWITCH", kill).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        if (server == null) server = BridgeServer(applicationContext).also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}

package com.sun.hermesbridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class BridgeNotificationListener : NotificationListenerService() {

    companion object {
        private val recent = ArrayDeque<JSONObject>()

        fun snapshot(): JSONArray = synchronized(recent) { JSONArray(recent.toList()) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in Config.allowed(applicationContext)) return
        val e = sbn.notification.extras
        val o = JSONObject()
            .put("pkg", sbn.packageName)
            .put("title", e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
            .put("text", e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")
            .put("time", sbn.postTime)
        synchronized(recent) {
            recent.addLast(o)
            while (recent.size > 100) recent.removeFirst()
        }
    }
}

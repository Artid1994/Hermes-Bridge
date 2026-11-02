package com.sun.hermesbridge

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this)
        col.addView(status)

        fun btn(label: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener { onClick() }
            col.addView(b)
        }

        btn("1) Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2) Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        btn("3) Start bridge") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            startForegroundService(Intent(this, BridgeForegroundService::class.java))
            refresh()
        }
        btn("Stop bridge") {
            startService(Intent(this, BridgeForegroundService::class.java).setAction(BridgeForegroundService.ACTION_STOP))
            refresh()
        }
        btn("Resume (clear kill switch)") {
            BridgeState.killed = false
            BridgeState.log("kill switch cleared")
            refresh()
        }
        btn("Copy API token") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("token", Config.token(this)))
        }

        col.addView(TextView(this).apply { text = "\nAllowed packages (comma separated):" })
        val allowed = EditText(this)
        allowed.setText(Config.allowedRaw(this))
        col.addView(allowed)
        btn("Save allowed packages") {
            Config.setAllowed(this, allowed.text.toString())
            refresh()
        }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val a11y = if (BridgeAccessibilityService.instance != null) "ON" else "OFF"
        status.text = "Accessibility: $a11y\nKill switch: ${BridgeState.killed}\n" +
            "API: 127.0.0.1:${BridgeServer.PORT}\nAllowed: ${Config.allowedRaw(this)}"
    }
}

package com.sun.hermesbridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BridgeAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        BridgeState.log("accessibility connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun dumpScreen(): JSONObject {
        val root = rootInActiveWindow ?: return JSONObject().put("error", "no_window")
        val arr = JSONArray()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || arr.length() >= 400) return
            val r = Rect()
            n.getBoundsInScreen(r)
            val text = if (n.isPassword) "" else (n.text?.toString() ?: "")
            val desc = if (n.isPassword) "" else (n.contentDescription?.toString() ?: "")
            if (text.isNotBlank() || desc.isNotBlank() || n.isClickable || n.isEditable) {
                arr.put(
                    JSONObject()
                        .put("text", text)
                        .put("desc", desc)
                        .put("id", n.viewIdResourceName ?: "")
                        .put("clickable", n.isClickable)
                        .put("editable", n.isEditable)
                        .put("cx", r.centerX())
                        .put("cy", r.centerY())
                        .put("bounds", "${r.left},${r.top},${r.right},${r.bottom}")
                )
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return JSONObject().put("package", root.packageName?.toString() ?: "").put("nodes", arr)
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null && !cur.isClickable) cur = cur.parent
            if (cur != null && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        if (target.isPassword) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val f = findEditable(n.getChild(i))
            if (f != null) return f
        }
        return null
    }

    fun tap(x: Float, y: Float): Boolean = gesture(x, y, x, y, 50)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean =
        gesture(x1, y1, x2, y2, ms.coerceIn(50, 3000))

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) lineTo(x2, y2)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun global(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }

    fun launch(pkg: String): Boolean = try {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) false else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            true
        }
    } catch (e: Exception) {
        false
    }
}

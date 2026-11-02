package com.sun.hermesbridge

import android.content.Context
import java.util.UUID

object BridgeState {
    @Volatile var killed = false
    private val entries = ArrayDeque<String>()

    fun log(msg: String) {
        synchronized(entries) {
            entries.addLast("${System.currentTimeMillis()} $msg")
            while (entries.size > 200) entries.removeFirst()
        }
    }

    fun logLines(): List<String> = synchronized(entries) { entries.toList() }
}

object Config {
    private const val DEFAULT_ALLOWED = "jp.naver.line.android,com.android.chrome"
    private fun prefs(c: Context) = c.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun token(c: Context): String {
        val p = prefs(c)
        val existing = p.getString("token", null)
        if (existing != null) return existing
        val t = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        p.edit().putString("token", t).apply()
        return t
    }

    fun allowedRaw(c: Context): String = prefs(c).getString("allowed", DEFAULT_ALLOWED) ?: DEFAULT_ALLOWED

    fun allowed(c: Context): Set<String> =
        allowedRaw(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun setAllowed(c: Context, raw: String) {
        prefs(c).edit().putString("allowed", raw).apply()
    }
}

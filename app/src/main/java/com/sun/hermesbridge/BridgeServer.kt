package com.sun.hermesbridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class BridgeServer(private val ctx: Context) {

    companion object { const val PORT = 8765 }

    private var server: ServerSocket? = null
    @Volatile private var running = false
    @Volatile private var lastAction = 0L
    private val clientSlots = Semaphore(8)

    fun start() {
        if (running) return
        running = true
        thread(name = "bridge-server") {
            try {
                val ss = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
                server = ss
                BridgeState.log("server listening on 127.0.0.1:$PORT")
                while (running) {
                    val s = ss.accept()
                    if (!clientSlots.tryAcquire()) {
                        try { respond(s, 429, err("server_busy")) } catch (_: Exception) {}
                        try { s.close() } catch (_: Exception) {}
                    } else {
                        thread(name = "bridge-client") {
                            try { handle(s) } finally { clientSlots.release() }
                        }
                    }
                }
            } catch (e: Exception) {
                BridgeState.log("server stopped: ${e.message}")
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
    }

    private fun readLine(ins: InputStream): String? {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = ins.read()
            if (b < 0) return if (sb.size() == 0) null else sb.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.write(b)
            if (sb.size() > 8192) return null
        }
        return sb.toString("UTF-8")
    }

    private fun handle(s: Socket) {
        try {
            s.use {
                it.soTimeout = 5000
                val ins = BufferedInputStream(it.getInputStream())
                val reqLine = readLine(ins) ?: return
                val parts = reqLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore("?")
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(ins) ?: break
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                if (len < 0 || len > 65536) {
                    respond(it, 413, err("request_body_too_large"))
                    return
                }
                var body = ""
                if (len in 1..65536) {
                    val buf = ByteArray(len)
                    var off = 0
                    while (off < len) {
                        val n = ins.read(buf, off, len - off)
                        if (n < 0) break
                        off += n
                    }
                    body = String(buf, 0, off, Charsets.UTF_8)
                }

                val expected = "Bearer " + Config.token(ctx)
                val got = headers["authorization"] ?: ""
                if (!MessageDigest.isEqual(got.toByteArray(), expected.toByteArray())) {
                    respond(it, 401, err("unauthorized"))
                    return
                }

                val (code, json) = try {
                    route(method, path, if (body.isBlank()) JSONObject() else JSONObject(body))
                } catch (e: JSONException) {
                    400 to err("bad_json_or_missing_field: ${e.message}")
                }
                respond(it, code, json)
            }
        } catch (e: Exception) {
            BridgeState.log("handler error: ${e.message}")
        }
    }

    private fun err(msg: String) = JSONObject().put("error", msg)

    private fun done(ok: Boolean, what: String): Pair<Int, JSONObject> {
        BridgeState.log("$what -> $ok")
        return 200 to JSONObject().put("ok", ok)
    }

    private fun route(method: String, path: String, body: JSONObject): Pair<Int, JSONObject> {
        val svc = BridgeAccessibilityService.instance

        when (path) {
            "/status" -> return 200 to JSONObject()
                .put("accessibility", svc != null)
                .put("killed", BridgeState.killed)
                .put("package", svc?.currentPackage() ?: "")
            "/log" -> return 200 to JSONObject().put("log", JSONArray(BridgeState.logLines()))
            "/stop" -> {
                BridgeState.killed = true
                BridgeState.log("KILL via api")
                return 200 to JSONObject().put("ok", true)
            }
        }

        if (BridgeState.killed) return 503 to err("killed")

        if (path == "/notifications") return 200 to JSONObject().put("notifications", BridgeNotificationListener.snapshot())

        if (svc == null) return 503 to err("accessibility_not_enabled")

        val postOnly = setOf("/tap", "/click", "/type", "/swipe", "/global", "/launch")
        if (path in postOnly && method != "POST") return 405 to err("use_POST")

        synchronized(this) {
            val now = System.currentTimeMillis()
            if (now - lastAction < 250) return 429 to err("too_fast")
            lastAction = now
        }

        val allowed = Config.allowed(ctx)
        val pkg = svc.currentPackage()
        if (path != "/launch" && (pkg == null || pkg !in allowed)) {
            return 403 to err("package_not_allowed: $pkg")
        }

        return when (path) {
            "/screen" -> 200 to svc.dumpScreen()
            "/tap" -> done(svc.tap(body.getDouble("x").toFloat(), body.getDouble("y").toFloat()), "tap")
            "/click" -> {
                val t = body.getString("text")
                done(svc.clickText(t), "click '${t.take(30)}'")
            }
            "/type" -> {
                val t = body.getString("text")
                done(svc.typeText(t), "type len=${t.length}")
            }
            "/swipe" -> done(
                svc.swipe(
                    body.getDouble("x1").toFloat(), body.getDouble("y1").toFloat(),
                    body.getDouble("x2").toFloat(), body.getDouble("y2").toFloat(),
                    body.optLong("ms", 300)
                ),
                "swipe"
            )
            "/global" -> {
                val a = body.getString("action")
                done(svc.global(a), "global $a")
            }
            "/launch" -> {
                val p = body.getString("package")
                if (p !in allowed) 403 to err("package_not_allowed: $p") else done(svc.launch(p), "launch $p")
            }
            else -> 404 to err("not_found")
        }
    }

    private fun respond(s: Socket, code: Int, json: JSONObject) {
        val b = json.toString().toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code X\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${b.size}\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply {
            write(head.toByteArray())
            write(b)
            flush()
        }
    }
}

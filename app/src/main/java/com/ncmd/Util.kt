package com.ncmd

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Log {
    @Volatile private var file: File? = null
    private val lock = Any()
    fun init(ctx: Context) { file = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs/app.log").also { it.parentFile?.mkdirs() } }
    fun i(tag: String, msg: String) { write("I", tag, msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) { write("E", tag, msg + (t?.let { " | " + it.javaClass.simpleName + ": " + (it.message ?: "") } ?: "")) }
    private fun write(lv: String, tag: String, msg: String) {
        val line = ts() + " " + lv + "/" + tag + " " + sanitize(msg)
        if (lv == "E") android.util.Log.e("NCMD", line) else android.util.Log.i("NCMD", line)
        val f = file ?: return
        try { synchronized(lock) { if (f.length() > 2_000_000) f.writeText(""); f.appendText(line + "\n") } } catch (_: Throwable) {}
    }
    private fun sanitize(s: String) = s.replace(Regex("(?i)(cookie|token|musk|password)=[^& ;]+"), "$1=***")
    fun ts(): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
    fun tail(n: Int): String = try { file?.readLines()?.takeLast(n)?.joinToString("\n") ?: "" } catch (e: Throwable) { "" }
}

object Http {
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

    class Resp(val code: Int, val body: String, val finalUrl: String = "")

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeout: Int = 15000): Resp =
        request("GET", url, null, headers, timeout)

    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap(), timeout: Int = 15000): Resp {
        val body = form.entries.joinToString("&") { urlEncode(it.key) + "=" + urlEncode(it.value) }
        return request("POST", url, body, headers + mapOf("Content-Type" to "application/x-www-form-urlencoded"), timeout)
    }

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>, timeout: Int): Resp {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Length", bytes.size.toString())
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream: InputStream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return Resp(code, text, conn?.url?.toString() ?: url)
        } finally { try { conn?.disconnect() } catch (_: Throwable) {} }
    }
}

fun md5Of(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
fun md5OfFile(f: File): String {
    val md = MessageDigest.getInstance("MD5")
    f.inputStream().use { ins -> val buf = ByteArray(1 shl 16); while (true) { val n = ins.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) }
}
fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> b.toString() + " B"
}
fun fmtDuration(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
fun nowMs(): Long = System.currentTimeMillis()
fun tsStr(ms: Long = nowMs()): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
fun safeFileName(s: String): String {
    var t = s.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().trimEnd('.')
    if (t.isEmpty()) t = "未命名"
    if (t.length > 120) t = t.substring(0, 120)
    return t
}

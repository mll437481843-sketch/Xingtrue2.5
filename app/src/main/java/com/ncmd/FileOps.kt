package com.ncmd

import android.content.Context
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileOps {

    private var appCtx: android.content.Context? = null

    fun init(c: android.content.Context) { appCtx = c.applicationContext }

    fun rootDir(): File {
        val configured = Settings.downloadDir
        val main = if (configured.isBlank())
            File(Environment.getExternalStorageDirectory(), "Download/云音乐管家") else File(configured)
        if (!main.exists()) try { main.mkdirs() } catch (_: Throwable) {}
        if (main.canWrite()) return main
        val alt = appCtx?.let { File(it.getExternalFilesDir(null), "Music") }
        if (alt != null) {
            if (!alt.exists()) alt.mkdirs()
            if (alt.canWrite()) return alt
        }
        return main
    }

    fun freeSpace(): Long = try { StatFs(rootDir().absolutePath).availableBytes } catch (e: Throwable) { 0L }

    fun targetDir(task: DownloadTask): File {
        val base = rootDir()
        val dir = when (Settings.classifyMode) {
            "artist" -> File(base, safeFileName(task.artist.ifBlank { "未知歌手" }))
            "album" -> File(base, safeFileName(task.album.ifBlank { "未知专辑" }))
            "playlist" -> File(base, safeFileName(task.playlistName.ifBlank { "未分类" }))
            "date" -> File(base, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            else -> base
        }
        if (Settings.autoCreateFolder && !dir.exists()) dir.mkdirs()
        return dir
    }

    fun renderName(task: DownloadTask): String {
        var tpl = Settings.nameTemplate.ifBlank { "{歌手} - {歌曲名称}" }
        tpl = tpl.replace("{歌手}", task.artist.ifBlank { "未知歌手" })
            .replace("{歌曲名称}", task.title.ifBlank { "未知歌曲" })
            .replace("{专辑}", task.album.ifBlank { "未知专辑" })
            .replace("{序号}", String.format(Locale.US, "%02d", task.position + 1))
            .replace("{音质}", task.quality.ifBlank { "standard" })
            .replace("{年份}", "")
        return safeFileName(tpl)
    }

    fun uniqueFile(dir: File, base: String, ext: String): File {
        var f = File(dir, base + "." + ext)
        if (!f.exists()) return f
        var i = 2
        while (f.exists() && i < 500) { f = File(dir, base + " (" + i + ")." + ext); i++ }
        return f
    }

    fun existSamePlatformSong(ctx: Context, platformId: String): JSONObject? {
        val rec = Db.get(ctx).songByPlatformId(platformId) ?: return null
        val p = rec.optString("filePath")
        if (p.isNotBlank() && File(p).exists() && File(p).length() > 0) return rec
        return null
    }

    fun findSimilar(dir: File, title: String, artist: String): File? {
        if (!dir.exists()) return null
        val norm = { s: String -> s.replace(Regex("[\\s._\\-（）()【】\\[\\]]"), "").lowercase() }
        val t = norm(title); val a = norm(artist)
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".mp3", true) || f.name.endsWith(".flac", true) || f.name.endsWith(".m4a", true) || f.name.endsWith(".ogg", true)) } ?: return null
        for (f in files) {
            val n = norm(f.nameWithoutExtension)
            if (n.contains(t) && (a.isBlank() || n.contains(a))) return f
        }
        return null
    }

    fun saveLyric(dir: File, base: String, lrc: String): File? {
        return try {
            var text = lrc
            if (Settings.lyricFormat == "txt") text = lrc.lines().mapNotNull { line ->
                val i = line.indexOf(']'); if (line.startsWith("[") && i > 0) line.substring(i + 1).trim() else line.trim()
            }.filter { it.isNotBlank() }.joinToString("\n")
            if (text.isBlank()) return null
            val f = File(dir, base + "." + Settings.lyricFormat)
            f.writeText(text, Charsets.UTF_8)
            f
        } catch (e: Throwable) { Log.e("file", "saveLyric", e); null }
    }

    fun saveCover(dir: File, base: String, bytes: ByteArray): File? {
        return try {
            if (bytes.isEmpty()) return null
            val ext = if (bytes.size > 3 && bytes[0].toInt() == 0x89 && bytes[1].toInt() == 0x50) "png" else Settings.coverFormat.ifBlank { "jpg" }
            val f = File(dir, base + "." + ext)
            f.writeBytes(bytes)
            f
        } catch (e: Throwable) { Log.e("file", "saveCover", e); null }
    }

    fun fetchCover(url: String): ByteArray? {
        if (url.isBlank()) return null
        return try {
            val r = Http.get(url, mapOf("Referer" to "https://music.163.com/"), 15000)
            if (r.code == 200) r.body.toByteArray(Charsets.ISO_8859_1) else null
        } catch (e: Throwable) { null }
    }

    fun deleteQuiet(f: File?): Boolean = try { f != null && f.exists() && f.delete() } catch (e: Throwable) { false }

    fun totalSizeOf(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

package com.ncmd

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Downloader(private val ctx: Context) {

    class Aborted : Exception("任务已取消")
    class Retryable(msg: String) : Exception(msg)
    class Fatal(msg: String) : Exception(msg)

    fun run(task: DownloadTask, isCanceled: () -> Boolean, onUpdate: (DownloadTask) -> Unit): DownloadTask {
        var t = task.copy(status = TaskStatus.PARSING, progress = 0, errorMessage = "", speed = 0)
        onUpdate(t)
        if (isCanceled()) throw Aborted()

        val src = NeteaseApi.playUrl(t.platformId, t.quality)
        if (isCanceled()) throw Aborted()
        if (!src.ok || src.trial) {
            val why = if (!src.ok) src.reason
            else "平台仅提供试听片段：需在「设置 → 账号 Cookie」填入你自己的登录 Cookie，且账号拥有该曲目权限后才可获得完整音频"
            t = t.copy(status = TaskStatus.UNAVAILABLE, errorMessage = why, updatedAt = nowMs())
            onUpdate(t)
            return t
        }
        t = t.copy(totalBytes = src.size, status = TaskStatus.RUNNING, updatedAt = nowMs())
        onUpdate(t)

        // 按平台 ID 去重
        val same = FileOps.existSamePlatformSong(ctx, t.platformId)
        if (same != null && Settings.skipDuplicate) {
            val existFile = File(same.optString("filePath"))
            t = t.copy(status = TaskStatus.DONE, progress = 100, filePath = existFile.absolutePath,
                totalBytes = existFile.length(), completedAt = nowMs(), errorMessage = "已存在同曲目文件，已跳过")
            onUpdate(t)
            return t
        }

        val dir = FileOps.targetDir(t)
        if (!dir.exists() && !dir.mkdirs()) throw Fatal("无法创建目录：" + dir.absolutePath)
        if (!dir.canWrite()) throw Fatal("目录不可写：" + dir.absolutePath)

        val need = if (src.size > 0) src.size else 5L * 1024 * 1024
        if (FileOps.freeSpace() < need * 2) throw Fatal("磁盘空间不足，需要约 " + humanSize(need * 2))

        val baseName = FileOps.renderName(t)
        var finalFile = File(dir, baseName + "." + src.format.ifBlank { "mp3" })
        if (finalFile.exists() && finalFile.length() > 0) {
            when (Settings.duplicateAction) {
                "skip" -> {
                    t = t.copy(status = TaskStatus.DONE, progress = 100, filePath = finalFile.absolutePath,
                        totalBytes = finalFile.length(), completedAt = nowMs(), errorMessage = "同名文件已存在，已跳过")
                    onUpdate(t); return t
                }
                "rename" -> finalFile = FileOps.uniqueFile(dir, baseName, src.format.ifBlank { "mp3" })
                else -> { /* overwrite */ }
            }
        }
        val part = File(dir, finalFile.name + ".part")
        val startAt = if (part.exists()) part.length() else 0L
        download(t, src.url, part, startAt, src.size, isCanceled, onUpdate)
        if (isCanceled()) throw Aborted()
        val len = part.length()
        if (len < 10240) { FileOps.deleteQuiet(part); throw Retryable("下载内容不完整（" + len + " 字节）") }
        if (src.size > 0 && len < src.size) { throw Retryable("下载中断（" + len + "/" + src.size + "）") }
        if (finalFile.exists()) finalFile.delete()
        if (!part.renameTo(finalFile)) { FileOps.deleteQuiet(part); throw Fatal("无法写入文件：" + finalFile.absolutePath) }

        val durSec = t.duration
        if (durSec > 0 && src.bitrate > 0) {
            val est = (finalFile.length() * 8 / src.bitrate).toInt()
            if (est < (durSec * 0.85).toInt()) {
                FileOps.deleteQuiet(finalFile)
                throw Fatal("音频不完整（约 " + est + " 秒 / 歌曲 " + durSec + " 秒），已丢弃，避免把试听片段当完整歌曲保存")
            }
        }

        // 后处理：封面 / 歌词 / 标签
        var cover: ByteArray? = null
        try { cover = FileOps.fetchCover(t.coverUrl) } catch (_: Throwable) {}
        val lyric = try { NeteaseApi.lyric(t.platformId) } catch (e: Throwable) { NeteaseApi.Lyric() }
        if (Settings.saveCover && cover != null) FileOps.saveCover(dir, finalFile.nameWithoutExtension, cover)
        if (Settings.saveLyric && lyric.ok) FileOps.saveLyric(dir, finalFile.nameWithoutExtension, lyric.lrc)
        if (Settings.writeMeta && finalFile.name.endsWith(".mp3", true)) {
            Thread {
                Tagger.writeMp3(finalFile, t.title, t.artist, t.album, t.position + 1, "", cover, lyric.lrc)
            }.start()
        }
        val md5 = try { md5OfFile(finalFile) } catch (e: Throwable) { "" }
        try {
            Db.get(ctx).markDownloaded(t.platformId, finalFile.absolutePath, finalFile.length(), md5)
            Db.get(ctx).addHistory(t.platformId, t.title, t.artist, t.album, finalFile.absolutePath,
                finalFile.length(), src.format, if (t.taskType == "playlist") "歌单" else "单曲")
        } catch (e: Throwable) { Log.e("dle", "record", e) }

        t = t.copy(status = TaskStatus.DONE, progress = 100, filePath = finalFile.absolutePath,
            totalBytes = finalFile.length(), speed = 0, completedAt = nowMs(), errorMessage = "")
        onUpdate(t)
        return t
    }

    private fun download(t0: DownloadTask, url: String, part: File, startAt: Long, expectSize: Long,
                         isCanceled: () -> Boolean, onUpdate: (DownloadTask) -> Unit) {
        var conn: HttpURLConnection? = null
        var ins: java.io.InputStream? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = Settings.timeoutSec * 1000
            conn.readTimeout = Settings.timeoutSec * 1000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 13; Mobile)")
            conn.setRequestProperty("Referer", "https://music.163.com/")
            if (startAt > 0) conn.setRequestProperty("Range", "bytes=" + startAt + "-")
            val code = conn.responseCode
            if (code !in 200..299) throw Retryable("服务器返回 " + code)
            var start = startAt
                        if (start > 0 && code != 206) { part.delete(); start = 0 }
            ins = conn.inputStream
            val total = if (expectSize > 0) expectSize else (start + conn.contentLengthLong)
            FileOutputStream(part, start > 0 && code == 206).use { os ->
                val buf = ByteArray(1 shl 15)
                var done = start
                var lastTick = System.currentTimeMillis()
                var lastBytes = done
                var speed = 0L
                while (true) {
                    if (isCanceled()) throw Aborted()
                    val n = ins.read(buf)
                    if (n <= 0) break
                    os.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - lastTick >= 400) {
                        speed = ((done - lastBytes) * 1000 / (now - lastTick))
                        lastBytes = done; lastTick = now
                        val p = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                        onUpdate(t0.copy(status = TaskStatus.RUNNING, progress = p, totalBytes = total, speed = speed, updatedAt = now))
                    }
                }
                os.flush()
            }
        } catch (e: Aborted) { throw e }
        catch (e: Retryable) { throw e }
        catch (e: Throwable) {
            val msg = e.message ?: ""
            val retryable = msg.contains("timeout", true) || msg.contains("timed out", true) ||
                    msg.contains("reset", true) || msg.contains("refused", true) || e is java.net.SocketException ||
                    e is java.io.EOFException || msg.contains("Temporary", true) || msg.contains("unable to resolve", true)
            if (retryable) throw Retryable("网络中断：" + msg) else throw Fatal("下载失败：" + msg)
        } finally {
            try { ins?.close() } catch (_: Throwable) {}
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }
}

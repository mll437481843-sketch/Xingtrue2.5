package com.ncmd

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class Bridge(private val act: Activity, private val web: WebView) : TaskManager.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val dirtyTasks = Collections.synchronizedMap(HashMap<String, String>())
    private val statsDirty = AtomicBoolean(false)

    init {
        TaskManager.setListener(this)
        Thread {
            while (true) {
                try { Thread.sleep(300) } catch (e: InterruptedException) { break }
                flush()
            }
        }.apply { isDaemon = true; start() }
    }

    override fun onTaskUpdate(t: DownloadTask) {
        try { dirtyTasks[t.taskId] = TaskManager.taskJson(t).toString() } catch (_: Throwable) {}
    }

    override fun onQueueStats(o: JSONObject) { statsDirty.set(true) }

    private fun flush() {
        val list: List<String>
        synchronized(dirtyTasks) { list = ArrayList(dirtyTasks.values); dirtyTasks.clear() }
        if (list.isNotEmpty()) emit("tasks", "[" + list.joinToString(",") + "]")
        if (statsDirty.getAndSet(false)) {
            val s = JSONObject()
            try { s.put("queue", TaskManager.stats()) } catch (_: Throwable) {}
            try { s.put("db", Db.get(act).stats()) } catch (_: Throwable) {}
            emit("stats", s.toString())
        }
    }

    fun emit(type: String, payload: String) {
        main.post {
            try {
                web.evaluateJavascript("window.__evt(" + JSONObject.quote(type) + "," + payload + ")", null)
            } catch (_: Throwable) {}
        }
    }

    private fun resp(id: String, data: JSONObject): String = JSONObject().apply {
        put("id", id); put("data", data)
    }.toString()

    private fun ok(o: JSONObject = JSONObject()): JSONObject = o.put("ok", true)

    private fun err(msg: String): JSONObject = JSONObject().put("ok", false).put("error", msg)

    private fun async(id: String, stage: String, body: () -> JSONObject) {
        Thread {
            val r = try { body() } catch (e: Throwable) {
                Log.e("bridge", stage, e)
                err(e.message ?: ("内部错误：" + e.javaClass.simpleName))
            }
            emit("cb", JSONObject().put("id", id).put("data", r).toString())
        }.apply { isDaemon = true; start() }
    }

    @JavascriptInterface
    fun call(method: String, argsJson: String): String {
        val args = try { JSONObject(argsJson.ifBlank { "{}" }) } catch (e: Throwable) { JSONObject() }
        val id = args.optString("__id", "sync")
        return try {
            dispatch(method, args, id)
        } catch (e: Throwable) {
            Log.e("bridge", method, e)
            resp(id, err(e.message ?: ("异常：" + e.javaClass.simpleName)))
        }
    }

    private fun dispatch(method: String, a: JSONObject, id: String): String {
        when (method) {
            "ping" -> return resp(id, ok().put("msg", "pong").put("version", "1.0"))
            "app.info" -> return resp(id, ok(appInfo()))
            "settings.get" -> return resp(id, ok(Settings.all()))
            "settings.set" -> {
                val v = a.optJSONObject("values") ?: JSONObject()
                Settings.setJson(v); return resp(id, ok(Settings.all()))
            }
            "settings.reset" -> { Settings.resetDefaults(); return resp(id, ok(Settings.all())) }
            "stats" -> {
                val o = ok(); o.put("queue", TaskManager.stats()); o.put("db", Db.get(act).stats())
                o.put("storage", JSONObject().apply {
                    put("free", FileOps.freeSpace())
                    put("used", FileOps.totalSizeOf(FileOps.rootDir()))
                    put("dir", FileOps.rootDir().absolutePath)
                })
                return resp(id, o)
            }
            "tasks.snapshot" -> return resp(id, ok().put("tasks", TaskManager.snapshot()).put("queue", TaskManager.stats()))
            "tasks.pause" -> { TaskManager.pauseTask(a.optString("taskId")); return resp(id, ok()) }
            "tasks.resume" -> { TaskManager.resumeTask(a.optString("taskId")); return resp(id, ok()) }
            "tasks.cancel" -> { TaskManager.cancelTask(a.optString("taskId")); return resp(id, ok()) }
            "tasks.delete" -> { TaskManager.deleteTask(a.optString("taskId")); return resp(id, ok()) }
            "tasks.clearFinished" -> { TaskManager.clearFinished(); return resp(id, ok()) }
            "tasks.retryFailed" -> return resp(id, ok().put("count", TaskManager.retryFailed()))
            "tasks.pauseAll" -> { TaskManager.pauseAll(); return resp(id, ok()) }
            "tasks.resumeAll" -> { TaskManager.resumeAll(); return resp(id, ok()) }
            "tasks.create" -> return createTasks(a, id)
            "history.list" -> return resp(id, ok().put("items", Db.get(act).history(500, a.optString("q"))))
            "playlists.recent" -> return resp(id, ok().put("items", Db.get(act).recentPlaylists()))
            "log.tail" -> return resp(id, ok().put("text", Log.tail(300)))
            "perm.status" -> {
                val m = MainActivity.current
                return resp(id, ok().put("allFiles", m?.hasAllFiles() ?: false)
                    .put("sdk", android.os.Build.VERSION.SDK_INT))
            }
            "perm.askAllFiles" -> {
                main.post { MainActivity.current?.askAllFiles() }
                return resp(id, ok())
            }
            "fs.openPath" -> return resp(id, openPath(a.optString("path")))
            "fs.deletePath" -> return resp(id, deletePath(a.optString("path")))
            "fs.list" -> return resp(id, ok().put("files", listFiles(a.optString("path"))))
            "playlist.load" -> return handleAsync(id, "playlist") { loadPlaylist(a) }
            "single.parse" -> return handleAsync(id, "single") { parseSingles(a) }
            "lyric.get" -> return handleAsync(id, "lyric") {
                val sid = a.optString("platformId")
                val l = NeteaseApi.lyric(sid)
                ok().put("lrc", l.lrc).put("tlyric", l.tlyric).put("hasLyric", l.ok)
            }
            "search" -> return handleAsync(id, "search") { val r = NeteaseApi.search(a.optString("keyword")); if (r.ok) (r.data ?: ok()) else err(r.message) }
            "net.check" -> return handleAsync(id, "net") { NeteaseApi.checkNetwork() }
            "availability.check" -> return handleAsync(id, "avail") { checkAvailability(a) }
            "library.scan" -> return handleAsync(id, "library") { scanLibrary(a) }
            "library.dups" -> return handleAsync(id, "dups") { ok().put("groups", Library.duplicates(File(a.optString("dir", FileOps.rootDir().absolutePath)))) }
            "library.hashDups" -> return handleAsync(id, "hashdups") { ok().put("groups", Library.hashDuplicates(File(a.optString("dir", FileOps.rootDir().absolutePath)))) }
            "library.cover" -> return handleAsync(id, "cover") { ok().put("base64", Library.coverOf(a.optString("path")) ?: "") }
            "db.backup" -> return handleAsync(id, "backup") { dbBackup() }
            "db.restore" -> return handleAsync(id, "restore") { dbRestore(a) }
            "cover.fetch" -> return handleAsync(id, "coverfetch") {
                val bytes = FileOps.fetchCover(a.optString("url"))
                ok().put("base64", if (bytes == null) "" else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            }
            else -> return resp(id, err("未知方法：" + method))
        }
    }

    private fun handleAsync(id: String, stage: String, body: () -> JSONObject): String {
        async(id, stage, body)
        return resp(id, JSONObject().put("ok", true).put("pending", true))
    }

    private fun appInfo(): JSONObject = JSONObject().apply {
        put("version", "1.0")
        put("build", "2026.11")
        put("sdk", android.os.Build.VERSION.SDK_INT)
        put("device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
        put("dir", FileOps.rootDir().absolutePath)
        put("allFiles", MainActivity.current?.hasAllFiles() ?: false)
        put("source", "网易云音乐公开 Web 接口（仅平台开放播放源）")
    }

    private fun loadPlaylist(a: JSONObject): JSONObject {
        val input = a.optString("input").trim()
        if (input.isBlank()) return err("请输入歌单链接或 ID")
        val parsed = NeteaseApi.resolveInput(input)
        if (parsed.type == "unknown" || parsed.id.isBlank()) return err("无法识别链接，请检查后重试")
        if (parsed.type != "playlist" && parsed.type != "album") return err("这不是歌单或专辑链接（识别为 " + parsed.type + "）")
        val r = if (parsed.type == "album") NeteaseApi.album(parsed.id) else NeteaseApi.playlist(parsed.id)
        if (!r.ok) return err(r.message)
        val data = r.data ?: return err("返回数据为空")
        val pl = data.optJSONObject("playlist") ?: JSONObject()
        val songs = data.optJSONArray("songs") ?: JSONArray()
        if (parsed.type == "playlist") {
            try {
                val info = PlaylistInfo(platformId = parsed.id, name = pl.optString("name"), cover = pl.optString("cover"),
                    creator = pl.optString("creator"), trackCount = pl.optInt("songCount"), description = pl.optString("description"))
                val rowId = Db.get(act).savePlaylist(info)
                for (i in 0 until songs.length()) {
                    val s = Song.fromJson(songs.getJSONObject(i))
                    val sid = Db.get(act).upsertSong(s)
                    Db.get(act).linkSong(rowId, sid, s.position)
                }
            } catch (e: Throwable) { Log.e("bridge", "persist playlist", e) }
        }
        // 标记已下载状态 + 完整度统计
        var downloaded = 0; var unavailable = 0; var dup = 0
        val seen = HashSet<String>()
        for (i in 0 until songs.length()) {
            val s = songs.getJSONObject(i)
            val pid = s.optString("platformId")
            if (!seen.add(pid)) { dup++; s.put("duplicate", true) } else s.put("duplicate", false)
            val exist = FileOps.existSamePlatformSong(act, pid)
            s.put("downloaded", exist != null)
            if (exist != null) downloaded++
            if (!s.optBoolean("available", true)) unavailable++
        }
        val report = JSONObject().apply {
            put("total", songs.length()); put("parsed", songs.length())
            put("downloaded", downloaded); put("duplicate", dup); put("unavailable", unavailable)
            put("report", "歌单共 " + songs.length() + " 首，已下载 " + downloaded + " 首，重复 " + dup + " 首")
        }
        return ok().put("playlist", pl).put("songs", songs).put("report", report)
    }

    private fun parseSingles(a: JSONObject): JSONObject {
        val text = a.optString("text").trim()
        if (text.isBlank()) return err("请输入歌曲链接或 ID")
        val lines = text.split(Regex("[\\n\\r,，；;\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (lines.size > 100) return err("一次最多解析 100 个链接")
        val songs = JSONArray()
        val invalid = JSONArray()
        val ids = ArrayList<String>()
        for (line in lines) {
            val p = NeteaseApi.resolveInput(line)
            if (p.type == "song" && p.id.isNotBlank()) ids.add(p.id) else if (p.type == "playlist" || p.type == "album") {
                invalid.put(JSONObject().put("input", line).put("reason", "这是歌单/专辑链接，请到歌单页导入"))
            } else invalid.put(JSONObject().put("input", line).put("reason", "无法识别的链接或 ID"))
        }
        if (ids.isNotEmpty()) {
            val details = NeteaseApi.songDetails(ids)
            val found = details.map { it.platformId }.toHashSet()
            for (s in details) {
                val src = NeteaseApi.playUrl(s.platformId, Settings.quality)
                val usable = src.ok && !src.trial
                val o = s.toJson()
                o.put("available", usable)
                o.put("unavailableReason", if (usable) "" else if (!src.ok) src.reason else "平台仅提供试听片段")
                o.put("bitrate", src.bitrate); o.put("format", src.format); o.put("level", src.level)
                o.put("fileSize", src.size)
                val exist = FileOps.existSamePlatformSong(act, s.platformId)
                o.put("downloaded", exist != null)
                songs.put(o)
                try { Db.get(act).upsertSong(s) } catch (_: Throwable) {}
            }
            for (id in ids) if (!found.contains(id)) invalid.put(JSONObject().put("input", id).put("reason", "歌曲不存在或已下架"))
        }
        return ok().put("songs", songs).put("invalid", invalid)
    }

    private fun checkAvailability(a: JSONObject): JSONObject {
        val arr = a.optJSONArray("songs") ?: JSONArray()
        val limit = a.optInt("limit", 60)
        val out = JSONArray()
        for (i in 0 until minOf(arr.length(), limit)) {
            val o = arr.getJSONObject(i)
            val pid = o.optString("platformId")
            val src = NeteaseApi.playUrl(pid, Settings.quality)
            val usable = src.ok && !src.trial
            o.put("available", usable)
            o.put("unavailableReason", if (usable) "" else if (!src.ok) src.reason else "平台仅提供试听片段")
            o.put("bitrate", src.bitrate); o.put("format", src.format); o.put("level", src.level)
            out.put(o)
        }
        return ok().put("songs", out).put("checked", out.length()).put("total", arr.length())
    }

    private fun createTasks(a: JSONObject, id: String): String {
        val songs = a.optJSONArray("songs") ?: JSONArray()
        if (songs.length() == 0) return resp(id, err("没有可下载的歌曲"))
        val playlistId = a.optString("playlistId")
        val playlistName = a.optString("playlistName")
        val taskType = a.optString("taskType", "single")
        val quality = a.optString("quality", Settings.quality)
        val list = ArrayList<DownloadTask>()
        for (i in 0 until songs.length()) {
            val o = songs.getJSONObject(i)
            val pid = o.optString("platformId")
            if (pid.isBlank()) continue
            val t = DownloadTask(
                taskId = UUID.randomUUID().toString(),
                platformId = pid,
                title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
                coverUrl = o.optString("coverUrl"), duration = o.optInt("duration"),
                taskType = taskType, playlistId = playlistId, playlistName = playlistName,
                position = o.optInt("position", i), quality = quality,
                status = TaskStatus.QUEUED, createdAt = nowMs() + i
            )
            list.add(t)
        }
        val n = TaskManager.submit(list)
        return resp(id, ok().put("count", n).put("skipped", songs.length() - n))
    }

    private fun openPath(path: String): JSONObject {
        if (path.isBlank()) return err("路径为空")
        val f = File(path)
        if (!f.exists()) return err("文件或目录不存在")
        if (f.isDirectory) {
            return ok().put("opened", false).put("path", f.absolutePath)
                .put("hint", "目录无法直接用系统应用打开，路径已显示")
        }
        return try {
            val uri = android.net.Uri.parse("content://com.ncmd.files" + f.absolutePath)
            val mime = when (f.extension.lowercase()) {
                "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "m4a" -> "audio/mp4"
                "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "lrc", "txt" -> "text/plain"
                else -> "*/*"
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(intent)
            ok().put("opened", true)
        } catch (e: Throwable) {
            Log.e("bridge", "openPath", e)
            ok().put("opened", false).put("path", f.absolutePath).put("hint", "系统未找到可打开该文件的应用")
        }
    }

    private fun deletePath(path: String): JSONObject {
        val f = File(path)
        if (!f.exists()) return err("文件不存在")
        return if (f.isDirectory) {
            val okDel = f.deleteRecursively(); ok().put("deleted", okDel)
        } else ok().put("deleted", f.delete())
    }

    private fun listFiles(path: String): JSONArray {
        val dir = File(if (path.isBlank()) FileOps.rootDir().absolutePath else path)
        val arr = JSONArray()
        val files = dir.listFiles() ?: return arr
        for (f in files.sortedByDescending { it.lastModified() }) {
            if (f.name.endsWith(".part")) continue
            arr.put(JSONObject().apply {
                put("name", f.name); put("path", f.absolutePath); put("dir", f.isDirectory)
                put("size", if (f.isFile) f.length() else 0)
            })
        }
        return arr
    }

    private fun scanLibrary(a: JSONObject): JSONObject {
        val dirPath = a.optString("dir").ifBlank { FileOps.rootDir().absolutePath }
        val dir = File(dirPath)
        if (!dir.exists()) return err("目录不存在：" + dirPath)
        val tracks = Library.scan(dir) { cur, total ->
            emit("progress", JSONObject().put("stage", "library").put("cur", cur).put("total", total).toString())
        }
        var noCover = 0; var noMeta = 0; var totalSize = 0L; var totalDur = 0L
        for (i in 0 until tracks.length()) {
            val t = tracks.getJSONObject(i)
            if (!t.optBoolean("hasCover")) noCover++
            if (!t.optBoolean("hasMeta")) noMeta++
            totalSize += t.optLong("size"); totalDur += t.optLong("duration")
        }
        return ok().put("tracks", tracks).put("dir", dirPath).put("stats", JSONObject().apply {
            put("count", tracks.length()); put("noCover", noCover); put("noMeta", noMeta)
            put("totalSize", totalSize); put("totalDuration", totalDur)
        })
    }

    private fun dbBackup(): JSONObject {
        val src = File(act.getDatabasePath("ncmd.db").absolutePath)
        if (!src.exists()) return err("数据库文件不存在")
        val dir = FileOps.rootDir()
        val dst = File(dir, "ncmd-backup-" + android.text.format.DateFormat.format("yyyyMMdd-HHmmss", nowMs()) + ".db")
        src.copyTo(dst, true)
        return ok().put("path", dst.absolutePath).put("size", dst.length())
    }

    private fun dbRestore(a: JSONObject): JSONObject {
        val p = a.optString("path")
        val f = File(p)
        if (!f.exists()) return err("备份文件不存在")
        val dst = File(act.getDatabasePath("ncmd.db").absolutePath)
        try {
            Db.get(act).close()
        } catch (_: Throwable) {}
        try {
            f.copyTo(dst, true)
            return ok().put("path", dst.absolutePath)
        } catch (e: Throwable) { return err("恢复失败：" + (e.message ?: "")) }
    }
}

package com.ncmd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object TaskManager {

    interface Listener {
        fun onTaskUpdate(t: DownloadTask)
        fun onQueueStats(o: JSONObject)
    }

    private lateinit var ctx: Context
    private var downloader: Downloader? = null
    private var listener: Listener? = null
    private val mem = Collections.synchronizedMap(LinkedHashMap<String, DownloadTask>())
    private val cancels = Collections.synchronizedMap(HashMap<String, AtomicBoolean>())
    private val executor = Executors.newFixedThreadPool(6)
    private val persistMarks = Collections.synchronizedMap(HashMap<String, DownloadTask>())
    @Volatile private var pausedAll = false
    @Volatile private var activeCount = 0
    @Volatile private var dispatcher: Thread? = null
    @Volatile private var started = false

    fun init(c: Context) {
        ctx = c.applicationContext
        downloader = Downloader(ctx)
    }

    fun setListener(l: Listener?) { listener = l }

    fun recover() {
        try {
            val arr = Db.get(ctx).loadTasks()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                var st = o.optString("status")
                if (st == TaskStatus.RUNNING || st == TaskStatus.PARSING) st = TaskStatus.PAUSED
                val t = DownloadTask(
                    taskId = o.optString("taskId"), platformId = o.optString("platformId"),
                    title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
                    coverUrl = o.optString("coverUrl"), duration = o.optInt("duration"),
                    taskType = o.optString("taskType", "single"), playlistId = o.optString("playlistId"),
                    playlistName = o.optString("playlistName"), position = o.optInt("position"),
                    quality = o.optString("quality", "standard"), status = st, progress = o.optInt("progress"),
                    totalBytes = o.optLong("totalBytes"), retryCount = o.optInt("retryCount"),
                    errorMessage = o.optString("errorMessage"), filePath = o.optString("filePath"),
                    createdAt = o.optLong("createdAt"), completedAt = o.optLong("completedAt")
                )
                mem[t.taskId] = t
                Db.get(ctx).upsertTask(t)
            }
            Log.i("task", "recovered " + mem.size)
        } catch (e: Throwable) { Log.e("task", "recover", e) }
    }

    private fun ensureDispatcher() {
        if (started) return
        started = true
        dispatcher = Thread {
            while (true) {
                try {
                    Thread.sleep(300)
                    if (pausedAll) { pushStats(); continue }
                    val limit = Settings.maxConcurrent
                    var free = limit - activeCount
                    if (free <= 0) { pushStats(); continue }
                    val queued = synchronized(mem) { mem.values.filter { it.status == TaskStatus.QUEUED }.sortedBy { it.createdAt } }
                    for (t in queued) {
                        if (free <= 0) break
                        if (cancels.containsKey(t.taskId)) continue
                        free--
                        launch(t)
                    }
                    pushStats()
                } catch (e: InterruptedException) { break }
                catch (e: Throwable) { try { Thread.sleep(600) } catch (_: InterruptedException) { break } }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun launch(t: DownloadTask) {
        val flag = AtomicBoolean(false)
        cancels[t.taskId] = flag
        activeCount++
        update(t.copy(status = if (t.progress > 0) TaskStatus.RUNNING else TaskStatus.PARSING))
        executor.execute {
            var cur = mem[t.taskId] ?: t
            val maxRetry = Settings.autoRetry
            var attempt = 0
            try {
                while (true) {
                    try {
                        val res = downloader!!.run(cur, { flag.get() }) { upd ->
                            cur = upd
                            update(upd)
                        }
                        cur = res
                        if (res.status == TaskStatus.PAUSED) { /* 不应发生 */ }
                        break
                    } catch (e: Downloader.Aborted) {
                        val st = mem[t.taskId]?.status
                        cur = cur.copy(status = if (st == TaskStatus.PAUSED) TaskStatus.PAUSED else TaskStatus.CANCELED,
                            speed = 0, updatedAt = nowMs(),
                            errorMessage = if (st == TaskStatus.PAUSED) "已暂停，可继续" else "任务已取消")
                        update(cur)
                        break
                    } catch (e: Downloader.Retryable) {
                        if (attempt < maxRetry && flag.get() == false) {
                            attempt++
                            cur = cur.copy(retryCount = attempt, errorMessage = e.message ?: "重试中",
                                status = TaskStatus.QUEUED, speed = 0, updatedAt = nowMs())
                            update(cur)
                            val wait = (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15000L)
                            var slept = 0L
                            while (slept < wait) {
                                if (flag.get()) break
                                Thread.sleep(200); slept += 200
                            }
                            if (flag.get()) {
                                cur = cur.copy(status = TaskStatus.CANCELED, errorMessage = "任务已取消")
                                update(cur); break
                            }
                            continue
                        }
                        cur = cur.copy(status = TaskStatus.FAILED, speed = 0, updatedAt = nowMs(),
                            errorMessage = (e.message ?: "下载失败") + "（已重试 " + attempt + " 次）")
                        update(cur); break
                    } catch (e: Downloader.Fatal) {
                        cur = cur.copy(status = TaskStatus.FAILED, speed = 0, updatedAt = nowMs(),
                            errorMessage = e.message ?: "下载失败")
                        update(cur); break
                    } catch (e: Throwable) {
                        cur = cur.copy(status = TaskStatus.FAILED, speed = 0, updatedAt = nowMs(),
                            errorMessage = "异常：" + (e.message ?: e.javaClass.simpleName))
                        Log.e("task", "launch error", e)
                        update(cur); break
                    }
                }
            } finally {
                cancels.remove(t.taskId)
                activeCount--
                pushStats()
            }
        }
    }

    private fun update(t: DownloadTask) {
        mem[t.taskId] = t
        val prev = persistMarks[t.taskId]
        val need = prev == null || prev.status != t.status || Math.abs(t.progress - prev.progress) >= 5 ||
                prev.retryCount != t.retryCount || prev.filePath != t.filePath
        if (need) {
            persistMarks[t.taskId] = t
            try { Db.get(ctx).upsertTask(t) } catch (e: Throwable) { Log.e("task", "persist", e) }
        }
        try { listener?.onTaskUpdate(t) } catch (_: Throwable) {}
    }

    fun pushStats() { try { listener?.onQueueStats(stats()) } catch (_: Throwable) {} }

    fun submit(list: List<DownloadTask>): Int {
        ensureDispatcher()
        var n = 0
        for (t in list) {
            if (mem.containsKey(t.taskId)) continue
            mem[t.taskId] = t
            Db.get(ctx).upsertTask(t)
            n++
        }
        pushStats()
        return n
    }

    fun pauseTask(id: String) {
        cancels[id]?.set(true)
        val t = mem[id] ?: return
        if (t.status == TaskStatus.QUEUED) update(t.copy(status = TaskStatus.PAUSED, errorMessage = "已暂停"))
        else update(t.copy(status = TaskStatus.PAUSED, errorMessage = "已暂停，可继续"))
    }

    fun resumeTask(id: String) {
        val t = mem[id] ?: return
        if (t.status == TaskStatus.PAUSED || t.status == TaskStatus.FAILED || t.status == TaskStatus.CANCELED) {
            cancels.remove(id)
            update(t.copy(status = TaskStatus.QUEUED, errorMessage = "", retryCount = 0))
        }
    }

    fun cancelTask(id: String) {
        cancels[id]?.set(true)
        val t = mem[id] ?: return
        update(t.copy(status = TaskStatus.CANCELED, speed = 0, errorMessage = "任务已取消", updatedAt = nowMs()))
    }

    fun deleteTask(id: String) {
        cancels[id]?.set(true)
        mem.remove(id)
        Db.get(ctx).deleteTask(id)
        pushStats()
    }

    fun clearFinished() {
        val ids = synchronized(mem) { mem.values.filter {
            it.status == TaskStatus.DONE || it.status == TaskStatus.FAILED ||
            it.status == TaskStatus.CANCELED || it.status == TaskStatus.UNAVAILABLE
        }.map { it.taskId } }
        for (id in ids) { mem.remove(id); Db.get(ctx).deleteTask(id) }
        pushStats()
    }

    fun pauseAll() { pausedAll = true; for (t in snapshotList()) if (t.status == TaskStatus.QUEUED) update(t.copy(status = TaskStatus.PAUSED)) }
    fun resumeAll() {
        pausedAll = false
        for (t in snapshotList()) if (t.status == TaskStatus.PAUSED) update(t.copy(status = TaskStatus.QUEUED, errorMessage = ""))
        ensureDispatcher()
    }
    fun retryFailed(): Int {
        var n = 0
        for (t in snapshotList()) if (t.status == TaskStatus.FAILED || t.status == TaskStatus.UNAVAILABLE || t.status == TaskStatus.CANCELED) {
            update(t.copy(status = TaskStatus.QUEUED, retryCount = 0, errorMessage = "", progress = 0)); n++
        }
        ensureDispatcher()
        return n
    }

    private fun snapshotList(): List<DownloadTask> = synchronized(mem) { mem.values.toList() }

    fun snapshot(): JSONArray {
        val arr = JSONArray()
        for (t in snapshotList().sortedByDescending { it.createdAt }) arr.put(taskJson(t))
        return arr
    }

    fun taskJson(t: DownloadTask): JSONObject = JSONObject().apply {
        put("taskId", t.taskId); put("platformId", t.platformId); put("title", t.title)
        put("artist", t.artist); put("album", t.album); put("coverUrl", t.coverUrl)
        put("duration", t.duration); put("taskType", t.taskType); put("playlistId", t.playlistId)
        put("playlistName", t.playlistName); put("position", t.position); put("quality", t.quality)
        put("status", t.status); put("progress", t.progress); put("totalBytes", t.totalBytes)
        put("speed", t.speed); put("retryCount", t.retryCount); put("errorMessage", t.errorMessage)
        put("filePath", t.filePath); put("createdAt", t.createdAt); put("completedAt", t.completedAt)
    }

    fun stats(): JSONObject {
        val list = snapshotList()
        var queued = 0; var running = 0; var paused = 0; var done = 0; var failed = 0; var canceled = 0; var unavailable = 0; var speed = 0L
        for (t in list) {
            when (t.status) {
                TaskStatus.QUEUED -> queued++
                TaskStatus.RUNNING, TaskStatus.PARSING -> { running++; speed += t.speed }
                TaskStatus.PAUSED -> paused++
                TaskStatus.DONE -> done++
                TaskStatus.FAILED -> failed++
                TaskStatus.CANCELED -> canceled++
                TaskStatus.UNAVAILABLE -> unavailable++
            }
        }
        val total = list.size
        val finished = done + failed + canceled + unavailable
        return JSONObject().apply {
            put("total", total); put("queued", queued); put("running", running); put("paused", paused)
            put("done", done); put("failed", failed); put("canceled", canceled); put("unavailable", unavailable)
            put("percent", if (total == 0) 0 else (finished * 100 / total))
            put("speed", speed); put("speedText", humanSize(speed) + "/s")
            put("activeCount", activeCount); put("pausedAll", pausedAll)
            put("maxConcurrent", Settings.maxConcurrent)
        }
    }
}

package com.ncmd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "ncmd.db", null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 1
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE songs(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            platform_song_id TEXT UNIQUE NOT NULL,
            title TEXT, artist TEXT, album TEXT, duration INTEGER DEFAULT 0,
            cover_path TEXT, cover_url TEXT, lyric_path TEXT,
            download_status TEXT DEFAULT 'none', file_path TEXT, file_size INTEGER DEFAULT 0,
            md5 TEXT, created_at INTEGER, updated_at INTEGER)""")
        db.execSQL("""CREATE TABLE playlists(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            platform_playlist_id TEXT UNIQUE NOT NULL,
            name TEXT, cover TEXT, creator TEXT, song_count INTEGER DEFAULT 0,
            imported_at INTEGER)""")
        db.execSQL("""CREATE TABLE playlist_songs(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            playlist_id INTEGER, song_id INTEGER, position INTEGER)""")
        db.execSQL("""CREATE TABLE download_tasks(
            task_id TEXT PRIMARY KEY, song_id TEXT, title TEXT, artist TEXT, album TEXT,
            cover_url TEXT, duration INTEGER DEFAULT 0, task_type TEXT, playlist_id TEXT,
            playlist_name TEXT, position INTEGER DEFAULT 0, quality TEXT,
            status TEXT, progress INTEGER DEFAULT 0, total_bytes INTEGER DEFAULT 0,
            retry_count INTEGER DEFAULT 0, error_message TEXT, file_path TEXT,
            created_at INTEGER, updated_at INTEGER, completed_at INTEGER)""")
        db.execSQL("""CREATE TABLE download_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT, song_id TEXT, title TEXT, artist TEXT,
            album TEXT, file_path TEXT, file_size INTEGER, format TEXT,
            downloaded_at INTEGER, source_type TEXT)""")
        db.execSQL("""CREATE TABLE app_settings(key TEXT PRIMARY KEY, value TEXT)""")
        db.execSQL("CREATE INDEX idx_task_status ON download_tasks(status)")
        db.execSQL("CREATE INDEX idx_history_time ON download_history(downloaded_at)")
        db.execSQL("CREATE INDEX idx_ps_playlist ON playlist_songs(playlist_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        if (oldV < 1) onCreate(db)
    }

    // ---------- songs ----------
    fun upsertSong(s: Song): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("platform_song_id", s.platformId); put("title", s.title); put("artist", s.artist)
            put("album", s.album); put("duration", s.duration); put("cover_url", s.coverUrl)
            put("updated_at", nowMs())
        }
        val exist = db.rawQuery("SELECT id FROM songs WHERE platform_song_id=?", arrayOf(s.platformId)).use { it.moveToFirst() }
        return if (exist) {
            db.update("songs", cv, "platform_song_id=?", arrayOf(s.platformId))
            db.rawQuery("SELECT id FROM songs WHERE platform_song_id=?", arrayOf(s.platformId)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        } else { cv.put("created_at", nowMs()); db.insert("songs", null, cv) }
    }

    fun songByPlatformId(pid: String): JSONObject? = readableDatabase.rawQuery(
        "SELECT platform_song_id,title,artist,album,duration,file_path,file_size,download_status,md5 FROM songs WHERE platform_song_id=?",
        arrayOf(pid)).use { if (it.moveToFirst()) JSONObject().apply {
            put("platformId", it.getString(0)); put("title", it.getString(1) ?: ""); put("artist", it.getString(2) ?: "")
            put("album", it.getString(3) ?: ""); put("duration", it.getInt(4)); put("filePath", it.getString(5) ?: "")
            put("fileSize", it.getLong(6)); put("downloadStatus", it.getString(7) ?: "none"); put("md5", it.getString(8) ?: "")
        } else null }

    fun markDownloaded(pid: String, filePath: String, size: Long, md5: String) {
        writableDatabase.execSQL("UPDATE songs SET download_status='done', file_path=?, file_size=?, md5=?, updated_at=? WHERE platform_song_id=?",
            arrayOf<Any>(filePath, size, md5, nowMs(), pid))
    }

    // ---------- playlists ----------
    fun savePlaylist(info: PlaylistInfo): Long {
        val db = writableDatabase
        db.execSQL("INSERT OR REPLACE INTO playlists(platform_playlist_id,name,cover,creator,song_count,imported_at) VALUES(?,?,?,?,?,?)",
            arrayOf<Any>(info.platformId, info.name, info.cover, info.creator, info.trackCount, nowMs()))
        val pid = db.rawQuery("SELECT id FROM playlists WHERE platform_playlist_id=?", arrayOf(info.platformId)).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        db.delete("playlist_songs", "playlist_id=?", arrayOf(pid.toString()))
        return pid
    }

    fun linkSong(playlistRowId: Long, songRowId: Long, position: Int) {
        writableDatabase.execSQL("INSERT INTO playlist_songs(playlist_id,song_id,position) VALUES(?,?,?)", arrayOf<Any>(playlistRowId, songRowId, position))
    }

    fun recentPlaylists(): JSONArray {
        val arr = JSONArray()
        readableDatabase.rawQuery("SELECT platform_playlist_id,name,cover,song_count,imported_at FROM playlists ORDER BY imported_at DESC LIMIT 20", null).use { c ->
            while (c.moveToNext()) arr.put(JSONObject().apply {
                put("platformId", c.getString(0)); put("name", c.getString(1) ?: ""); put("cover", c.getString(2) ?: "")
                put("songCount", c.getInt(3)); put("importedAt", c.getLong(4))
            })
        }
        return arr
    }

    // ---------- tasks ----------
    fun upsertTask(t: DownloadTask) {
        val cv = ContentValues().apply {
            put("task_id", t.taskId); put("song_id", t.platformId); put("title", t.title); put("artist", t.artist)
            put("album", t.album); put("cover_url", t.coverUrl); put("duration", t.duration)
            put("task_type", t.taskType); put("playlist_id", t.playlistId); put("playlist_name", t.playlistName)
            put("position", t.position); put("quality", t.quality); put("status", t.status)
            put("progress", t.progress); put("total_bytes", t.totalBytes); put("retry_count", t.retryCount)
            put("error_message", t.errorMessage); put("file_path", t.filePath)
            put("created_at", t.createdAt); put("updated_at", nowMs()); put("completed_at", t.completedAt)
        }
        writableDatabase.insertWithOnConflict("download_tasks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateTask(taskId: String, values: ContentValues) {
        values.put("updated_at", nowMs())
        writableDatabase.update("download_tasks", values, "task_id=?", arrayOf(taskId))
    }

    fun deleteTask(taskId: String) { writableDatabase.delete("download_tasks", "task_id=?", arrayOf(taskId)) }
    fun clearTasksByStatus(status: String) { writableDatabase.delete("download_tasks", "status=?", arrayOf(status)) }

    fun loadTasks(): JSONArray {
        val arr = JSONArray()
        readableDatabase.rawQuery("""SELECT task_id,song_id,title,artist,album,cover_url,duration,task_type,playlist_id,
            playlist_name,position,quality,status,progress,total_bytes,retry_count,error_message,file_path,created_at,completed_at
            FROM download_tasks ORDER BY created_at ASC""", null).use { c ->
            while (c.moveToNext()) arr.put(JSONObject().apply {
                put("taskId", c.getString(0)); put("platformId", c.getString(1) ?: ""); put("title", c.getString(2) ?: "")
                put("artist", c.getString(3) ?: ""); put("album", c.getString(4) ?: ""); put("coverUrl", c.getString(5) ?: "")
                put("duration", c.getInt(6)); put("taskType", c.getString(7) ?: "single")
                put("playlistId", c.getString(8) ?: ""); put("playlistName", c.getString(9) ?: "")
                put("position", c.getInt(10)); put("quality", c.getString(11) ?: "standard")
                put("status", c.getString(12) ?: TaskStatus.QUEUED); put("progress", c.getInt(13))
                put("totalBytes", c.getLong(14)); put("retryCount", c.getInt(15))
                put("errorMessage", c.getString(16) ?: ""); put("filePath", c.getString(17) ?: "")
                put("createdAt", c.getLong(18)); put("completedAt", c.getLong(19))
            })
        }
        return arr
    }

    // ---------- history ----------
    fun addHistory(songId: String, title: String, artist: String, album: String, path: String, size: Long, format: String, source: String) {
        writableDatabase.execSQL("INSERT INTO download_history(song_id,title,artist,album,file_path,file_size,format,downloaded_at,source_type) VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(songId, title, artist, album, path, size, format, nowMs(), source))
    }

    fun history(limit: Int = 500, query: String = ""): JSONArray {
        val arr = JSONArray()
        val where = if (query.isBlank()) "" else "WHERE title LIKE '%" + query.replace("'", "") + "%' OR artist LIKE '%" + query.replace("'", "") + "%'"
        readableDatabase.rawQuery("SELECT song_id,title,artist,album,file_path,file_size,format,downloaded_at,source_type FROM download_history $where ORDER BY downloaded_at DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) arr.put(JSONObject().apply {
                put("platformId", c.getString(0) ?: ""); put("title", c.getString(1) ?: ""); put("artist", c.getString(2) ?: "")
                put("album", c.getString(3) ?: ""); put("filePath", c.getString(4) ?: ""); put("fileSize", c.getLong(5))
                put("format", c.getString(6) ?: ""); put("downloadedAt", c.getLong(7)); put("sourceType", c.getString(8) ?: "")
            })
        }
        return arr
    }

    fun stats(): JSONObject {
        val todayStart = nowMs() - (nowMs() % 86400000L)
        val o = JSONObject()
        readableDatabase.rawQuery("SELECT COUNT(*) FROM download_history WHERE downloaded_at>=?", arrayOf(todayStart.toString())).use { if (it.moveToFirst()) o.put("todayCount", it.getInt(0)) }
        readableDatabase.rawQuery("SELECT COUNT(*), IFNULL(SUM(file_size),0) FROM download_history", null).use { if (it.moveToFirst()) { o.put("totalCount", it.getInt(0)); o.put("totalBytes", it.getLong(1)) } }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM download_tasks WHERE status IN ('queued','running','parsing','paused')", null).use { if (it.moveToFirst()) o.put("activeTasks", it.getInt(0)) }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM playlists", null).use { if (it.moveToFirst()) o.put("playlistCount", it.getInt(0)) }
        return o
    }
}

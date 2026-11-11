package com.ncmd

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Settings {
    private lateinit var sp: SharedPreferences
    private val lock = Any()
    fun init(ctx: Context) { sp = ctx.getSharedPreferences("ncmd_settings", Context.MODE_PRIVATE) }

    var downloadDir: String
        get() = sp.getString("download_dir", android.os.Environment.getExternalStorageDirectory().absolutePath + "/Download/云音乐管家") ?: ""
        set(v) = sp.edit().putString("download_dir", v).apply()
    var nameTemplate: String
        get() = sp.getString("name_template", "{歌手} - {歌曲名称}") ?: ""
        set(v) = sp.edit().putString("name_template", v).apply()
    var classifyMode: String
        get() = sp.getString("classify", "none") ?: "none"
        set(v) = sp.edit().putString("classify", v).apply()
    var maxConcurrent: Int
        get() = sp.getInt("max_concurrent", 2)
        set(v) = sp.edit().putInt("max_concurrent", v.coerceIn(1, 6)).apply()
    var autoRetry: Int
        get() = sp.getInt("auto_retry", 3)
        set(v) = sp.edit().putInt("auto_retry", v.coerceIn(0, 8)).apply()
    var timeoutSec: Int
        get() = sp.getInt("timeout_sec", 30)
        set(v) = sp.edit().putInt("timeout_sec", v.coerceIn(10, 180)).apply()
    var quality: String
        get() = sp.getString("quality", "exhigh") ?: "exhigh"
        set(v) = sp.edit().putString("quality", v).apply()
    var saveLyric: Boolean
        get() = sp.getBoolean("save_lyric", true)
        set(v) = sp.edit().putBoolean("save_lyric", v).apply()
    var saveCover: Boolean
        get() = sp.getBoolean("save_cover", true)
        set(v) = sp.edit().putBoolean("save_cover", v).apply()
    var writeMeta: Boolean
        get() = sp.getBoolean("write_meta", true)
        set(v) = sp.edit().putBoolean("write_meta", v).apply()
    var lyricFormat: String
        get() = sp.getString("lyric_format", "lrc") ?: "lrc"
        set(v) = sp.edit().putString("lyric_format", v).apply()
    var coverFormat: String
        get() = sp.getString("cover_format", "jpg") ?: "jpg"
        set(v) = sp.edit().putString("cover_format", v).apply()
    var embedCover: Boolean
        get() = sp.getBoolean("embed_cover", true)
        set(v) = sp.edit().putBoolean("embed_cover", v).apply()
    var theme: String
        get() = sp.getString("theme", "dark") ?: "dark"
        set(v) = sp.edit().putString("theme", v).apply()
    var uiScale: Float
        get() = sp.getFloat("ui_scale", 1.0f)
        set(v) = sp.edit().putFloat("ui_scale", v.coerceIn(0.8f, 1.3f)).apply()
    var cookie: String
        get() = sp.getString("cookie", "") ?: ""
        set(v) = sp.edit().putString("cookie", v.trim()).apply()
    var skipDuplicate: Boolean
        get() = sp.getBoolean("skip_duplicate", true)
        set(v) = sp.edit().putBoolean("skip_duplicate", v).apply()
    var duplicateAction: String
        get() = sp.getString("duplicate_action", "skip") ?: "skip"
        set(v) = sp.edit().putString("duplicate_action", v).apply()
    var openDirAfter: Boolean
        get() = sp.getBoolean("open_dir_after", false)
        set(v) = sp.edit().putBoolean("open_dir_after", v).apply()
    var autoCreateFolder: Boolean
        get() = sp.getBoolean("auto_create_folder", true)
        set(v) = sp.edit().putBoolean("auto_create_folder", v).apply()

    fun all(): JSONObject = JSONObject().apply {
        put("downloadDir", downloadDir); put("nameTemplate", nameTemplate); put("classify", classifyMode)
        put("maxConcurrent", maxConcurrent); put("autoRetry", autoRetry); put("timeoutSec", timeoutSec)
        put("quality", quality); put("saveLyric", saveLyric); put("saveCover", saveCover)
        put("writeMeta", writeMeta); put("lyricFormat", lyricFormat); put("coverFormat", coverFormat)
        put("embedCover", embedCover); put("theme", theme); put("uiScale", uiScale.toDouble())
        put("skipDuplicate", skipDuplicate); put("duplicateAction", duplicateAction)
        put("openDirAfter", openDirAfter); put("autoCreateFolder", autoCreateFolder)
        put("cookie", if (cookie.isBlank()) "" else "已设置")
    }
    fun setJson(o: JSONObject) {
        synchronized(lock) {
            if (o.has("downloadDir")) downloadDir = o.optString("downloadDir")
            if (o.has("nameTemplate")) nameTemplate = o.optString("nameTemplate")
            if (o.has("classify")) classifyMode = o.optString("classify")
            if (o.has("maxConcurrent")) maxConcurrent = o.optInt("maxConcurrent", 2)
            if (o.has("autoRetry")) autoRetry = o.optInt("autoRetry", 3)
            if (o.has("timeoutSec")) timeoutSec = o.optInt("timeoutSec", 30)
            if (o.has("quality")) quality = o.optString("quality")
            if (o.has("saveLyric")) saveLyric = o.optBoolean("saveLyric", true)
            if (o.has("saveCover")) saveCover = o.optBoolean("saveCover", true)
            if (o.has("writeMeta")) writeMeta = o.optBoolean("writeMeta", true)
            if (o.has("lyricFormat")) lyricFormat = o.optString("lyricFormat")
            if (o.has("coverFormat")) coverFormat = o.optString("coverFormat")
            if (o.has("embedCover")) embedCover = o.optBoolean("embedCover", true)
            if (o.has("theme")) theme = o.optString("theme")
            if (o.has("uiScale")) uiScale = o.optDouble("uiScale", 1.0).toFloat()
            if (o.has("skipDuplicate")) skipDuplicate = o.optBoolean("skipDuplicate", true)
            if (o.has("duplicateAction")) duplicateAction = o.optString("duplicateAction")
            if (o.has("openDirAfter")) openDirAfter = o.optBoolean("openDirAfter", false)
            if (o.has("autoCreateFolder")) autoCreateFolder = o.optBoolean("autoCreateFolder", true)
            if (o.has("cookie")) cookie = o.optString("cookie")
        }
    }
    fun resetDefaults() {
        sp.edit().clear().apply()
    }
}

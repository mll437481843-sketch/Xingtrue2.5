package com.ncmd

import org.json.JSONObject

data class Song(
    val platformId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Int = 0,
    val coverUrl: String = "",
    val fee: Int = 0,
    val available: Boolean = true,
    val unavailableReason: String = "",
    val maxBitrate: Int = 0,
    val publishTime: Long = 0,
    val position: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("platformId", platformId); put("title", title); put("artist", artist)
        put("album", album); put("duration", duration); put("coverUrl", coverUrl)
        put("fee", fee); put("available", available); put("unavailableReason", unavailableReason)
        put("maxBitrate", maxBitrate); put("publishTime", publishTime); put("position", position)
    }
    companion object {
        fun fromJson(o: JSONObject) = Song(
            platformId = o.optString("platformId"), title = o.optString("title"),
            artist = o.optString("artist"), album = o.optString("album"),
            duration = o.optInt("duration"), coverUrl = o.optString("coverUrl"),
            fee = o.optInt("fee"), available = o.optBoolean("available", true),
            unavailableReason = o.optString("unavailableReason"), maxBitrate = o.optInt("maxBitrate"),
            publishTime = o.optLong("publishTime"), position = o.optInt("position")
        )
    }
}

data class PlaylistInfo(
    val platformId: String = "", val name: String = "", val cover: String = "",
    val creator: String = "", val trackCount: Int = 0, val description: String = "",
    val updateTime: Long = 0
)

object TaskStatus {
    const val QUEUED = "queued"
    const val PARSING = "parsing"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELED = "canceled"
    const val UNAVAILABLE = "unavailable"
}

data class DownloadTask(
    val taskId: String,
    val platformId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val duration: Int = 0,
    val taskType: String = "single",
    val playlistId: String = "",
    val playlistName: String = "",
    val position: Int = 0,
    val quality: String = "standard",
    val status: String = TaskStatus.QUEUED,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val speed: Long = 0,
    val retryCount: Int = 0,
    val errorMessage: String = "",
    val filePath: String = "",
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
    val completedAt: Long = 0
)

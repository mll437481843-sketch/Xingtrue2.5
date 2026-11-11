package com.ncmd

import android.media.MediaMetadataRetriever
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Library {

    private val EXTS = setOf("mp3", "flac", "m4a", "ogg", "wav", "aac", "wma", "ape")

    fun isAudio(f: File): Boolean = f.isFile && EXTS.contains(f.extension.lowercase())

    fun scan(root: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): JSONArray {
        val out = JSONArray()
        if (!root.exists()) return out
        val files = ArrayList<File>()
        try {
            root.walkTopDown().onEnter { d -> !d.name.startsWith(".") && d.name != "Android" }
                .filter { isAudio(it) }.forEach { files.add(it) }
        } catch (e: Throwable) { Log.e("lib", "walk", e) }
        val total = files.size
        var i = 0
        for (f in files) {
            i++
            if (i % 20 == 0) onProgress(i, total)
            val o = readMeta(f)
            if (o != null) out.put(o)
        }
        onProgress(total, total)
        return out
    }

    fun readMeta(f: File): JSONObject? {
        var mmr: MediaMetadataRetriever? = null
        return try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val hasPic = mmr.embeddedPicture != null
            JSONObject().apply {
                put("path", f.absolutePath)
                put("fileName", f.name)
                put("title", title ?: f.nameWithoutExtension)
                put("artist", artist ?: "")
                put("album", album ?: "")
                put("duration", (dur / 1000).toInt())
                put("size", f.length())
                put("format", f.extension.uppercase())
                put("hasCover", hasPic)
                put("hasMeta", !title.isNullOrBlank() && !artist.isNullOrBlank())
                put("modified", f.lastModified())
            }
        } catch (e: Throwable) {
            JSONObject().apply {
                put("path", f.absolutePath); put("fileName", f.name); put("title", f.nameWithoutExtension)
                put("artist", ""); put("album", ""); put("duration", 0); put("size", f.length())
                put("format", f.extension.uppercase()); put("hasCover", false); put("hasMeta", false)
                put("modified", f.lastModified())
            }
        } finally { try { mmr?.release() } catch (_: Throwable) {} }
    }

    fun coverOf(path: String): String? {
        var mmr: MediaMetadataRetriever? = null
        return try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val b = mmr.embeddedPicture ?: return null
            android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) { null } finally { try { mmr?.release() } catch (_: Throwable) {} }
    }

    fun duplicates(root: File): JSONArray {
        val arr = JSONArray()
        val groups = HashMap<String, MutableList<File>>()
        try {
            root.walkTopDown().filter { isAudio(it) }.forEach { f ->
                val key = f.nameWithoutExtension.lowercase().replace(Regex("[\\s._-]"), "")
                groups.getOrPut(key) { ArrayList() }.add(f)
            }
        } catch (e: Throwable) {}
        for ((k, v) in groups) if (v.size > 1) {
            arr.put(JSONObject().apply {
                put("key", k)
                put("files", JSONArray().also { a -> v.forEach { f -> a.put(JSONObject().put("path", f.absolutePath).put("size", f.length())) } })
            })
        }
        return arr
    }

    fun hashDuplicates(root: File): JSONArray {
        val arr = JSONArray()
        val byHash = HashMap<String, MutableList<File>>()
        try {
            root.walkTopDown().filter { isAudio(it) && it.length() > 1024 }.forEach { f ->
                val h = try { md5OfFile(f) } catch (e: Throwable) { "" }
                if (h.isNotEmpty()) byHash.getOrPut(h) { ArrayList() }.add(f)
            }
        } catch (e: Throwable) {}
        for ((h, v) in byHash) if (v.size > 1) {
            arr.put(JSONObject().apply {
                put("hash", h)
                put("files", JSONArray().also { a -> v.forEach { f -> a.put(JSONObject().put("path", f.absolutePath).put("size", f.length())) } })
            })
        }
        return arr
    }
}

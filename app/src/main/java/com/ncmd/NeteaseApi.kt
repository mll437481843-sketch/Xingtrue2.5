package com.ncmd

import org.json.JSONArray
import org.json.JSONObject

object LinkParser {
    data class Parsed(val type: String, val id: String, val raw: String = "")

    // type: song / playlist / album / unknown
    fun parse(input: String): Parsed {
        var s = input.trim()
        if (s.isEmpty()) return Parsed("unknown", "")
        if (s.matches(Regex("^\\d{3,}$"))) return Parsed("song", s)
        // 分享文本里抽取 url
        val urlMatch = Regex("https?://[^\\s\u4e00-\u9fa5]+").find(s)
        if (urlMatch != null) s = urlMatch.value
        s = s.replace("&amp;", "&")
        val idMatch = Regex("[?&#](?:id|songid|playlistid)=([0-9]+)", RegexOption.IGNORE_CASE).find(s)
        val pathSong = Regex("music\\.163\\.com/(?:#/)?song/([0-9]+)").find(s)
        val pathPl = Regex("music\\.163\\.com/(?:#/)?(?:m/)?playlist/([0-9]+)").find(s)
        val pathAl = Regex("music\\.163\\.com/(?:#/)?album/([0-9]+)").find(s)
        val pathDj = Regex("music\\.163\\.com/(?:#/)?program/([0-9]+)").find(s)
        if (pathSong != null) return Parsed("song", pathSong.groupValues[1])
        if (pathPl != null) return Parsed("playlist", pathPl.groupValues[1])
        if (pathAl != null) return Parsed("album", pathAl.groupValues[1])
        if (pathDj != null) return Parsed("program", pathDj.groupValues[1])
        if (idMatch != null) {
            val id = idMatch.groupValues[1]
            val type = when {
                s.contains("album", true) -> "album"
                s.contains("playlist", true) -> "playlist"
                s.contains("song", true) -> "song"
                s.contains("program", true) -> "program"
                else -> "song"
            }
            return Parsed(type, id)
        }
        if (Regex("^[0-9]{3,}$").matches(s)) return Parsed("song", s)
        return Parsed("unknown", "")
    }
}

object NeteaseApi {
    private const val BASE = "https://music.163.com"
    @Volatile var cookieProvider: (() -> String)? = null

    private fun cookieHeader(): String {
        val custom = try { cookieProvider?.invoke() ?: "" } catch (e: Throwable) { "" }
        val base = "os=pc; appver=8.9.70; osver=18; deviceId=ncmd"
        return if (custom.isBlank()) base else base + "; " + custom
    }

    private val HEADERS: Map<String, String>
        get() = mapOf(
            "Referer" to "https://music.163.com/",
            "Origin" to "https://music.163.com",
            "Cookie" to cookieHeader()
        )

    data class Result(val ok: Boolean, val message: String = "", val data: JSONObject? = null)

    fun followShortLink(url: String): String {
        return try {
            val r = Http.get(url, HEADERS, 12000)
            r.finalUrl.ifBlank { url }
        } catch (e: Throwable) { url }
    }

    fun resolveInput(input: String): LinkParser.Parsed {
        var p = LinkParser.parse(input)
        if (p.type == "unknown" && input.contains("163cn.tv")) {
            val real = followShortLink(Regex("https?://[^\\s]+").find(input)?.value ?: input)
            p = LinkParser.parse(real)
        }
        return p
    }

    fun playlist(pid: String): Result {
        return try {
            val r = Http.postForm("$BASE/api/v6/playlist/detail", mapOf("id" to pid, "n" to "1000", "s" to "0"), HEADERS)
            val o = JSONObject(r.body)
            val code = o.optInt("code", -1)
            if (code != 200) return Result(false, "歌单不存在或无法访问（code=$code）")
            val pl = o.optJSONObject("playlist") ?: return Result(false, "歌单数据为空")
            val tracks = pl.optJSONArray("tracks") ?: JSONArray()
            val ids = pl.optJSONArray("trackIds") ?: JSONArray()
            val total = if (ids.length() > 0) ids.length() else tracks.length()
            val info = PlaylistInfo(
                platformId = pid,
                name = pl.optString("name"),
                cover = pl.optString("coverImgUrl"),
                creator = pl.optJSONObject("creator")?.optString("nickname") ?: "",
                trackCount = total,
                description = pl.optString("description"),
                updateTime = pl.optLong("updateTime")
            )
            val songs = ArrayList<Song>()
            for (i in 0 until tracks.length()) songs.add(songFromTrack(tracks.getJSONObject(i), i))
            // 补全超过首屏的歌曲
            if (ids.length() > tracks.length()) {
                val known = songs.map { it.platformId }.toHashSet()
                val missing = ArrayList<String>()
                for (i in 0 until ids.length()) {
                    val id = ids.getJSONObject(i).optString("id")
                    if (id.isNotEmpty() && !known.contains(id)) missing.add(id)
                }
                var idx = songs.size
                missing.chunked(200).forEach { chunk ->
                    val more = songDetails(chunk)
                    for (s in more) { songs.add(s.copy(position = idx)); idx++ }
                }
            }
            Result(true, "", JSONObject().apply {
                put("playlist", JSONObject().apply {
                    put("platformId", info.platformId); put("name", info.name); put("cover", info.cover)
                    put("creator", info.creator); put("songCount", info.trackCount)
                    put("description", info.description); put("updateTime", info.updateTime)
                })
                put("songs", JSONArray().also { a -> songs.forEach { a.put(it.toJson()) } })
            })
        } catch (e: Throwable) { Log.e("api", "playlist $pid", e); Result(false, "网络异常：" + (e.message ?: "请求失败")) }
    }

    private fun songFromTrack(t: JSONObject, idx: Int): Song {
        val ar = t.optJSONArray("ar") ?: t.optJSONArray("artists") ?: JSONArray()
        val names = ArrayList<String>()
        for (i in 0 until ar.length()) names.add(ar.getJSONObject(i).optString("name"))
        val al = t.optJSONObject("al") ?: t.optJSONObject("album") ?: JSONObject()
        val fee = t.optInt("fee", 0)
        val st = t.optInt("st", 0)
        val available = st >= 0 && t.optJSONObject("privilege")?.optInt("st", 0).let { it == null || it >= 0 }
        return Song(
            platformId = t.optString("id"),
            title = t.optString("name", "未知歌曲"),
            artist = if (names.isEmpty()) "未知歌手" else names.joinToString("/"),
            album = al.optString("name", ""),
            duration = (t.optLong("dt", t.optLong("duration", 0)) / 1000).toInt(),
            coverUrl = al.optString("picUrl", ""),
            fee = fee,
            available = available,
            maxBitrate = t.optJSONObject("h")?.optInt("br", 0) ?: 0,
            publishTime = t.optLong("publishTime", 0),
            position = idx
        )
    }

    fun songDetails(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        return try {
            val idStr = ids.joinToString(",") { it }
            val r = Http.get("$BASE/api/song/detail?ids=[$idStr]", HEADERS)
            val o = JSONObject(r.body)
            val arr = o.optJSONArray("songs") ?: return emptyList()
            val out = ArrayList<Song>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                out.add(songFromTrack(t, i))
            }
            out
        } catch (e: Throwable) { Log.e("api", "songDetails", e); emptyList() }
    }

    fun album(aid: String): Result {
        return try {
            val r = Http.get("$BASE/api/album/$aid", HEADERS)
            val o = JSONObject(r.body)
            val al = o.optJSONObject("album") ?: return Result(false, "专辑不存在或无法访问")
            val tracks = al.optJSONArray("songs") ?: JSONArray()
            val songs = ArrayList<Song>()
            for (i in 0 until tracks.length()) songs.add(songFromTrack(tracks.getJSONObject(i), i))
            Result(true, "", JSONObject().apply {
                put("playlist", JSONObject().apply {
                    put("platformId", aid); put("name", al.optString("name"))
                    put("cover", al.optString("picUrl")); put("creator", al.optJSONObject("artist")?.optString("name") ?: "")
                    put("songCount", songs.size); put("description", al.optString("description")); put("type", "album")
                })
                put("songs", JSONArray().also { a -> songs.forEach { a.put(it.toJson()) } })
            })
        } catch (e: Throwable) { Result(false, "网络异常：" + (e.message ?: "")) }
    }

    data class PlaySource(val ok: Boolean, val url: String = "", val bitrate: Int = 0, val size: Long = 0,
                          val format: String = "", val level: String = "", val reason: String = "", val fatal: Boolean = false, val trial: Boolean = false)

    private fun brOf(quality: String): Int = when (quality) {
        "lossless" -> 999000
        "exhigh" -> 320000
        "higher" -> 192000
        else -> 128000
    }

    private fun levelChain(quality: String): List<String> = when (quality) {
        "lossless" -> listOf("lossless", "exhigh", "higher", "standard")
        "exhigh" -> listOf("exhigh", "higher", "standard")
        "higher" -> listOf("higher", "standard")
        else -> listOf("standard")
    }

    /** 使用 Web 播放器同款接口 /api/song/enhance/player/url/v1（支持 level 档位） */
    fun playUrl(sid: String, quality: String): PlaySource {
        var last = PlaySource(false, reason = "平台未提供可用的音频源")
        for (lv in levelChain(quality)) {
            val one = tryOne(sid, lv)
            if (one.ok) return one
            last = one
            if (one.fatal) return one
        }
        return last
    }

    private fun tryOne(sid: String, level: String): PlaySource {
        return try {
            val r = Http.get("$BASE/api/song/enhance/player/url/v1?ids=[$sid]&level=$level&encodeType=mp3", HEADERS, 20000)
            val o = JSONObject(r.body)
            val data = o.optJSONArray("data")?.optJSONObject(0)
                ?: return PlaySource(false, reason = "接口未返回数据", fatal = true)
            val url = data.optString("url", "")
            val code = data.optInt("code", 0)
            if (url.isEmpty() || url == "null") {
                val fee = data.optInt("fee", 0)
                val reason = when (code) {
                    404 -> "平台无此歌曲资源"
                    -110 -> "版权受限，当前无法获取音频"
                    else -> if (fee == 1 || fee == 8) "会员/付费曲目，平台未向本账号提供音频源"
                            else "平台未提供可用的音频源（可能受版权或地区限制）"
                }
                val fatal = (code == 404 || code == -110)
                return PlaySource(false, reason = reason, fatal = fatal)
            }
            val trialInfo = data.optJSONObject("freeTrialInfo")
            val trialPriv = data.optJSONObject("freeTrialPrivilege")
            val trial = (trialInfo != null && trialInfo.length() > 0) ||
                    (trialPriv != null && trialPriv.optInt("resConsumable", 0) == 1)
            PlaySource(true, url = url, bitrate = data.optInt("br", 0), size = data.optLong("size", 0),
                format = data.optString("type", "mp3"), level = data.optString("level", level), trial = trial)
        } catch (e: Throwable) {
            Log.e("api", "playUrl " + sid, e)
            PlaySource(false, reason = "网络异常：" + (e.message ?: "请求失败"), fatal = false)
        }
    }

    data class Lyric(val lrc: String = "", val tlyric: String = "", val yrc: String = "", val ok: Boolean = false)

    fun lyric(sid: String): Lyric {
        return try {
            val r = Http.get("$BASE/api/song/lyric?os=pc&id=$sid&lv=-1&kv=-1&tv=-1", HEADERS)
            val o = JSONObject(r.body)
            val lrc = o.optJSONObject("lrc")?.optString("lyric", "") ?: ""
            val tl = o.optJSONObject("tlyric")?.optString("lyric", "") ?: ""
            val yrc = o.optJSONObject("yrc")?.optString("lyric", "") ?: ""
            Lyric(lrc = lrc, tlyric = tl, yrc = yrc, ok = lrc.isNotBlank() || yrc.isNotBlank())
        } catch (e: Throwable) { Log.e("api", "lyric $sid", e); Lyric() }
    }

    fun search(keyword: String, limit: Int = 20): Result {
        return try {
            val r = Http.postForm("$BASE/api/search/get", mapOf("s" to keyword, "type" to "1", "limit" to limit.toString(), "offset" to "0"), HEADERS)
            val o = JSONObject(r.body)
            val songs = o.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            val out = ArrayList<Song>()
            for (i in 0 until songs.length()) out.add(songFromTrack(songs.getJSONObject(i), i))
            Result(true, "", JSONObject().put("songs", JSONArray().also { a -> out.forEach { a.put(it.toJson()) } }))
        } catch (e: Throwable) { Result(false, "搜索失败：" + (e.message ?: "")) }
    }

    fun checkNetwork(): JSONObject {
        val o = JSONObject()
        val t0 = nowMs()
        return try {
            val r = Http.get("$BASE/", HEADERS, 8000)
            o.put("ok", r.code == 200); o.put("latency", nowMs() - t0); o.put("message", "连接正常")
        } catch (e: Throwable) { o.put("ok", false); o.put("latency", 0); o.put("message", "连接失败：" + (e.message ?: "")) }
    }
}

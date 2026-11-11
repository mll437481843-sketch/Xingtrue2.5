package com.ncmd

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

object Tagger {
    private fun syncsafe(n: Int): ByteArray = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(),
        ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

    private fun frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        out.write(syncsafe(data.size))
        out.write(byteArrayOf(0, 0))
        out.write(data)
        return out.toByteArray()
    }

    private fun textFrame(id: String, value: String): ByteArray? {
        if (value.isBlank()) return null
        val b = ByteArrayOutputStream()
        b.write(0x01); b.write(0xFF); b.write(0xFE)
        b.write(value.toByteArray(Charsets.UTF_16LE))
        return frame(id, b.toByteArray())
    }

    private fun usltFrame(lyric: String): ByteArray? {
        if (lyric.isBlank()) return null
        val b = ByteArrayOutputStream()
        b.write(0x01); b.write(0xFF); b.write(0xFE)
        b.write("chi".toByteArray(Charsets.UTF_16LE)); b.write(0); b.write(0)
        b.write(lyric.toByteArray(Charsets.UTF_16LE))
        return frame("USLT", b.toByteArray())
    }

    private fun apicFrame(img: ByteArray, mime: String): ByteArray? {
        if (img.isEmpty()) return null
        val b = ByteArrayOutputStream()
        b.write(0x00)
        b.write(mime.toByteArray(Charsets.ISO_8859_1)); b.write(0)
        b.write(0x03); b.write(0); b.write(0)
        b.write(img)
        return frame("APIC", b.toByteArray())
    }

    fun writeMp3(file: File, title: String, artist: String, album: String, track: Int, year: String,
                 cover: ByteArray?, lyric: String?): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(file, "rw")
            val header = ByteArray(10)
            raf.seek(0)
            raf.readFully(header)
            var audioStart = 0L
            if (header[0] == 'I'.toByte() && header[1] == 'D'.toByte() && header[2] == '3'.toByte()) {
                val size = ((header[6].toInt() and 0x7F) shl 21) or ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or (header[9].toInt() and 0x7F)
                audioStart = (10 + size).toLong()
            }
            val total = raf.length()
            val audioLen = (total - audioStart).toInt()
            if (audioLen <= 0) return false
            val audio = ByteArray(audioLen)
            raf.seek(audioStart)
            raf.readFully(audio)
            raf.close(); raf = null

            val frames = ByteArrayOutputStream()
            textFrame("TIT2", title)?.let { frames.write(it) }
            textFrame("TPE1", artist)?.let { frames.write(it) }
            textFrame("TALB", album)?.let { frames.write(it) }
            if (track > 0) textFrame("TRCK", track.toString())?.let { frames.write(it) }
            if (year.isNotBlank()) textFrame("TYER", year)?.let { frames.write(it) }
            if (cover != null && cover.isNotEmpty()) {
                val mime = if (cover.size > 3 && cover[0].toInt() == 0x89) "image/png" else "image/jpeg"
                apicFrame(cover, mime)?.let { frames.write(it) }
            }
            if (!lyric.isNullOrBlank()) usltFrame(lyric)?.let { frames.write(it) }
            val body = frames.toByteArray()
            if (body.isEmpty()) return false
            val tag = ByteArrayOutputStream()
            tag.write("ID3".toByteArray(Charsets.ISO_8859_1))
            tag.write(byteArrayOf(3, 0, 0))
            tag.write(syncsafe(body.size))
            tag.write(body)

            val out = File(file.absolutePath + ".tmp")
            out.outputStream().use { os ->
                os.write(tag.toByteArray())
                os.write(audio)
            }
            val bak = File(file.absolutePath + ".bak")
            if (bak.exists()) bak.delete()
            if (!file.renameTo(bak)) { out.delete(); return false }
            if (!out.renameTo(file)) { bak.renameTo(file); out.delete(); return false }
            bak.delete()
            return true
        } catch (e: Throwable) {
            Log.e("tag", "writeMp3 " + file.name, e)
            return false
        } finally { try { raf?.close() } catch (_: Throwable) {} }
    }
}

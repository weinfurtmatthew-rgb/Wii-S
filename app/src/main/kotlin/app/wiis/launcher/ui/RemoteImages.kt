package app.wiis.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Small images from the web (RetroAchievements badges and game icons): cached on disk and in memory. */
class RemoteImages(private val dir: File) {
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(url: String, sizePx: Int): Bitmap? {
        val key = "$url@$sizePx"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            dir.mkdirs()
            val file = File(dir, sha1(url))
            if (!file.exists()) {
                val bytes = runCatching { fetch(url) }.getOrNull() ?: return@withContext null
                file.writeBytes(bytes)
            }
            decode(file, sizePx)?.also { memory.put(key, it) }
        }
    }

    private fun fetch(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Wii-S launcher")
        }
        try {
            return if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

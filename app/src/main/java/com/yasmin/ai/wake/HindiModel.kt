package com.yasmin.ai.wake

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads and stores the small offline Vosk Hindi model used for "Hey Yasmin". */
object HindiModel {
    const val NAME = "vosk-model-small-hi-0.22"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/$NAME.zip"

    fun dir(ctx: Context) = File(ctx.filesDir, "vosk_hi/$NAME")

    fun isInstalled(ctx: Context) = File(dir(ctx), "conf").isDirectory

    fun remove(ctx: Context) {
        File(ctx.filesDir, "vosk_hi").deleteRecursively()
    }

    /** Blocking. Call from a background thread. Throws IOException on failure. */
    fun download(ctx: Context, onProgress: (Int) -> Unit) {
        val root = File(ctx.filesDir, "vosk_hi").apply { mkdirs() }
        val zip = File(ctx.cacheDir, "$NAME.zip")

        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (conn.responseCode != 200) throw IOException("Download failed: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            zip.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done * 90 / total).toInt())
                }
            }
        }

        val tmp = File(root, "tmp").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(zip.inputStream()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val f = File(tmp, e.name)
                if (!f.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                    throw IOException("Unsafe file in download")
                }
                if (e.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zin.copyTo(it) }
                }
                e = zin.nextEntry
            }
        }
        zip.delete()

        val dest = dir(ctx)
        dest.deleteRecursively()
        if (!File(tmp, NAME).renameTo(dest)) throw IOException("Could not install voice model")
        tmp.deleteRecursively()
        onProgress(100)
    }
}

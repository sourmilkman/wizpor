package dev.wizpor

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

object ModelManager {
    const val URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2"
    const val APPROX_MB = 480
    private const val ARCHIVE = "parakeet.tar.bz2"

    fun modelDir(ctx: Context) = File(ctx.filesDir, "models/parakeet-tdt-0.6b-v2")
    fun isReady(ctx: Context) = File(modelDir(ctx), ".ready").exists()
    private fun archive(ctx: Context) = File(ctx.getExternalFilesDir(null), ARCHIVE)

    data class Progress(val status: Int, val done: Long, val total: Long, val reason: Int)

    fun startDownload(ctx: Context): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val old = Prefs(ctx).downloadId
        if (old >= 0) dm.remove(old)
        archive(ctx).delete()
        val req = DownloadManager.Request(Uri.parse(URL))
            .setTitle("wizpor speech model")
            .setDescription("About $APPROX_MB MB, one time only")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, null, ARCHIVE)
            .setAllowedOverMetered(true)
        val id = dm.enqueue(req)
        Prefs(ctx).downloadId = id
        return id
    }

    fun query(ctx: Context): Progress? {
        val id = Prefs(ctx).downloadId
        if (id < 0) return null
        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return Progress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    /** Unpacks the downloaded archive. Blocking; call off the main thread. */
    fun extract(ctx: Context, onProgress: (Float) -> Unit) {
        val src = archive(ctx)
        val total = src.length().coerceAtLeast(1)
        val dest = modelDir(ctx)
        dest.deleteRecursively()
        dest.mkdirs()
        var read = 0L
        val counting = object : FilterInputStream(BufferedInputStream(src.inputStream(), 1 shl 16)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) { read += n; onProgress(read.toFloat() / total) }
                return n
            }
        }
        TarArchiveInputStream(BZip2CompressorInputStream(counting as InputStream)).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (!name.endsWith(".onnx") && name != "tokens.txt") continue
                File(dest, name).outputStream().use { tar.copyTo(it, 1 shl 16) }
            }
        }
        val required = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
        val missing = required.filter { !File(dest, it).exists() }
        require(missing.isEmpty()) { "Model archive incomplete: missing $missing" }
        File(dest, ".ready").writeText("ok")
        src.delete()
        Prefs(ctx).downloadId = -1
    }

    fun archiveDownloaded(ctx: Context) = archive(ctx).exists()
}

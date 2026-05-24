package com.example.upbeat.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SongDownloader {

    private const val TAG = "SongDownloader"

    /** Returns the local songs directory, creating it if needed. */
    private fun songsDir(context: Context): File =
        File(context.filesDir, "songs").also { it.mkdirs() }

    /** Sanitises an S3 key into a safe flat filename, e.g. "uuid/My Song.flac" → "uuid_My Song.flac" */
    private fun localName(s3Key: String): String = s3Key.replace("/", "_")

    /** Returns the cached file for this key, or null if not yet downloaded. */
    fun cachedFile(context: Context, s3Key: String): File? {
        val f = File(songsDir(context), localName(s3Key))
        return if (f.exists() && f.length() > 0) f else null
    }

    /**
     * Downloads [s3Key] to local storage if not already cached.
     *
     * @param onProgress called with bytes downloaded and total bytes (-1 if unknown)
     * @return the local [File] on success, null on failure
     */
    suspend fun ensureDownloaded(
        context: Context,
        s3Key: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        // Return immediately if already cached
        cachedFile(context, s3Key)?.let {
            Log.d(TAG, "Cache hit: $s3Key → ${it.absolutePath}")
            onProgress(it.length(), it.length())
            return@withContext it
        }

        // Need a presigned URL to download
        val url = S3Uploader.getPresignedUrl(context, s3Key) ?: run {
            Log.e(TAG, "Could not get presigned URL for $s3Key")
            return@withContext null
        }

        val dest = File(songsDir(context), localName(s3Key))
        val tmp  = File(songsDir(context), "${localName(s3Key)}.tmp")

        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                requestMethod  = "GET"
                connect()
            }

            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "HTTP ${conn.responseCode} downloading $s3Key")
                conn.disconnect()
                return@withContext null
            }

            val total = conn.contentLengthLong  // -1 if unknown
            var downloaded = 0L

            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()

            tmp.renameTo(dest)
            Log.d(TAG, "Downloaded $s3Key → ${dest.absolutePath} (${dest.length()} B)")
            dest
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $s3Key: ${e.message}", e)
            tmp.delete()
            null
        }
    }

    /** Deletes the locally cached file for [s3Key], if it exists. */
    fun evict(context: Context, s3Key: String) {
        cachedFile(context, s3Key)?.delete()
    }

    /** Deletes all locally cached songs. */
    fun evictAll(context: Context) {
        songsDir(context).listFiles()?.forEach { it.delete() }
    }
}

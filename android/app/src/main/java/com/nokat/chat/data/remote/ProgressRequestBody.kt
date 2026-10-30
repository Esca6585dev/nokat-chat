package com.nokat.chat.data.remote

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

/**
 * Streams a `content://` Uri straight from the ContentResolver into the request (never loads
 * the whole file in memory) and reports progress as a 0..1 fraction (throttled to ~1% steps).
 * Each [writeTo] reopens the stream, so OkHttp retries are safe.
 */
class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val length: Long,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = if (length > 0) length else -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        input.use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var written = 0L
            var lastReported = -1
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                if (length > 0) {
                    val percent = ((written * 100) / length).toInt().coerceIn(0, 100)
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(percent / 100f)
                    }
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}

/** In-memory body (e.g. an encrypted blob) that reports upload progress as 0..1 in ~1% steps. */
class ByteArrayProgressBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = bytes.size
        var written = 0
        var lastReported = -1
        while (written < total) {
            val n = minOf(CHUNK, total - written)
            sink.write(bytes, written, n)
            written += n
            val percent = ((written.toLong() * 100) / total).toInt()
            if (percent != lastReported) {
                lastReported = percent
                onProgress(percent / 100f)
            }
        }
    }

    private companion object {
        const val CHUNK = 16 * 1024
    }
}

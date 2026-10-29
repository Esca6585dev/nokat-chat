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

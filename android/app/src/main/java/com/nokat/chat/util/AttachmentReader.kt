package com.nokat.chat.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.nokat.chat.data.model.LocalFile

/** Reads display name, size, MIME type and image bounds of a picked Uri. Blocking: call on Dispatchers.IO. */
class AttachmentReader(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun describe(uri: Uri): LocalFile? {
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "query failed for $uri", e)
            return null
        }

        val displayName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "file"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val isImage = mime.lowercase() in IMAGE_MIMES

        var width = 0
        var height = 0
        if (isImage) {
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    width = options.outWidth.coerceAtLeast(0)
                    height = options.outHeight.coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "bounds decode failed for $uri", e)
            }
        }
        return LocalFile(
            uri = uri.toString(),
            name = displayName,
            size = size,
            mime = mime,
            isImage = isImage,
            width = width,
            height = height,
        )
    }

    companion object {
        private const val TAG = "AttachmentReader"

        /** Same as the server: only these are sent as `image` messages. */
        val IMAGE_MIMES = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")

        /** POST /uploads limit. */
        const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
    }
}

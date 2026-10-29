package com.nokat.chat.util

import java.time.Instant
import java.time.OffsetDateTime

object Timestamps {
    /** Parses an RFC3339 timestamp (any fraction precision, `Z` or offset) to epoch millis; 0 on failure. */
    fun toMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (e2: Exception) {
                0L
            }
        }
    }

    fun nowIso(): String = Instant.now().toString()
}

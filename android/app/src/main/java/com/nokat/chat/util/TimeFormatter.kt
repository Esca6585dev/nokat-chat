package com.nokat.chat.util

import android.content.Context
import com.nokat.chat.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turkmen-friendly date/time labels. Thread-safe; can be used from background dispatchers. */
class TimeFormatter(context: Context) {

    private val appContext = context.applicationContext
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dmy: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val dmyFull: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val weekdays: Array<String> = appContext.resources.getStringArray(R.array.weekdays_short)
    private val months: Array<String> = appContext.resources.getStringArray(R.array.months)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    /** Chat list: `14:05` today, `Duş` within a week, else `29.10.26`. */
    fun chatListTime(iso: String?): String {
        val millis = Timestamps.toMillis(iso)
        if (millis <= 0L) return ""
        val dt = local(millis)
        val today = LocalDate.now(zone())
        val date = dt.toLocalDate()
        return when {
            date == today -> hm.format(dt)
            date.isAfter(today.minusDays(7)) -> weekdays[dt.dayOfWeek.value - 1]
            else -> dmy.format(dt)
        }
    }

    /** Bubble time, `HH:mm`. */
    fun messageTime(iso: String?): String {
        val millis = Timestamps.toMillis(iso)
        return if (millis <= 0L) "" else hm.format(local(millis))
    }

    /** Local epoch day used for date separators. */
    fun epochDay(iso: String?): Long {
        val millis = Timestamps.toMillis(iso)
        return if (millis <= 0L) LocalDate.now(zone()).toEpochDay() else local(millis).toLocalDate().toEpochDay()
    }

    /** Date separator label: `Şu gün`, `Düýn`, `29 oktýabr`, `29 oktýabr 2025`. */
    fun dayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val today = LocalDate.now(zone())
        return when {
            date == today -> appContext.getString(R.string.today)
            date == today.minusDays(1) -> appContext.getString(R.string.yesterday)
            date.year == today.year -> "${date.dayOfMonth} ${months[date.monthValue - 1]}"
            else -> "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"
        }
    }

    /** `soňky gezek şu gün 14:05 göründi`. */
    fun lastSeen(iso: String?): String {
        val millis = Timestamps.toMillis(iso)
        if (millis <= 0L || System.currentTimeMillis() - millis < 60_000L) {
            return appContext.getString(R.string.last_seen_recently)
        }
        val dt = local(millis)
        val today = LocalDate.now(zone())
        val whenText = when (dt.toLocalDate()) {
            today -> appContext.getString(R.string.last_seen_today, hm.format(dt))
            today.minusDays(1) -> appContext.getString(R.string.last_seen_yesterday, hm.format(dt))
            else -> dmyFull.format(dt)
        }
        return appContext.getString(R.string.last_seen, whenText)
    }
}

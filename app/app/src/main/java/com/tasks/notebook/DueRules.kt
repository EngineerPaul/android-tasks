package com.tasks.notebook

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object DueRules {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun startOfMinute(epoch: Long): Long {
        val time = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime()
            .withSecond(0).withNano(0)
        return time.atZone(zone).toInstant().toEpochMilli()
    }

    fun earliest(now: Long): Long {
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
            .withSecond(0).withNano(0)
            .plusMinutes(2)
        return time.atZone(zone).toInstant().toEpochMilli()
    }

    fun defaultDue(now: Long): Long {
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
            .withSecond(0).withNano(0)
            .plusHours(1)
        return time.atZone(zone).toInstant().toEpochMilli()
    }

    fun formatDate(epoch: Long): String =
        dateFormat.format(Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate())

    fun formatTime(epoch: Long): String =
        timeFormat.format(Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime())

    fun parse(date: String, time: String): Long? = try {
        val day = LocalDate.parse(date.trim(), dateFormat)
        val clock = LocalTime.parse(time.trim(), timeFormat)
        day.atTime(clock).atZone(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    fun shiftDay(epoch: Long, days: Int, now: Long): Long? {
        val shifted = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime().plusDays(days.toLong())
        val result = shifted.atZone(zone).toInstant().toEpochMilli()
        if (days < 0 && result < startOfMinute(now)) return null
        return result
    }

    fun shiftHour(epoch: Long, direction: Int, now: Long): Long {
        var shifted = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime()
            .plusHours(direction.toLong())
            .withMinute(0).withSecond(0).withNano(0)
        var result = shifted.atZone(zone).toInstant().toEpochMilli()
        if (direction < 0) {
            val floor = earliest(now)
            if (result < floor) result = floor
        }
        return result
    }

    fun canShiftHourLeft(epoch: Long, now: Long): Boolean = shiftHour(epoch, -1, now) != epoch

    fun nextFuture(epoch: Long, everyDays: Int, now: Long): Long {
        var cursor = epoch
        val step = everyDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        while (cursor <= now) cursor += step
        return cursor
    }

    fun pickerMillis(epoch: Long): Long {
        val date = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun fromPicker(millis: Long, timeEpoch: Long): Long {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(timeEpoch).atZone(zone).toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    fun todayPickerMillis(now: Long): Long = pickerMillis(now)

    fun combine(date: LocalDateTime): Long = date.atZone(zone).toInstant().toEpochMilli()
}

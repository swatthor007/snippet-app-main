package com.sockjaw.clipsy.data

import java.util.Calendar

object Stats {
    private const val CHARS_PER_WORD = 5.0
    private const val WORDS_PER_MINUTE = 40.0

    fun minutesSaved(chars: Long): Double = chars / CHARS_PER_WORD / WORDS_PER_MINUTE

    /** "3.4 hours", "10 min", "0 min". */
    fun formatSaved(chars: Long): String {
        val minutes = minutesSaved(chars)
        return when {
            minutes >= 60 -> "%.1f hours".format(minutes / 60)
            chars > 0 && minutes < 1 -> "< 1 min"
            else -> "${minutes.toInt()} min"
        }
    }

    fun formatChars(chars: Long): String = when {
        chars >= 1_000_000 -> "%.1fM".format(chars / 1_000_000.0)
        chars >= 1_000 -> "%.1fk".format(chars / 1_000.0)
        else -> chars.toString()
    }

    fun startOfMonth(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }.timeInMillis

    fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        startOfDay()
    }.timeInMillis

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /** Characters saved per day for the last [days] days, oldest first, ending today. */
    fun dailyChars(events: List<UsageEvent>, days: Int, now: Long = System.currentTimeMillis()): List<Long> {
        val dayMs = 24L * 60 * 60 * 1000
        val today = startOfDay(now)
        val buckets = LongArray(days)
        for (e in events) {
            val ago = ((today - startOfDay(e.timestamp)) / dayMs).toInt()
            if (ago in 0 until days) buckets[days - 1 - ago] += e.charsSaved.toLong()
        }
        return buckets.toList()
    }

    /** Number of expansions per day for the last [days] days, oldest first. */
    fun dailyCounts(events: List<UsageEvent>, days: Int, now: Long = System.currentTimeMillis()): List<Long> =
        dailyChars(events.map { it.copy(charsSaved = 1) }, days, now)

    fun relativeAge(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null) return "Never"
        val s = (now - timestamp) / 1000
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            s < 7 * 86_400 -> "${s / 86_400}d"
            s < 30 * 86_400 -> "${s / (7 * 86_400)}w"
            else -> "${s / (30 * 86_400)}mo"
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

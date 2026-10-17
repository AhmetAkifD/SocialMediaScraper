package com.socialmediascraper.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TR = Locale.forLanguageTag("tr-TR")
private val DATE_THIS_YEAR = DateTimeFormatter.ofPattern("d MMM", TR)
private val DATE_FULL = DateTimeFormatter.ofPattern("d MMM yyyy", TR)
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", TR)

/** "şimdi", "5 dk", "3 sa", "2 g" ya da tarih. */
fun formatRelative(time: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - time).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "şimdi"
        diff < 3600 -> "${diff / 60} dk"
        diff < 86_400 -> "${diff / 3600} sa"
        diff < 7 * 86_400 -> "${diff / 86_400} g"
        else -> {
            val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
            val thisYear = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).year == date.year
            (if (thisYear) DATE_THIS_YEAR else DATE_FULL).format(date)
        }
    }
}

fun formatDateTime(time: Long): String = DATE_TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

/** 1234 -> "1,2 B", 2_500_000 -> "2,5 Mn" */
fun formatCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(TR, "%.1f B", value / 1_000.0).replace(",0 ", " ")
    else -> String.format(TR, "%.1f Mn", value / 1_000_000.0).replace(",0 ", " ")
}

fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes dakika"
    minutes % 60 == 0 -> "${minutes / 60} saat"
    else -> "$minutes dakika"
}

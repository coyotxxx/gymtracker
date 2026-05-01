package pl.filebit.gymtracker.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

fun formatWeight(kg: Double): String {
    return if (kg == kg.toLong().toDouble()) {
        "${kg.toLong()}"
    } else {
        String.format(Locale.US, "%.1f", kg)
    }
}

fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatTimerSeconds(remainingSec: Int): String {
    val m = remainingSec / 60
    val s = remainingSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDate(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val month = dt.monthNumber.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "$day.$month.${dt.year} $hour:$minute"
}

fun formatDateShort(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val month = dt.monthNumber.toString().padStart(2, '0')
    return "$day.$month"
}

private val PL_DAY_LONG = arrayOf(
    "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"
)
private val PL_MONTH_SHORT = arrayOf(
    "sty", "lut", "mar", "kwi", "maj", "cze",
    "lip", "sie", "wrz", "paź", "lis", "gru"
)

/** "Wtorek, 29 kwi · 18:42" — dla nagłówka szczegółów treningu. */
fun formatDateLongPl(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val dayName = PL_DAY_LONG[dt.dayOfWeek.isoDayNumber - 1]
    val monthShort = PL_MONTH_SHORT[dt.monthNumber - 1]
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "$dayName, ${dt.dayOfMonth} $monthShort · $hour:$minute"
}

package dev.gavenda.kozeki.ui

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.gavenda.kozeki.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/** "1h 22m", "24m" or "<1m". */
@Composable
@ReadOnlyComposable
fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        totalMinutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        durationMs > 0 -> stringResource(R.string.duration_under_minute)
        else -> stringResource(R.string.duration_minutes, 0)
    }
}

/** Ui's birthday, 23 April, which is also World Book Day. */
fun isUiBirthday(date: LocalDate): Boolean = date.month == Month.APRIL && date.dayOfMonth == 23

fun formatPercent(fraction: Double): String = "${(fraction.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun formatDate(epochMillis: Long): String =
    formatDate(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** A moment as its date and the time of day, such as "11 Oct 2026, 21:14". */
fun formatDateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

/** How long ago [epochMillis] was, such as "Now", "1 minute ago" or "2 days ago". */
fun formatTimeAgo(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.getDefault(),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
    )
    val minutes = (now - epochMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    val (amount, unit) = when {
        minutes < 1 -> return formatter.format(
            RelativeDateTimeFormatter.Direction.PLAIN,
            RelativeDateTimeFormatter.AbsoluteUnit.NOW,
        )
        minutes < 60 -> minutes to RelativeDateTimeFormatter.RelativeUnit.MINUTES
        hours < 24 -> hours to RelativeDateTimeFormatter.RelativeUnit.HOURS
        days < 7 -> days to RelativeDateTimeFormatter.RelativeUnit.DAYS
        days < 30 -> days / 7 to RelativeDateTimeFormatter.RelativeUnit.WEEKS
        days < 365 -> days / 30 to RelativeDateTimeFormatter.RelativeUnit.MONTHS
        else -> days / 365 to RelativeDateTimeFormatter.RelativeUnit.YEARS
    }
    return formatter.format(amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
}

fun formatMonth(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy"))

/** A price stored in minor units, such as cents, in the conventions of its currency. */
fun formatPrice(minorUnits: Long, currencyCode: String?): String {
    val currency = currencyCode?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: defaultCurrency()
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    return NumberFormat.getCurrencyInstance().apply { this.currency = currency }
        .format(minorUnits / 10.0.pow(digits))
}

fun defaultCurrency(): Currency =
    runCatching { Currency.getInstance(Locale.getDefault()) }.getOrDefault(Currency.getInstance("USD"))

/** Turns what the user typed into minor units of [currencyCode], or null if it is not a number. */
fun parsePrice(text: String, currencyCode: String): Long? {
    val amount = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
    val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrDefault(2)
    return (amount * 10.0.pow(digits.coerceAtLeast(0))).roundToInt().toLong()
}

/** The inverse of [parsePrice], for pre-filling a text field. */
fun priceToInput(minorUnits: Long, currencyCode: String?): String {
    val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }.getOrDefault(2)
        .coerceAtLeast(0)
    return String.format(Locale.ROOT, "%.${digits}f", minorUnits / 10.0.pow(digits))
}

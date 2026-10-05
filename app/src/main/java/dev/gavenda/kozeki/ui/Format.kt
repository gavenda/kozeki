package dev.gavenda.kozeki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.gavenda.kozeki.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
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

fun formatPercent(fraction: Double): String = "${(fraction.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun formatDate(epochMillis: Long): String =
    formatDate(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

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

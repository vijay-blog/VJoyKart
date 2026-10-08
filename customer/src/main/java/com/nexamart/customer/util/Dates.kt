package com.nexamart.customer.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 parsing compatible with Dart's DateTime.parse/tryParse (minSdk 24 has no java.time).
 * Timestamps without an offset are interpreted as local time, like Dart.
 */
object IsoDates {
    private val pattern = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?)?\s*(Z|z|[+-]\d{2}(?::?\d{2})?)?$""",
    )

    fun parse(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        val m = pattern.matchEntire(text) ?: return null
        val g = m.groupValues
        val zone = g[8]
        val calendar = Calendar.getInstance(if (zone.isEmpty()) TimeZone.getDefault() else TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(
            g[1].toInt(), g[2].toInt() - 1, g[3].toInt(),
            g[4].ifEmpty { "0" }.toInt(), g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt(),
        )
        calendar.set(Calendar.MILLISECOND, g[7].padEnd(3, '0').take(3).ifEmpty { "0" }.toInt())
        var millis = calendar.timeInMillis
        if (zone.isNotEmpty() && zone != "Z" && zone != "z") {
            val sign = if (zone[0] == '-') -1 else 1
            val digits = zone.substring(1).replace(":", "")
            val hours = digits.take(2).toInt()
            val minutes = digits.drop(2).ifEmpty { "0" }.toInt()
            millis -= sign * (hours * 3_600_000L + minutes * 60_000L)
        }
        return millis
    }

    /** UTC ISO-8601 with milliseconds, e.g. 2026-09-17T07:30:00.000Z. */
    fun formatUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
}

object Formats {
    /** Dart: '₹${value.round()}' */
    fun rupees(value: Double): String = "₹" + Math.round(value)

    /** Exact payable amount with paise, e.g. "₹800.00" (used where the customer must pay an exact sum). */
    fun rupeesExact(value: Double): String =
        "₹" + java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    fun orderDateTime(millis: Long): String = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.ENGLISH).format(Date(millis))

    fun orderDateTimeComma(millis: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(millis))

    fun time(millis: Long): String = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(millis))
}

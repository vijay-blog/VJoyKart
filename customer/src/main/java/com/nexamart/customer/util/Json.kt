package com.nexamart.customer.util

import com.squareup.moshi.Moshi
import kotlin.math.abs

typealias JsonMap = Map<String, Any?>

/**
 * Dynamic JSON helpers. The backend returns many numbers as strings (BigDecimal) and the
 * Flutter app parsed every field defensively, so models are parsed from untyped maps exactly
 * like the Dart `fromJson` factories instead of strict data classes.
 */
object Json {
    val moshi: Moshi = Moshi.Builder().build()
    private val anyAdapter = moshi.adapter(Any::class.java).serializeNulls()

    /** Decodes JSON text; numbers become Double, objects Map, arrays List. */
    fun decode(text: String): Any? = anyAdapter.fromJson(text)

    fun encode(value: Any?): String = anyAdapter.toJson(value)
}

@Suppress("UNCHECKED_CAST")
fun Any?.asJsonMap(): JsonMap? = when (this) {
    is Map<*, *> -> this.entries.associate { (k, v) -> k.toString() to v }
    else -> null
}

fun Any?.asJsonList(): List<Any?>? = this as? List<Any?>

/** Dart `value?.toString()` semantics: integral doubles render without ".0". */
fun Any?.jsonString(): String? = when (this) {
    null -> null
    is Double -> if (this % 1.0 == 0.0 && abs(this) < 1e15) this.toLong().toString() else this.toString()
    is Float -> this.toDouble().jsonString()
    else -> this.toString()
}

/** Dart `_number`: num → double, otherwise double.tryParse('$value') ?? 0. */
fun Any?.jsonDouble(): Double = jsonDoubleOrNull() ?: 0.0

fun Any?.jsonDoubleOrNull(): Double? = when (this) {
    null -> null
    is Number -> this.toDouble()
    else -> this.toString().trim().toDoubleOrNull()
}

/** Dart `_integer`: num → int, otherwise int.tryParse('$value') ?? 0. */
fun Any?.jsonInt(): Int = when (this) {
    null -> 0
    is Number -> this.toInt()
    else -> this.toString().trim().toIntOrNull() ?: 0
}

fun Any?.jsonLong(): Long = when (this) {
    null -> 0L
    is Number -> this.toLong()
    else -> this.toString().trim().toLongOrNull() ?: 0L
}

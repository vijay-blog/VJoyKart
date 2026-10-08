package com.nexamart.customer.data.local

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.nexamart.customer.util.Json
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * One-time import of the data the Flutter app kept in `FlutterSharedPreferences.xml`
 * (keys prefixed with `flutter.`), so updating from the Flutter build keeps the customer's
 * session, cart, saved addresses and cached orders.
 */
object FlutterPrefsMigrator {
    private const val FLUTTER_FILE = "FlutterSharedPreferences"
    private const val FLUTTER_KEY_PREFIX = "flutter."
    private const val MIGRATED_FLAG = "native.flutterPrefsMigrated"

    const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
    const val JSON_LIST_PREFIX = "$LIST_PREFIX!"
    const val DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    fun migrate(context: Context, target: CustomerPrefs) {
        if (target.prefs.getBoolean(MIGRATED_FLAG, false)) return
        val source = context.getSharedPreferences(FLUTTER_FILE, Context.MODE_PRIVATE)
        val all = source.all
        target.prefs.edit {
            for ((key, value) in all) {
                if (!key.startsWith(FLUTTER_KEY_PREFIX) || value == null) continue
                val name = key.removePrefix(FLUTTER_KEY_PREFIX)
                if (target.contains(name)) continue
                when (name) {
                    in CustomerPrefs.STRING_LIST_KEYS -> decodeStringList(value as? String)?.let {
                        putString(name, Json.encode(it))
                    }
                    in CustomerPrefs.STRING_KEYS -> (value as? String)
                        ?.takeUnless { it.startsWith(LIST_PREFIX) || it.startsWith(DOUBLE_PREFIX) }
                        ?.let { putString(name, it) }
                    in CustomerPrefs.INT_KEYS -> when (value) {
                        is Long -> putInt(name, value.toInt())
                        is Int -> putInt(name, value)
                    }
                }
            }
            putBoolean(MIGRATED_FLAG, true)
        }
        if (all.isNotEmpty()) {
            // Tokens must not linger in the old file once they live in the native store.
            source.edit { all.keys.filter { it.startsWith(FLUTTER_KEY_PREFIX) }.forEach { remove(it) } }
        }
    }

    /** Decodes a Flutter `setStringList` value (JSON form, or the legacy Java-serialized form). */
    fun decodeStringList(raw: String?, base64Decoder: (String) -> ByteArray = ::androidBase64): List<String>? {
        if (raw == null) return null
        return try {
            when {
                raw.startsWith(JSON_LIST_PREFIX) ->
                    (Json.decode(raw.substring(JSON_LIST_PREFIX.length)) as? List<*>)?.map { it.toString() }
                raw.startsWith(LIST_PREFIX) ->
                    decodeSerializedList(base64Decoder(raw.substring(LIST_PREFIX.length)))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun androidBase64(text: String): ByteArray = Base64.decode(text, Base64.DEFAULT)

    private fun decodeSerializedList(bytes: ByteArray): List<String>? =
        SafeListInputStream(ByteArrayInputStream(bytes)).use { stream ->
            (stream.readObject() as? List<*>)?.map { it.toString() }
        }

    /** Only allows the classes a `List<String>` serialization can contain. */
    private class SafeListInputStream(input: InputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (desc.name !in ALLOWED) throw java.io.InvalidClassException(desc.name, "Unexpected class")
            return super.resolveClass(desc)
        }

        companion object {
            val ALLOWED = setOf("java.util.ArrayList", "java.lang.String")
        }
    }
}

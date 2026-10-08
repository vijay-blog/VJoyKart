package com.nexamart.customer.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nexamart.customer.util.Json

/**
 * Lightweight on-device storage. Keys are identical to the ones the Flutter app used
 * (`zp.cart`, `zp.addresses`, `vk.accessToken`, …) so data carries over via [FlutterPrefsMigrator].
 */
class CustomerPrefs(context: Context) {
    internal val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String) = prefs.edit { putString(key, value) }

    fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    fun getStringList(key: String): List<String>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            (Json.decode(raw) as? List<*>)?.mapNotNull { it as? String }
        } catch (_: Exception) {
            null
        }
    }

    fun putStringList(key: String, value: List<String>) =
        prefs.edit { putString(key, Json.encode(value)) }

    fun remove(vararg keys: String) = prefs.edit { keys.forEach { remove(it) } }

    fun clearCustomerData() = remove(
        CART, ADDRESSES, SELECTED_ADDRESS_ID, ORDERS, ORDERS_OWNER, CUSTOMER_ID,
        ACCESS_TOKEN, REFRESH_TOKEN, GUEST_ACCESS_TOKEN, GUEST_REFRESH_TOKEN, CUSTOMER_PHONE,
    )

    fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        const val FILE_NAME = "vjoykart_customer"

        const val CART = "zp.cart"
        const val ADDRESSES = "zp.addresses"
        const val SELECTED_ADDRESS_ID = "zp.selectedAddressId"
        const val ORDERS = "zp.orders"
        /** Customer id that owns the cached [ORDERS], so one account never sees another account's cache. */
        const val ORDERS_OWNER = "vk.ordersOwner"
        const val CUSTOMER_ID = "nm.customerId"
        const val ACCESS_TOKEN = "vk.accessToken"
        const val REFRESH_TOKEN = "vk.refreshToken"
        const val GUEST_ACCESS_TOKEN = "vk.guestAccessToken"
        const val GUEST_REFRESH_TOKEN = "vk.guestRefreshToken"
        const val CUSTOMER_PHONE = "vk.customerPhone"

        val STRING_LIST_KEYS = setOf(CART, ADDRESSES, ORDERS)
        val STRING_KEYS = setOf(
            SELECTED_ADDRESS_ID, ACCESS_TOKEN, REFRESH_TOKEN,
            GUEST_ACCESS_TOKEN, GUEST_REFRESH_TOKEN, CUSTOMER_PHONE,
        )
        val INT_KEYS = setOf(CUSTOMER_ID, ORDERS_OWNER)
    }
}

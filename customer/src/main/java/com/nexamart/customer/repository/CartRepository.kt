package com.nexamart.customer.repository

import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.model.CartItem
import com.nexamart.customer.model.Product
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.asJsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Port of lib/providers/cart_provider.dart. The cart lives on the device (`zp.cart`), as before. */
class CartRepository(private val prefs: CustomerPrefs) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<CartItem>> = _items.asStateFlow()

    val current: List<CartItem> get() = _items.value

    private fun load(): List<CartItem> = prefs.getStringList(CustomerPrefs.CART).orEmpty().mapNotNull { raw ->
        try {
            Json.decode(raw).asJsonMap()?.let(CartItem::fromJson)
        } catch (_: Exception) {
            null
        }
    }

    private fun publish(items: List<CartItem>) {
        _items.value = items
        prefs.putStringList(CustomerPrefs.CART, items.map { Json.encode(it.toJson()) })
    }

    fun quantityOf(productId: Int): Int = current.firstOrNull { it.product.id == productId }?.quantity ?: 0

    /** Adds one unit. Returns false when the known stock limit has been reached. */
    fun add(product: Product): Boolean {
        val items = current.toMutableList()
        val index = items.indexOfFirst { it.product.id == product.id }
        if (index < 0) {
            items += CartItem(product)
        } else {
            val item = items[index]
            if (!CartMath.canIncrease(item)) return false
            items[index] = item.copy(quantity = item.quantity + 1)
        }
        publish(items)
        return true
    }

    fun remove(product: Product) {
        val items = current.toMutableList()
        val index = items.indexOfFirst { it.product.id == product.id }
        if (index < 0) return
        val item = items[index]
        if (item.quantity <= 1) items.removeAt(index) else items[index] = item.copy(quantity = item.quantity - 1)
        publish(items)
    }

    fun delete(product: Product) = publish(current.filterNot { it.product.id == product.id })

    fun clear() = publish(emptyList())
}

/** Pricing rules from CartProvider / CartScreen. */
object CartMath {
    const val FREE_DELIVERY_THRESHOLD = 499.0
    const val DELIVERY_FEE = 39.0

    fun count(items: List<CartItem>): Int = items.sumOf { it.quantity }
    fun subtotal(items: List<CartItem>): Double = items.sumOf { it.total }
    fun delivery(subtotal: Double): Double = when {
        subtotal == 0.0 -> 0.0
        subtotal >= FREE_DELIVERY_THRESHOLD -> 0.0
        else -> DELIVERY_FEE
    }
    fun total(items: List<CartItem>): Double = subtotal(items).let { it + delivery(it) }
    fun mrpTotal(items: List<CartItem>): Double = items.sumOf { it.product.mrp * it.quantity }
    /** Cart screen "Discount" row: Σ(mrp − sellingPrice) × qty, exactly as Dart (no clamping). */
    fun savings(items: List<CartItem>): Double =
        items.sumOf { (it.product.mrp - it.product.sellingPrice) * it.quantity }

    /** Stock is only enforced when the backend reported a positive stock quantity. */
    fun canIncrease(item: CartItem): Boolean =
        item.product.stockQuantity <= 0 || item.quantity < item.product.stockQuantity
}

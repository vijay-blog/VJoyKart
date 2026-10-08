package com.nexamart.customer.presentation.common

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.model.Product
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.asJsonMap

/** Typed navigation helpers (destination ids + bundle arguments). */
object Nav {
    const val ARG_TITLE = "title"
    const val ARG_KEYWORDS = "keywords"
    const val ARG_PRODUCT_JSON = "productJson"
    const val ARG_ORDER_ID = "orderId"
    const val ARG_ADDRESS_ID = "addressId"
    const val ARG_STANDALONE = "standalone"

    private val slideOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.slide_in_right)
        .setExitAnim(R.anim.fade_out_short)
        .setPopEnterAnim(R.anim.fade_in_short)
        .setPopExitAnim(R.anim.slide_out_right)
        .setLaunchSingleTop(true)
        .build()

    private fun Fragment.go(id: Int, args: Bundle? = null, options: NavOptions = slideOptions) {
        runCatching { findNavController().navigate(id, args, options) }
    }

    fun Fragment.openSearch() = go(R.id.searchFragment)

    fun Fragment.openCategory(title: String, keywords: List<String>) =
        go(R.id.categoryProductsFragment, bundleOf(ARG_TITLE to title, ARG_KEYWORDS to keywords.toTypedArray()))

    /** Not single-top: "Similar products" pushes a new detail screen on top, like Navigator.push. */
    fun Fragment.openProduct(product: Product) = go(
        R.id.productDetailFragment,
        bundleOf(ARG_PRODUCT_JSON to Json.encode(product.toJson())),
        NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.fade_out_short)
            .setPopEnterAnim(R.anim.fade_in_short)
            .setPopExitAnim(R.anim.slide_out_right)
            .build(),
    )

    fun productFrom(args: Bundle?): Product? = args?.getString(ARG_PRODUCT_JSON)?.let { raw ->
        runCatching { Json.decode(raw).asJsonMap()?.let { Product.fromJson(it) } }.getOrNull()
    }

    fun Fragment.openCart() = go(R.id.cartFragment, bundleOf(ARG_STANDALONE to true))

    fun Fragment.openCheckout() = go(R.id.checkoutFragment)

    /** Replaces checkout with the success screen so Back never returns to a paid checkout. */
    fun Fragment.openOrderSuccess(orderId: String) = go(
        R.id.orderSuccessFragment,
        bundleOf(ARG_ORDER_ID to orderId),
        NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in_short)
            .setExitAnim(R.anim.fade_out_short)
            .setPopUpTo(R.id.checkoutFragment, true)
            .build(),
    )

    fun Fragment.openOrderDetail(orderId: String) = go(R.id.orderDetailFragment, bundleOf(ARG_ORDER_ID to orderId))

    fun Fragment.openWallet() = go(R.id.walletFragment)

    fun Fragment.openSavedAddresses() = go(R.id.savedAddressesFragment)

    fun Fragment.openAddressForm(addressId: String?) =
        go(R.id.addressFormFragment, bundleOf(ARG_ADDRESS_ID to addressId))
}

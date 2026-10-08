package com.nexamart.customer

import com.nexamart.customer.data.local.FlutterPrefsMigrator
import com.nexamart.customer.model.CartItem
import com.nexamart.customer.model.CustomerOrder
import com.nexamart.customer.model.OrderStatus
import com.nexamart.customer.model.PaymentOrder
import com.nexamart.customer.model.Product
import com.nexamart.customer.repository.CartMath
import com.nexamart.customer.util.ImageUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.Base64

/** Ports of app/test/marketplace_contract_test.dart plus native-only helpers. */
class MarketplaceContractTest {
    private val catalogBase = "https://partner.example.com"

    @Test
    fun productParsesCatalogFieldsAndPreservesServerImageUrl() {
        val product = Product.fromJson(
            mapOf(
                "productId" to "42", "sku" to "ZP-MOB-001", "name" to "Mobile Phone", "brand" to "SmartTech",
                "categoryId" to 15, "categoryName" to "Mobile Phones",
                "imageUrl" to "https://cdn.example.com/mobile.png", "price" to "16999",
                "discountedPrice" to "14999", "discountPercent" to "11.76", "unit" to "1 unit",
                "availability" to "IN_STOCK", "stock" to 8, "deliveryType" to "MEDIUM",
            ),
            catalogBase,
        )
        assertEquals(42, product.id)
        assertEquals("15", product.categoryId)
        assertEquals("https://cdn.example.com/mobile.png", product.imageAsset)
        assertEquals(16999.0, product.mrp, 0.0)
        assertEquals(14999.0, product.sellingPrice, 0.0)
        assertTrue(product.available)
    }

    @Test
    fun productParsesMultipleImageObjectsInSortOrder() {
        val product = Product.fromJson(
            mapOf(
                "productId" to "44", "name" to "Salt", "price" to "30", "stock" to 20,
                "images" to listOf(
                    mapOf("imageId" to 2, "url" to "/api/v1/catalog/products/44/images/2", "sortOrder" to 1),
                    mapOf("imageId" to 1, "url" to "/api/v1/catalog/products/44/images/1", "sortOrder" to 0),
                    mapOf("imageId" to 3, "url" to "/api/v1/catalog/products/44/images/3", "sortOrder" to 2),
                ),
            ),
            catalogBase,
        )
        assertEquals(
            listOf(
                "/api/v1/catalog/products/44/images/1",
                "/api/v1/catalog/products/44/images/2",
                "/api/v1/catalog/products/44/images/3",
            ),
            product.images,
        )
        assertEquals("/api/v1/catalog/products/44/images/1", product.imageAsset)
    }

    @Test
    fun productWithZeroStockIsUnavailable() {
        val product = Product.fromJson(
            mapOf(
                "productId" to "43", "name" to "Sold out", "status" to "ACTIVE",
                "availability" to "AVAILABLE", "stock" to 0, "price" to 100,
            ),
            catalogBase,
        )
        assertFalse(product.available)
    }

    @Test
    fun productWithoutImagesFallsBackToPartnerImage() {
        val product = Product.fromJson(mapOf("productId" to 7, "name" to "Shirt", "price" to 10), catalogBase)
        assertTrue(product.imageAsset.startsWith(catalogBase))
        assertTrue(product.imageAsset.contains("/7/"))
    }

    @Test
    fun imageUrlsResolveAbsoluteRelativeAndMissing() {
        val api = "https://api.example.com"
        assertEquals("https://x.com/a.png", ImageUrls.resolve("https://x.com/a.png", api, catalogBase))
        assertEquals("$api/uploads/a.jpg", ImageUrls.resolve("/uploads/a.jpg", api, catalogBase))
        assertEquals(
            "$catalogBase/api/v1/catalog/products/4/images/1",
            ImageUrls.resolve("/api/v1/catalog/products/4/images/1", api, catalogBase),
        )
        assertEquals(
            "file:///android_asset/images/products/rice.png",
            ImageUrls.resolve("assets/images/products/rice.png", api, catalogBase),
        )
        assertNull(ImageUrls.resolve("  ", api, catalogBase))
        assertNull(ImageUrls.resolve(null, api, catalogBase))
    }

    private fun product(id: Int, mrp: Double, sell: Double, stock: Int = 10) = Product.fromJson(
        mapOf("id" to id, "name" to "P$id", "mrp" to mrp, "sellingPrice" to sell, "stockQuantity" to stock),
        catalogBase,
    )

    @Test
    fun cartItemTotalUsesSellingPriceAndQuantity() {
        val item = CartItem(product(1, 300.0, 250.0), 2)
        assertEquals(500.0, item.total, 0.0)
    }

    @Test
    fun cartMathMatchesFlutterRules() {
        val small = listOf(CartItem(product(1, 300.0, 250.0), 1))
        assertEquals(250.0, CartMath.subtotal(small), 0.0)
        assertEquals(CartMath.DELIVERY_FEE, CartMath.delivery(250.0), 0.0)
        assertEquals(289.0, CartMath.total(small), 0.0)
        assertEquals(50.0, CartMath.savings(small), 0.0)

        val large = listOf(CartItem(product(1, 300.0, 250.0), 2), CartItem(product(2, 100.0, 100.0), 1))
        assertEquals(3, CartMath.count(large))
        assertEquals(0.0, CartMath.delivery(CartMath.subtotal(large)), 0.0)
        assertEquals(600.0, CartMath.total(large), 0.0)
        assertEquals(700.0, CartMath.mrpTotal(large), 0.0)
        assertEquals(100.0, CartMath.savings(large), 0.0)
        assertEquals(0.0, CartMath.delivery(0.0), 0.0)
    }

    @Test
    fun cartRespectsStockLimit() {
        assertTrue(CartMath.canIncrease(CartItem(product(1, 10.0, 10.0, stock = 3), 2)))
        assertFalse(CartMath.canIncrease(CartItem(product(1, 10.0, 10.0, stock = 3), 3)))
        assertTrue(CartMath.canIncrease(CartItem(product(1, 10.0, 10.0, stock = 0), 50)))
    }

    @Test
    fun orderResponseMapsLegacyContract() {
        val order = CustomerOrder.fromJson(
            mapOf(
                "id" to 10, "orderNumber" to "ZP-123", "createdAt" to "2026-08-26T18:40:00",
                "status" to "PAYMENT_PENDING", "paymentMethod" to "ONLINE", "paymentStatus" to "CREATED",
                "subtotal" to 499, "deliveryFee" to 0, "discountAmount" to 20, "totalAmount" to 479,
                "addressSnapshot" to "Madhapur, Hyderabad",
                "items" to listOf(
                    mapOf(
                        "productId" to 1, "productName" to "Rice", "productBrand" to "India Gate",
                        "productImageUrl" to "assets/images/products/rice.png", "productUnit" to "5 kg",
                        "quantity" to 1, "unitPrice" to 499, "lineTotal" to 499,
                    ),
                ),
            ),
        )
        assertEquals("ZP-123", order.orderNumber)
        assertEquals(OrderStatus.paymentPending, order.status)
        assertEquals("ONLINE", order.paymentMethod)
        assertEquals(479.0, order.total, 0.0)
        assertEquals("Rice", order.items.single().product.name)
    }

    @Test
    fun orderResponseMapsActiveCustomerContract() {
        val order = CustomerOrder.fromJson(
            mapOf(
                "orderId" to "25", "createdAt" to "2026-09-17T07:30:00Z", "status" to "PENDING",
                "address" to "12 Main Road, Hyderabad, Telangana, 500001",
                "payment" to mapOf("method" to "COD", "status" to "PENDING"),
                "totals" to mapOf(
                    "subtotal" to "120.00", "deliveryFee" to "30.00", "discount" to "0.00", "grandTotal" to "150.00",
                ),
                "items" to listOf(
                    mapOf(
                        "productId" to 8, "productName" to "Backend Product", "quantity" to 2,
                        "unitPrice" to "60.00", "lineTotal" to "120.00",
                    ),
                ),
            ),
        )
        assertEquals("25", order.id)
        assertEquals("25", order.orderNumber)
        assertEquals("COD", order.paymentMethod)
        assertEquals(150.0, order.total, 0.0)
        assertEquals(8, order.items.single().product.id)
        assertEquals(2, order.items.single().quantity)
    }

    @Test
    fun paymentOrderMapsRazorpayCreateOrderResponse() {
        val payment = PaymentOrder.fromJson(
            mapOf(
                "paymentId" to 7, "orderId" to 10, "keyId" to "rzp_test_key",
                "gatewayOrderId" to "order_abc", "amount" to 479, "currency" to "INR",
            ),
        )
        assertEquals("order_abc", payment.gatewayOrderId)
        assertEquals(10, payment.orderId)
        assertEquals("INR", payment.currency)
        assertEquals(479.0, payment.amount, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun paymentOrderRejectsMissingAmount() {
        PaymentOrder.fromJson(mapOf("paymentId" to 7, "orderId" to 10))
    }

    @Test
    fun flutterStringListsAreMigrated() {
        val json = FlutterPrefsMigrator.JSON_LIST_PREFIX + "[\"a\",\"b\"]"
        assertEquals(listOf("a", "b"), FlutterPrefsMigrator.decodeStringList(json))

        val bytes = ByteArrayOutputStream().also { out ->
            ObjectOutputStream(out).use { it.writeObject(arrayListOf("x", "y")) }
        }.toByteArray()
        val legacy = FlutterPrefsMigrator.LIST_PREFIX + Base64.getEncoder().encodeToString(bytes)
        assertEquals(
            listOf("x", "y"),
            FlutterPrefsMigrator.decodeStringList(legacy) { Base64.getDecoder().decode(it) },
        )
        assertNull(FlutterPrefsMigrator.decodeStringList("plain"))
        assertNull(FlutterPrefsMigrator.decodeStringList(null))
    }
}

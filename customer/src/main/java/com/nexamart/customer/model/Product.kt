package com.nexamart.customer.model

import com.nexamart.customer.core.AppConfig
import com.nexamart.customer.util.JsonMap
import com.nexamart.customer.util.asJsonList
import com.nexamart.customer.util.asJsonMap
import com.nexamart.customer.util.jsonDouble
import com.nexamart.customer.util.jsonInt
import com.nexamart.customer.util.jsonString

/** Port of lib/models/product.dart. */
data class Product(
    val id: Int,
    val name: String,
    val description: String,
    val brand: String,
    val categoryId: String,
    val categoryName: String,
    val images: List<String>,
    val mrp: Double,
    val sellingPrice: Double,
    val discountPercentage: Double,
    val unit: String,
    val weight: String,
    val size: String,
    val attributes: Map<String, String>,
    val available: Boolean,
    val stockQuantity: Int,
    val deliveryType: String,
) {
    val category: String get() = categoryName
    val imageAsset: String get() = images.firstOrNull().orEmpty()
    val stock: Int get() = stockQuantity

    val discount: Double
        get() = if (discountPercentage > 0) {
            discountPercentage
        } else if (mrp <= 0) {
            0.0
        } else {
            ((mrp - sellingPrice) / mrp) * 100
        }

    fun toJson(): JsonMap = linkedMapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "brand" to brand,
        "categoryId" to categoryId,
        "categoryName" to categoryName,
        "images" to images,
        "mrp" to mrp,
        "sellingPrice" to sellingPrice,
        "discountPercentage" to discountPercentage,
        "unit" to unit,
        "weight" to weight,
        "size" to size,
        "attributes" to attributes,
        "available" to available,
        "stockQuantity" to stockQuantity,
        "deliveryType" to deliveryType,
    )

    companion object {
        fun partnerImageUrl(productId: Int, catalogImageBaseUrl: String = AppConfig.catalogImageBaseUrl): String =
            "$catalogImageBaseUrl/api/v1/catalog/products/$productId/image"

        fun fromJson(j: JsonMap, catalogImageBaseUrl: String = AppConfig.catalogImageBaseUrl): Product {
            val imageFromServer = (j["imageAsset"] ?: j["imageUrl"]).jsonString().orEmpty()

            data class Entry(val sortOrder: Int, val index: Int, val url: String)
            val entries = mutableListOf<Entry>()
            j["images"].asJsonList()?.forEachIndexed { index, entry ->
                val map = entry.asJsonMap()
                if (map != null) {
                    val candidate = map["url"] ?: map["imageUrl"] ?: map["path"] ?: map["src"]
                    val url = candidate.jsonString()?.trim().orEmpty()
                    if (url.isNotEmpty()) {
                        val rawOrder = map["sortOrder"]
                        val sortOrder = if (rawOrder is Number) {
                            rawOrder.toInt()
                        } else {
                            (rawOrder ?: index).jsonString()?.trim()?.toIntOrNull() ?: index
                        }
                        entries += Entry(sortOrder, index, url)
                    }
                } else if (entry != null && entry.jsonString()!!.trim().isNotEmpty()) {
                    entries += Entry(index, index, entry.jsonString()!!.trim())
                }
            }
            val imgs = entries
                .sortedWith(compareBy<Entry> { it.sortOrder }.thenBy { it.index })
                .map { it.url }
                .distinct()
                .toMutableList()

            if (imgs.isEmpty() && imageFromServer.isNotEmpty()) imgs += imageFromServer

            // Product images are uploaded by the VJoyKart Partner/Admin backend. Older records may
            // have an empty imageUrl in the customer API while the partner backend already has the
            // uploaded BLOB, so fall back to its public primary-image endpoint.
            val productId = (j["productId"] ?: j["id"]).jsonInt()
            if (imgs.isEmpty() && productId > 0) imgs += partnerImageUrl(productId, catalogImageBaseUrl)

            val price = (j["price"] ?: j["mrp"]).jsonDouble()
            val discountedPrice = (j["discountedPrice"] ?: j["sellingPrice"])?.jsonDouble() ?: price
            val availability = j["availability"].jsonString()?.uppercase()
            val status = j["status"].jsonString()?.uppercase()
            val hasStock = j.containsKey("stock") || j.containsKey("stockQuantity")
            val stock = (j["stock"] ?: j["stockQuantity"]).jsonInt()
            val attributes = j["attributes"].asJsonMap()
                ?.mapValues { (_, v) -> v.jsonString() ?: "null" }
                ?: emptyMap()

            return Product(
                id = productId,
                name = j["name"].jsonString().orEmpty(),
                description = j["description"].jsonString().orEmpty(),
                brand = j["brand"].jsonString().orEmpty(),
                categoryId = j["categoryId"].jsonString().orEmpty(),
                categoryName = (j["categoryName"] ?: j["category"]).jsonString().orEmpty(),
                images = imgs,
                mrp = price,
                sellingPrice = discountedPrice,
                discountPercentage = (j["discountPercent"] ?: j["discountPercentage"]).jsonDouble(),
                unit = (j["unit"] ?: "1 unit").jsonString()!!,
                weight = j["weight"].jsonString().orEmpty(),
                size = j["size"].jsonString().orEmpty(),
                attributes = attributes,
                available = status != "INACTIVE" &&
                    availability != "UNAVAILABLE" &&
                    availability != "OUT_OF_STOCK" &&
                    (j["available"] ?: true) != false &&
                    (!hasStock || stock > 0),
                stockQuantity = stock,
                deliveryType = (j["deliveryType"] ?: "SMALL").jsonString()!!,
            )
        }
    }
}

/** Port of lib/models/cart_item.dart. */
data class CartItem(val product: Product, val quantity: Int = 1) {
    val total: Double get() = product.sellingPrice * quantity

    fun toJson(): JsonMap = linkedMapOf("product" to product.toJson(), "quantity" to quantity)

    companion object {
        fun fromJson(json: JsonMap): CartItem = CartItem(
            product = Product.fromJson(json["product"].asJsonMap() ?: emptyMap()),
            quantity = (json["quantity"] ?: 1).jsonInt(),
        )
    }
}

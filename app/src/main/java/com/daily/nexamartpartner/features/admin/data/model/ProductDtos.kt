package com.daily.nexamartpartner.features.admin.data.model

import com.squareup.moshi.Json

data class ProductsPageDto(
    @field:Json(name = "content") val content: List<ProductSummaryDto>?,
    @field:Json(name = "number") val number: Int?,
    @field:Json(name = "size") val size: Int?,
    @field:Json(name = "totalPages") val totalPages: Int?,
    @field:Json(name = "totalElements") val totalElements: Long?,
    @field:Json(name = "last") val last: Boolean?
)

data class ProductSummaryDto(
    @field:Json(name = "productId") val productId: String?,
    @field:Json(name = "name") val name: String?,
    @field:Json(name = "categoryId") val categoryId: String?,
    @field:Json(name = "categoryName") val categoryName: String?,
    @field:Json(name = "price") val price: String?,
    @field:Json(name = "discountedPrice") val discountedPrice: String?,
    @field:Json(name = "currencyCode") val currencyCode: String?,
    @field:Json(name = "stock") val stock: Int?,
    @field:Json(name = "unit") val unit: String?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "availability") val availability: String?,
    @field:Json(name = "imageUrl") val imageUrl: String?
)

data class ProductDetailsDto(
    @field:Json(name = "productId") val productId: String?,
    @field:Json(name = "name") val name: String?,
    @field:Json(name = "description") val description: String?,
    @field:Json(name = "categoryId") val categoryId: String?,
    @field:Json(name = "categoryName") val categoryName: String?,
    @field:Json(name = "price") val price: String?,
    @field:Json(name = "discountedPrice") val discountedPrice: String?,
    @field:Json(name = "discountPercent") val discountPercent: String?,
    @field:Json(name = "currencyCode") val currencyCode: String?,
    @field:Json(name = "stock") val stock: Int?,
    @field:Json(name = "sku") val sku: String?,
    @field:Json(name = "unit") val unit: String?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "availability") val availability: String?,
    @field:Json(name = "imageUrl") val imageUrl: String?,
    @field:Json(name = "createdAt") val createdAt: String?,
    @field:Json(name = "updatedAt") val updatedAt: String?,
    @field:Json(name = "allowedActions") val allowedActions: List<String>?,
    @field:Json(name = "discountAmount") val discountAmount: String? = null
)

data class CategoryOptionDto(
    @field:Json(name = "categoryId") val categoryId: String?,
    @field:Json(name = "name") val name: String?
)

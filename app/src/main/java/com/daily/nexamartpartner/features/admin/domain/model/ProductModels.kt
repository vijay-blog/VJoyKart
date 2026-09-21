package com.daily.nexamartpartner.features.admin.domain.model

import java.math.BigDecimal

enum class ProductStatus(val backendValue: String) {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    DRAFT("DRAFT"),
    OUT_OF_STOCK("OUT_OF_STOCK"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(raw: String?): ProductStatus {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.backendValue.equals(raw.trim(), ignoreCase = true) }
                ?: UNKNOWN
        }
    }
}

enum class ProductAvailability(val backendValue: String) {
    IN_STOCK("IN_STOCK"),
    LOW_STOCK("LOW_STOCK"),
    OUT_OF_STOCK("OUT_OF_STOCK"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(raw: String?): ProductAvailability {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.backendValue.equals(raw.trim(), ignoreCase = true) }
                ?: UNKNOWN
        }
    }
}

/**
 * Admin-facing product mutation actions. The backend alone decides which of these are
 * currently valid for a given product; the app never infers this locally.
 */
enum class ProductAdminAction(val backendValue: String) {
    ACTIVATE("ACTIVATE"),
    DEACTIVATE("DEACTIVATE"),
    DELETE("DELETE"),
    EDIT("EDIT")
}

enum class ProductSort(val backendValue: String) {
    NEWEST("NEWEST"),
    NAME_A_Z("NAME_A_Z"),
    PRICE_LOW_TO_HIGH("PRICE_LOW_TO_HIGH"),
    PRICE_HIGH_TO_LOW("PRICE_HIGH_TO_LOW")
}

data class CategoryOption(
    val categoryId: String,
    val name: String
)

data class ProductSummary(
    val productId: String,
    val name: String,
    val categoryId: String?,
    val categoryName: String?,
    /** Exact decimal amount as returned by the backend; never computed client-side. */
    val price: BigDecimal?,
    /** Backend-computed final/discounted price; the app never derives this itself. */
    val discountedPrice: BigDecimal?,
    val currencyCode: String?,
    val stock: Int?,
    val unit: String?,
    val status: ProductStatus,
    val availability: ProductAvailability,
    val imageUrl: String?
)

data class PagedProducts(
    val products: List<ProductSummary>,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalElements: Long,
    val hasNextPage: Boolean
)

data class ProductFilters(
    val status: ProductStatus? = null,
    val categoryId: String? = null
)

data class ProductsQuery(
    val page: Int,
    val pageSize: Int,
    val searchText: String?,
    val filters: ProductFilters,
    val sort: ProductSort
)

data class ProductDetails(
    val productId: String,
    val name: String,
    val description: String?,
    val categoryId: String?,
    val categoryName: String?,
    val price: BigDecimal?,
    val discountedPrice: BigDecimal?,
    val discountPercent: BigDecimal?,
    val currencyCode: String?,
    val stock: Int?,
    val sku: String?,
    val unit: String?,
    val status: ProductStatus,
    val availability: ProductAvailability,
    val imageUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    /** Only actions the backend currently allows for this product; never inferred locally. */
    val allowedActions: List<ProductAdminAction>,
    val discountAmount: BigDecimal? = null
)

/**
 * Create/update draft. Values are kept as raw strings (not parsed BigDecimal/Int) so the
 * exact user-entered text is preserved for the backend request once the contract is confirmed.
 * All fields are optional because the backend's exact required-field set is unconfirmed.
 */
data class ProductDraft(
    val name: String,
    val description: String? = null,
    val categoryId: String? = null,
    val price: String? = null,
    val discountPercent: String? = null,
    val stock: String? = null,
    val sku: String? = null,
    val unit: String? = null,
    val imageUrl: String? = null
)

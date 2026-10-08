package com.daily.nexamartpartner.features.admin.data.contract

import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery

interface ProductManagementContract {
    val listProductsPath: String?
    val productDetailsPathTemplate: String?
    val categoryOptionsPath: String?
    val createProductPath: String?
    val updateProductPathTemplate: String?
    val productImagePathTemplate: String?
    val productActionPathTemplate: String?

    fun buildProductListQuery(query: ProductsQuery): Map<String, String>?
    fun buildCreateProductBody(draft: ProductDraft): Map<String, String>?
    fun buildUpdateProductBody(draft: ProductDraft): Map<String, String>?
    fun buildProductActionBody(action: ProductAdminAction): Map<String, String>?
    fun resolvePath(template: String?, productId: String): String?
}

class BackendProductManagementContract : ProductManagementContract {
    override val listProductsPath = "admin/catalog/products"
    override val productDetailsPathTemplate = "admin/catalog/products/{id}"
    override val categoryOptionsPath = "admin/catalog/categories/options"
    override val createProductPath = "admin/catalog/products"
    override val updateProductPathTemplate = "admin/catalog/products/{id}"
    override val productImagePathTemplate = "admin/catalog/products/{id}/image"
    override val productActionPathTemplate = "admin/catalog/products/{id}/actions"

    override fun buildProductListQuery(query: ProductsQuery): Map<String, String> = buildMap {
        put("page", query.page.toString())
        put("pageSize", query.pageSize.toString())
        query.searchText?.takeIf { it.isNotBlank() }?.let { put("search", it) }
        query.filters.categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryId", it) }
        when (query.filters.status) {
            ProductStatus.ACTIVE -> put("active", "true")
            ProductStatus.INACTIVE -> put("active", "false")
            else -> Unit
        }
        put("sort", query.sort.backendValue)
    }

    override fun buildCreateProductBody(draft: ProductDraft) = draft.toBody()
    override fun buildUpdateProductBody(draft: ProductDraft) = draft.toBody()
    override fun buildProductActionBody(action: ProductAdminAction) =
        mapOf("action" to action.backendValue)

    override fun resolvePath(template: String?, productId: String): String? =
        template?.replace("{id}", productId)

    private fun ProductDraft.toBody(): Map<String, String> = buildMap {
        put("name", name)
        description?.let { put("description", it) }
        categoryId?.let { put("categoryId", it) }
        price?.let { put("price", it) }
        discountPercent?.let { put("discountPercent", it) }
        stock?.let { put("stock", it) }
        sku?.let { put("sku", it) }
        unit?.let { put("unit", it) }
        imageUrl?.let { put("imageUrl", it) }
    }
}

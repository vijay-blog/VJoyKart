package com.daily.nexamartpartner.features.admin.data.repository

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.model.CategoryOptionDto
import com.daily.nexamartpartner.features.admin.data.model.ProductDetailsDto
import com.daily.nexamartpartner.features.admin.data.model.ProductSummaryDto
import com.daily.nexamartpartner.features.admin.data.model.ProductsPageDto
import com.daily.nexamartpartner.features.admin.data.source.ProductManagementRemoteDataSource
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductAvailability
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductFilters
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import com.daily.nexamartpartner.features.admin.domain.model.ProductSort
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
import com.daily.nexamartpartner.features.admin.domain.repository.ProductManagementRepository
import java.math.BigDecimal

class ProductManagementRepositoryImpl(
    private val remoteDataSource: ProductManagementRemoteDataSource
) : ProductManagementRepository {

    override suspend fun getProducts(query: ProductsQuery): AppResult<PagedProducts> {
        return when (val result = remoteDataSource.getProducts(query)) {
            is AppResult.Success -> mapProductPage(result.data, query.page, query.pageSize)
            is AppResult.Failure -> result
        }
    }

    override suspend fun getProductDetails(productId: String): AppResult<ProductDetails> {
        return when (val result = remoteDataSource.getProductDetails(productId)) {
            is AppResult.Success -> mapProductDetails(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun getCategoryOptions(): AppResult<List<CategoryOption>> {
        return when (val result = remoteDataSource.getCategoryOptions()) {
            is AppResult.Success -> mapCategoryOptions(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetails> {
        return when (val result = remoteDataSource.createProduct(draft)) {
            is AppResult.Success -> mapProductDetails(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetails> {
        return when (val result = remoteDataSource.updateProduct(productId, draft)) {
            is AppResult.Success -> mapProductDetails(result.data)
            is AppResult.Failure -> result
        }
    }



    override suspend fun uploadProductImage(
        productId: String,
        image: ProductImageUpload
    ): AppResult<ProductDetails> {
        return when (val result = remoteDataSource.uploadProductImage(productId, image)) {
            is AppResult.Success -> mapProductDetails(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun performProductAction(
        productId: String,
        action: ProductAdminAction
    ): AppResult<Unit> {
        return remoteDataSource.performProductAction(productId, action)
    }

    private fun mapProductPage(
        dto: ProductsPageDto,
        fallbackPage: Int,
        fallbackPageSize: Int
    ): AppResult<PagedProducts> {
        val mappedProducts = dto.content?.map { summary ->
            mapProductSummary(summary)
                ?: return invalid("Invalid product list response from server.")
        }.orEmpty()

        val totalPages = dto.totalPages ?: 1
        val currentPage = dto.number ?: fallbackPage
        val hasNext = dto.last?.not() ?: (currentPage + 1 < totalPages)

        return AppResult.Success(
            PagedProducts(
                products = mappedProducts,
                page = currentPage,
                pageSize = dto.size ?: fallbackPageSize,
                totalPages = totalPages,
                totalElements = dto.totalElements ?: mappedProducts.size.toLong(),
                hasNextPage = hasNext
            )
        )
    }

    private fun mapProductSummary(dto: ProductSummaryDto): ProductSummary? {
        val productId = dto.productId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val status = ProductStatus.fromRaw(dto.status)
        val price = parseAmount(dto.price)
        val discountedPrice = parseAmount(dto.discountedPrice)
        if ((!dto.price.isNullOrBlank() && price == null) ||
            (!dto.discountedPrice.isNullOrBlank() && discountedPrice == null)
        ) return null

        return ProductSummary(
            productId = productId,
            name = dto.name?.trim().orEmpty(),
            categoryId = dto.categoryId?.trim(),
            categoryName = dto.categoryName?.trim(),
            price = price,
            discountedPrice = discountedPrice,
            currencyCode = dto.currencyCode?.trim(),
            stock = dto.stock,
            unit = dto.unit?.trim(),
            status = status,
            availability = ProductAvailability.fromRaw(dto.availability),
            imageUrl = dto.imageUrl?.trim()
        )
    }

    private fun mapProductDetails(dto: ProductDetailsDto): AppResult<ProductDetails> {
        val productId = dto.productId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return invalid("Invalid product details response from server.")
        val status = ProductStatus.fromRaw(dto.status)
        val price = parseAmount(dto.price)
        val discountedPrice = parseAmount(dto.discountedPrice)
        val discountPercent = parseAmount(dto.discountPercent)
        val discountAmount = parseAmount(dto.discountAmount)
        if ((!dto.price.isNullOrBlank() && price == null) ||
            (!dto.discountedPrice.isNullOrBlank() && discountedPrice == null) ||
            (!dto.discountPercent.isNullOrBlank() && discountPercent == null) ||
            (!dto.discountAmount.isNullOrBlank() && discountAmount == null)
        ) {
            return invalid("Invalid product details response from server.")
        }

        val allowedActions = dto.allowedActions?.mapNotNull { raw ->
            ProductAdminAction.entries.firstOrNull { it.backendValue.equals(raw.trim(), ignoreCase = true) }
        }.orEmpty()

        return AppResult.Success(
            ProductDetails(
                productId = productId,
                name = dto.name?.trim().orEmpty(),
                description = dto.description?.trim(),
                categoryId = dto.categoryId?.trim(),
                categoryName = dto.categoryName?.trim(),
                price = price,
                discountedPrice = discountedPrice,
                discountPercent = discountPercent,
                currencyCode = dto.currencyCode?.trim(),
                stock = dto.stock,
                sku = dto.sku?.trim(),
                unit = dto.unit?.trim(),
                status = status,
                availability = ProductAvailability.fromRaw(dto.availability),
                imageUrl = dto.imageUrl?.trim(),
                createdAt = dto.createdAt?.trim(),
                updatedAt = dto.updatedAt?.trim(),
                allowedActions = allowedActions,
                discountAmount = discountAmount
            )
        )
    }

    private fun mapCategoryOptions(dtos: List<CategoryOptionDto>?): AppResult<List<CategoryOption>> {
        val options = dtos?.mapNotNull { dto ->
            val id = dto.categoryId?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            CategoryOption(categoryId = id, name = dto.name?.trim().orEmpty())
        }.orEmpty()
        return AppResult.Success(options)
    }

    private fun parseAmount(raw: String?): BigDecimal? {
        return raw?.trim()?.takeUnless { it.isEmpty() }?.toBigDecimalOrNull()
    }

    private fun <T> invalid(message: String): AppResult<T> {
        return AppResult.Failure(AppFailure(message = message, type = FailureType.SERVER))
    }
}

fun defaultProductsQuery(): ProductsQuery {
    return ProductsQuery(
        page = 0,
        pageSize = 20,
        searchText = null,
        filters = ProductFilters(),
        sort = ProductSort.NEWEST
    )
}

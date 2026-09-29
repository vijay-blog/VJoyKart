package com.daily.nexamartpartner.features.admin

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.model.CategoryOptionDto
import com.daily.nexamartpartner.features.admin.data.model.ProductDetailsDto
import com.daily.nexamartpartner.features.admin.data.model.ProductSummaryDto
import com.daily.nexamartpartner.features.admin.data.model.ProductsPageDto
import com.daily.nexamartpartner.features.admin.data.repository.ProductManagementRepositoryImpl
import com.daily.nexamartpartner.features.admin.data.repository.defaultProductsQuery
import com.daily.nexamartpartner.features.admin.data.source.ProductManagementRemoteDataSource
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductManagementRepositoryImplTest {
    @Test
    fun `products success maps content`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                listResult = AppResult.Success(
                    ProductsPageDto(
                        content = listOf(
                            ProductSummaryDto(
                                productId = "P1001",
                                name = "Rice 5kg",
                                categoryId = "C1",
                                categoryName = "Groceries",
                                price = "450.00",
                                discountedPrice = "400.00",
                                currencyCode = "INR",
                                stock = 10,
                                unit = "bag",
                                status = "ACTIVE",
                                availability = "IN_STOCK",
                                imageUrl = null
                            )
                        ),
                        number = 0,
                        size = 20,
                        totalPages = 1,
                        totalElements = 1,
                        last = true
                    )
                )
            )
        )

        val result = repository.getProducts(defaultProductsQuery())
        assertTrue(result is AppResult.Success)
        val page = (result as AppResult.Success).data
        assertEquals(1, page.products.size)
        assertEquals("P1001", page.products.first().productId)
        assertEquals(ProductStatus.ACTIVE, page.products.first().status)
    }

    @Test
    fun `products empty maps empty list`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                listResult = AppResult.Success(
                    ProductsPageDto(emptyList(), 0, 20, 0, 0, true)
                )
            )
        )
        val result = repository.getProducts(defaultProductsQuery())
        assertTrue(result is AppResult.Success)
        assertEquals(0, (result as AppResult.Success).data.products.size)
    }

    @Test
    fun `network failure propagates`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                listResult = AppResult.Failure(AppFailure("Network", type = FailureType.NETWORK))
            )
        )
        val result = repository.getProducts(defaultProductsQuery())
        assertTrue(result is AppResult.Failure)
        assertEquals(FailureType.NETWORK, (result as AppResult.Failure).error.type)
    }

    @Test
    fun `contract missing failure propagates`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                listResult = AppResult.Failure(
                    AppFailure("Product list API contract is not confirmed yet.", type = FailureType.CONTRACT_MISSING)
                )
            )
        )
        val result = repository.getProducts(defaultProductsQuery())
        assertTrue(result is AppResult.Failure)
        assertEquals(FailureType.CONTRACT_MISSING, (result as AppResult.Failure).error.type)
    }

    @Test
    fun `unauthorized failure propagates`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                detailsResult = AppResult.Failure(AppFailure("Unauthorized", 401, FailureType.UNAUTHORIZED))
            )
        )
        val result = repository.getProductDetails("P1001")
        assertTrue(result is AppResult.Failure)
        assertEquals(FailureType.UNAUTHORIZED, (result as AppResult.Failure).error.type)
    }

    @Test
    fun `not found failure propagates`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                detailsResult = AppResult.Failure(AppFailure("Product not found", 404, FailureType.UNKNOWN))
            )
        )
        val result = repository.getProductDetails("P404")
        assertTrue(result is AppResult.Failure)
        assertEquals(404, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `forbidden failure propagates`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                detailsResult = AppResult.Failure(AppFailure("Forbidden", 403, FailureType.FORBIDDEN))
            )
        )
        val result = repository.getProductDetails("P1001")
        assertTrue(result is AppResult.Failure)
        assertEquals(FailureType.FORBIDDEN, (result as AppResult.Failure).error.type)
    }

    @Test
    fun `conflict failure propagates on action`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                actionResult = AppResult.Failure(AppFailure("Conflict", 409, FailureType.UNKNOWN))
            )
        )
        val result = repository.performProductAction("P1001", ProductAdminAction.DEACTIVATE)
        assertTrue(result is AppResult.Failure)
        assertEquals(409, (result as AppResult.Failure).error.code)
    }

    @Test
    fun `category options map and tolerate missing ids`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                categoryResult = AppResult.Success(
                    listOf(
                        CategoryOptionDto("C1", "Groceries"),
                        CategoryOptionDto(null, "Invalid")
                    )
                )
            )
        )
        val result = repository.getCategoryOptions()
        assertTrue(result is AppResult.Success)
        val options = (result as AppResult.Success).data
        assertEquals(1, options.size)
        assertEquals("C1", options.first().categoryId)
    }

    @Test
    fun `details maps allowed actions`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                detailsResult = AppResult.Success(
                    ProductDetailsDto(
                        productId = "P1001",
                        name = "Rice 5kg",
                        description = "Premium rice",
                        categoryId = "C1",
                        categoryName = "Groceries",
                        price = "450.00",
                        discountedPrice = "400.00",
                        discountPercent = "11.11",
                        currencyCode = "INR",
                        stock = 10,
                        sku = "SKU-1",
                        unit = "bag",
                        status = "ACTIVE",
                        availability = "IN_STOCK",
                        imageUrl = null,
                        createdAt = "2026-09-01T10:00:00Z",
                        updatedAt = "2026-09-08T10:00:00Z",
                        allowedActions = listOf("DEACTIVATE", "DELETE", "EDIT", "unsupported")
                    )
                )
            )
        )
        val result = repository.getProductDetails("P1001")
        assertTrue(result is AppResult.Success)
        val details = (result as AppResult.Success).data
        assertEquals(3, details.allowedActions.size)
        assertTrue(details.allowedActions.contains(ProductAdminAction.EDIT))
    }

    @Test
    fun `create and update map backend product responses`() = runTest {
        val dto = detailsDto()
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                createResult = AppResult.Success(dto),
                updateResult = AppResult.Success(dto)
            )
        )
        val draft = ProductDraft(name = "Rice 5kg", categoryId = "C1", price = "450.00")

        val created = repository.createProduct(draft)
        val updated = repository.updateProduct("P1001", draft)

        assertTrue(created is AppResult.Success)
        assertTrue(updated is AppResult.Success)
        assertEquals("P1001", (created as AppResult.Success).data.productId)
    }

    @Test
    fun `malformed monetary response is rejected`() = runTest {
        val repository = ProductManagementRepositoryImpl(
            remoteDataSource = FakeRemote(
                detailsResult = AppResult.Success(detailsDto(price = "not-a-number"))
            )
        )
        val result = repository.getProductDetails("P1001")
        assertTrue(result is AppResult.Failure)
        assertEquals(FailureType.SERVER, (result as AppResult.Failure).error.type)
    }

    private fun detailsDto(price: String = "450.00") = ProductDetailsDto(
        productId = "P1001",
        name = "Rice 5kg",
        description = "Premium rice",
        categoryId = "C1",
        categoryName = "Groceries",
        price = price,
        discountedPrice = "400.00",
        discountPercent = "11.11",
        currencyCode = "INR",
        stock = 10,
        sku = "SKU-1",
        unit = "bag",
        status = "ACTIVE",
        availability = "IN_STOCK",
        imageUrl = null,
        createdAt = "2026-09-01T10:00:00Z",
        updatedAt = "2026-09-08T10:00:00Z",
        allowedActions = listOf("DEACTIVATE", "EDIT")
    )

    private class FakeRemote(
        private val listResult: AppResult<ProductsPageDto> = AppResult.Success(
            ProductsPageDto(emptyList(), 0, 20, 0, 0, true)
        ),
        private val detailsResult: AppResult<ProductDetailsDto> = AppResult.Failure(
            AppFailure("Not used", type = FailureType.UNKNOWN)
        ),
        private val categoryResult: AppResult<List<CategoryOptionDto>> = AppResult.Success(emptyList()),
        private val createResult: AppResult<ProductDetailsDto> = AppResult.Failure(
            AppFailure("Not used", type = FailureType.UNKNOWN)
        ),
        private val updateResult: AppResult<ProductDetailsDto> = AppResult.Failure(
            AppFailure("Not used", type = FailureType.UNKNOWN)
        ),
        private val actionResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ProductManagementRemoteDataSource {
        override suspend fun getProducts(query: com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery) = listResult
        override suspend fun getProductDetails(productId: String) = detailsResult
        override suspend fun getCategoryOptions() = categoryResult
        override suspend fun createProduct(draft: ProductDraft) = createResult
        override suspend fun updateProduct(productId: String, draft: ProductDraft) = updateResult
        override suspend fun performProductAction(productId: String, action: ProductAdminAction) = actionResult
    }
}

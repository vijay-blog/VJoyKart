package com.daily.nexamartpartner.features.admin

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductAvailability
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import com.daily.nexamartpartner.features.admin.domain.model.ProductFilters
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
import com.daily.nexamartpartner.features.admin.domain.repository.ProductManagementRepository
import com.daily.nexamartpartner.features.admin.domain.usecase.CreateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductCategoryOptionsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductDetailsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.PerformProductAdminActionUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UpdateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UploadProductImageUseCase
import com.daily.nexamartpartner.features.admin.presentation.state.ProductDetailsUiState
import com.daily.nexamartpartner.features.admin.presentation.state.ProductFormUiState
import com.daily.nexamartpartner.features.admin.presentation.state.ProductListUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductDetailsViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductFormViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductListViewModel
import com.daily.nexamartpartner.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ---------- List ViewModel ----------

    @Test
    fun `list initial loading then success`() = runTest {
        val repo = FakeRepository(listResults = mutableListOf(AppResult.Success(page(listOf(product())))))
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        assertTrue(vm.uiState.value.content is ProductListUiState.Content.Loading)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.content is ProductListUiState.Content.Success)
    }

    @Test
    fun `list empty and error and unavailable states`() = runTest {
        val emptyVm = ProductListViewModel(
            GetProductsUseCase(FakeRepository(listResults = mutableListOf(AppResult.Success(page(emptyList()))))),
            GetProductCategoryOptionsUseCase(FakeRepository())
        )
        advanceUntilIdle()
        assertTrue(emptyVm.uiState.value.content is ProductListUiState.Content.Empty)

        val errorVm = ProductListViewModel(
            GetProductsUseCase(
                FakeRepository(listResults = mutableListOf(AppResult.Failure(AppFailure("Server", 500, FailureType.SERVER))))
            ),
            GetProductCategoryOptionsUseCase(FakeRepository())
        )
        advanceUntilIdle()
        assertTrue(errorVm.uiState.value.content is ProductListUiState.Content.Error)

        val unavailableVm = ProductListViewModel(
            GetProductsUseCase(
                FakeRepository(
                    listResults = mutableListOf(
                        AppResult.Failure(AppFailure("Contract missing", type = FailureType.CONTRACT_MISSING))
                    )
                )
            ),
            GetProductCategoryOptionsUseCase(FakeRepository())
        )
        advanceUntilIdle()
        assertTrue(unavailableVm.uiState.value.content is ProductListUiState.Content.Unavailable)
    }

    @Test
    fun `search debounce sends a single reload after typing`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")))),
                AppResult.Success(page(listOf(product("P2"))))
            )
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        advanceUntilIdle()
        vm.onSearchQueryChanged("R")
        vm.onSearchQueryChanged("Ri")
        vm.onSearchQueryChanged("Rice")
        advanceUntilIdle()
        // Only the initial load + one debounced reload should have reached the repository.
        assertEquals(2, repo.listQueries.size)
        assertEquals("Rice", repo.listQueries.last().searchText)
    }

    @Test
    fun `filter and sort trigger reload`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")))),
                AppResult.Success(page(listOf(product("P2")))),
                AppResult.Success(page(listOf(product("P3"))))
            )
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        advanceUntilIdle()
        vm.applyFilters(ProductFilters(status = ProductStatus.ACTIVE))
        advanceUntilIdle()
        vm.applySort(com.daily.nexamartpartner.features.admin.domain.model.ProductSort.PRICE_LOW_TO_HIGH)
        advanceUntilIdle()
        assertEquals(3, repo.listQueries.size)
        assertEquals(ProductStatus.ACTIVE, repo.listQueries[1].filters.status)
    }

    @Test
    fun `refresh keeps success content and requests server again`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")))),
                AppResult.Success(page(listOf(product("P3"))))
            )
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, repo.listQueries.size)
        assertTrue(vm.uiState.value.content is ProductListUiState.Content.Success)
    }

    @Test
    fun `pagination loads next page and appends results`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")), pageNumber = 0, hasNext = true)),
                AppResult.Success(page(listOf(product("P2")), pageNumber = 1, hasNext = false))
            )
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        advanceUntilIdle()
        vm.loadNextPage()
        advanceUntilIdle()
        val state = vm.uiState.value.content as ProductListUiState.Content.Success
        assertEquals(2, state.products.size)
    }

    @Test
    fun `new filters replace an in flight list request`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")))),
                AppResult.Success(page(listOf(product("P2"))))
            ),
            listDelayMs = 100L
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        runCurrent()
        vm.applyFilters(ProductFilters(status = ProductStatus.ACTIVE))
        advanceUntilIdle()
        assertEquals(ProductStatus.ACTIVE, repo.listQueries.last().filters.status)
        val state = vm.uiState.value.content as ProductListUiState.Content.Success
        assertEquals("P2", state.products.single().productId)
    }

    @Test
    fun `pagination failure keeps previously loaded products visible`() = runTest {
        val repo = FakeRepository(
            listResults = mutableListOf(
                AppResult.Success(page(listOf(product("P1")), hasNext = true)),
                AppResult.Failure(AppFailure("Network error", type = FailureType.NETWORK))
            )
        )
        val vm = ProductListViewModel(GetProductsUseCase(repo), GetProductCategoryOptionsUseCase(repo))
        advanceUntilIdle()
        vm.loadNextPage()
        advanceUntilIdle()
        val state = vm.uiState.value.content as ProductListUiState.Content.Success
        assertEquals("P1", state.products.single().productId)
        assertEquals(false, vm.uiState.value.isLoadingMore)
    }

    // ---------- Details ViewModel ----------

    @Test
    fun `details load and every backend allowed action executes`() = runTest {
        ProductAdminAction.entries.forEach { action ->
            val repo = FakeRepository(
                detailsResults = mutableListOf(
                    AppResult.Success(details(listOf(action))),
                    AppResult.Success(details(emptyList()))
                )
            )
            val vm = ProductDetailsViewModel(
                "P1",
                GetProductDetailsUseCase(repo),
                PerformProductAdminActionUseCase(repo)
            )
            advanceUntilIdle()
            vm.performAction(action)
            advanceUntilIdle()
            assertEquals(1, repo.actions.size)
            assertTrue(vm.uiState.value.content is ProductDetailsUiState.Content.Success)
        }
    }

    @Test
    fun `details ignores duplicate action while a request is in flight`() = runTest {
        val repo = FakeRepository(
            detailsResults = mutableListOf(
                AppResult.Success(details(listOf(ProductAdminAction.DEACTIVATE))),
                AppResult.Success(details(emptyList()))
            ),
            actionDelayMs = 100L
        )
        val vm = ProductDetailsViewModel("P1", GetProductDetailsUseCase(repo), PerformProductAdminActionUseCase(repo))
        advanceUntilIdle()
        vm.performAction(ProductAdminAction.DEACTIVATE)
        runCurrent() // let the coroutine start and reach the in-flight delay
        vm.performAction(ProductAdminAction.DEACTIVATE) // must be ignored: actionInProgress already set
        advanceUntilIdle()
        assertEquals(1, repo.actions.size)
    }

    @Test
    fun `details conflict triggers refresh`() = runTest {
        val repo = FakeRepository(
            detailsResults = mutableListOf(
                AppResult.Success(details(listOf(ProductAdminAction.DEACTIVATE))),
                AppResult.Success(details(emptyList()))
            ),
            actionResult = AppResult.Failure(AppFailure("Conflict", 409, FailureType.UNKNOWN))
        )
        val vm = ProductDetailsViewModel("P1", GetProductDetailsUseCase(repo), PerformProductAdminActionUseCase(repo))
        advanceUntilIdle()
        vm.performAction(ProductAdminAction.DEACTIVATE)
        advanceUntilIdle()
        assertEquals(2, repo.detailsCallCount)
    }

    @Test
    fun `details unauthorized failure surfaces error content`() = runTest {
        val repo = FakeRepository(
            detailsResults = mutableListOf(AppResult.Failure(AppFailure("Unauthorized", 401, FailureType.UNAUTHORIZED)))
        )
        val vm = ProductDetailsViewModel("P1", GetProductDetailsUseCase(repo), PerformProductAdminActionUseCase(repo))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.content is ProductDetailsUiState.Content.Error)
    }

    // ---------- Form ViewModel ----------

    @Test
    fun `create form validates required fields before saving`() = runTest {
        val repo = FakeRepository()
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.CREATE,
            null,
            null,
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        val errors = vm.uiState.value.fieldErrors
        assertEquals("Product name is required.", errors.name)
        assertEquals("Category is required.", errors.category)
        assertEquals("Price is required.", errors.price)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `create form validates discount and stock ranges`() = runTest {
        val repo = FakeRepository(categoryResult = AppResult.Success(listOf(CategoryOption("C1", "Groceries"))))
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.CREATE,
            null,
            null,
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        vm.onNameChanged("Rice")
        vm.onCategorySelected("C1")
        vm.onPriceChanged("100.00")
        vm.onDiscountChanged("150")
        vm.onStockChanged("-5")
        vm.save()
        advanceUntilIdle()
        val errors = vm.uiState.value.fieldErrors
        assertEquals("Discount must be between 0 and 100.", errors.discount)
        assertEquals("Stock cannot be negative.", errors.stock)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `create form saves successfully`() = runTest {
        val repo = FakeRepository(
            categoryResult = AppResult.Success(listOf(CategoryOption("C1", "Groceries"))),
            createResult = AppResult.Success(details(emptyList()))
        )
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.CREATE,
            null,
            null,
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        vm.onNameChanged("Rice")
        vm.onCategorySelected("C1")
        vm.onPriceChanged("100.00")
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.createCalls)
        assertTrue(vm.uiState.value.isDirty.not())
    }

    @Test
    fun `create form ignores duplicate save while a request is in flight`() = runTest {
        val repo = FakeRepository(
            categoryResult = AppResult.Success(listOf(CategoryOption("C1", "Groceries"))),
            createResult = AppResult.Success(details(emptyList())),
            createDelayMs = 100L
        )
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.CREATE,
            null,
            null,
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        vm.onNameChanged("Rice")
        vm.onCategorySelected("C1")
        vm.onPriceChanged("100.00")
        vm.save()
        runCurrent() // let the coroutine start and reach the in-flight delay
        vm.save() // must be ignored: isSaving already true
        advanceUntilIdle()
        assertEquals(1, repo.createCalls)
    }

    @Test
    fun `edit form loads existing product and resets dirty flag`() = runTest {
        val repo = FakeRepository(
            detailsResults = mutableListOf(AppResult.Success(details(listOf(ProductAdminAction.EDIT)))),
            categoryResult = AppResult.Success(listOf(CategoryOption("C1", "Groceries")))
        )
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.EDIT,
            "P1",
            GetProductDetailsUseCase(repo),
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        assertEquals("Rice 5kg", vm.uiState.value.name)
        assertEquals(false, vm.uiState.value.isDirty)
        assertTrue(vm.uiState.value.content == ProductFormUiState.Content.Editing)
    }

    @Test
    fun `edit form conflict preserves unsaved changes`() = runTest {
        val repo = FakeRepository(
            detailsResults = mutableListOf(AppResult.Success(details(listOf(ProductAdminAction.EDIT)))),
            categoryResult = AppResult.Success(listOf(CategoryOption("C1", "Groceries"))),
            updateResult = AppResult.Failure(AppFailure("Conflict", 409, FailureType.UNKNOWN))
        )
        val vm = ProductFormViewModel(
            ProductFormUiState.Mode.EDIT,
            "P1",
            GetProductDetailsUseCase(repo),
            GetProductCategoryOptionsUseCase(repo),
            CreateProductUseCase(repo),
            UpdateProductUseCase(repo),
            UploadProductImageUseCase(repo)
        )
        advanceUntilIdle()
        vm.onNameChanged("My unsaved name")
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.detailsCallCount)
        assertEquals("My unsaved name", vm.uiState.value.name)
        assertTrue(vm.uiState.value.isDirty)
    }

    // ---------- fixtures ----------

    private fun page(values: List<ProductSummary>, pageNumber: Int = 0, hasNext: Boolean = false) =
        PagedProducts(values, pageNumber, 20, if (hasNext) 2 else 1, values.size.toLong(), hasNext)

    private fun product(id: String = "P1") = ProductSummary(
        productId = id,
        name = "Rice 5kg",
        categoryId = "C1",
        categoryName = "Groceries",
        price = java.math.BigDecimal("450.00"),
        discountedPrice = java.math.BigDecimal("400.00"),
        currencyCode = "INR",
        stock = 10,
        unit = "bag",
        status = ProductStatus.ACTIVE,
        availability = ProductAvailability.IN_STOCK,
        imageUrl = null
    )

    private fun details(actions: List<ProductAdminAction>) = ProductDetails(
        productId = "P1",
        name = "Rice 5kg",
        description = "Premium rice",
        categoryId = "C1",
        categoryName = "Groceries",
        price = java.math.BigDecimal("450.00"),
        discountedPrice = java.math.BigDecimal("400.00"),
        discountPercent = java.math.BigDecimal("11.11"),
        currencyCode = "INR",
        stock = 10,
        sku = "SKU-1",
        unit = "bag",
        status = ProductStatus.ACTIVE,
        availability = ProductAvailability.IN_STOCK,
        imageUrl = null,
        createdAt = "2026-09-01T10:00:00Z",
        updatedAt = "2026-09-08T10:00:00Z",
        allowedActions = actions
    )

    private class FakeRepository(
        private val listResults: MutableList<AppResult<PagedProducts>> = mutableListOf(),
        private val detailsResults: MutableList<AppResult<ProductDetails>> = mutableListOf(),
        private val categoryResult: AppResult<List<CategoryOption>> = AppResult.Success(emptyList()),
        private val createResult: AppResult<ProductDetails> = AppResult.Failure(
            AppFailure("Not used", type = FailureType.UNKNOWN)
        ),
        private val updateResult: AppResult<ProductDetails> = AppResult.Failure(
            AppFailure("Not used", type = FailureType.UNKNOWN)
        ),
        private val actionResult: AppResult<Unit> = AppResult.Success(Unit),
        private val actionDelayMs: Long = 0L,
        private val createDelayMs: Long = 0L,
        private val listDelayMs: Long = 0L
    ) : ProductManagementRepository {
        val listQueries = mutableListOf<ProductsQuery>()
        val actions = mutableListOf<ProductAdminAction>()
        var createCalls = 0
        var detailsCallCount = 0

        override suspend fun getProducts(query: ProductsQuery): AppResult<PagedProducts> {
            listQueries += query
            val result = if (listResults.isNotEmpty()) {
                listResults.removeAt(0)
            } else {
                AppResult.Success(PagedProducts(emptyList(), 0, 20, 1, 0, false))
            }
            if (listDelayMs > 0) delay(listDelayMs)
            return result
        }

        override suspend fun getProductDetails(productId: String): AppResult<ProductDetails> {
            detailsCallCount += 1
            return if (detailsResults.isNotEmpty()) {
                detailsResults.removeAt(0)
            } else {
                AppResult.Failure(AppFailure("Not used", type = FailureType.UNKNOWN))
            }
        }

        override suspend fun getCategoryOptions(): AppResult<List<CategoryOption>> = categoryResult

        override suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetails> {
            if (createDelayMs > 0) delay(createDelayMs)
            createCalls += 1
            return createResult
        }

        override suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetails> =
            updateResult

        override suspend fun uploadProductImage(
            productId: String,
            image: ProductImageUpload
        ): AppResult<ProductDetails> = AppResult.Success(
            ProductDetails(
                productId = productId,
                name = "Rice 5kg",
                description = null,
                categoryId = "C1",
                categoryName = "Groceries",
                price = java.math.BigDecimal("45"),
                discountedPrice = java.math.BigDecimal("45"),
                discountPercent = java.math.BigDecimal.ZERO,
                currencyCode = "INR",
                stock = 10,
                sku = null,
                unit = "kg",
                status = ProductStatus.ACTIVE,
                availability = ProductAvailability.IN_STOCK,
                imageUrl = "/api/v1/catalog/products/$productId/image",
                createdAt = null,
                updatedAt = null,
                allowedActions = emptyList()
            )
        )

        override suspend fun performProductAction(productId: String, action: ProductAdminAction): AppResult<Unit> {
            if (actionDelayMs > 0) delay(actionDelayMs)
            actions += action
            return actionResult
        }
    }
}

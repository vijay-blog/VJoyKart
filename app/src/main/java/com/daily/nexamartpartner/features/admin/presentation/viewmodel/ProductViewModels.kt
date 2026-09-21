package com.daily.nexamartpartner.features.admin.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.repository.defaultProductsQuery
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import com.daily.nexamartpartner.features.admin.domain.model.ProductFilters
import com.daily.nexamartpartner.features.admin.domain.model.ProductSort
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
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
import java.math.BigDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProductEvent {
    data object SessionExpired : ProductEvent
    data class Message(val text: String) : ProductEvent
    data class ActionSucceeded(val action: ProductAdminAction) : ProductEvent
    data object SavedSuccessfully : ProductEvent
}

class ProductListViewModel(
    private val getProducts: GetProductsUseCase,
    private val getCategoryOptions: GetProductCategoryOptionsUseCase
) : ViewModel() {
    private val defaultQuery = defaultProductsQuery()
    private val _uiState = MutableStateFlow(
        ProductListUiState(sort = defaultQuery.sort, filters = defaultQuery.filters)
    )
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ProductEvent> = _events.asSharedFlow()

    private val products = mutableListOf<ProductSummary>()
    private var requestJob: Job? = null
    private var searchJob: Job? = null
    private var page = 0
    private var hasNext = true

    init {
        load(0, append = false, showLoading = true)
        loadCategoryOptions()
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(0, append = false, showLoading = true)
        }
    }

    fun applyFilters(filters: ProductFilters) {
        _uiState.update { it.copy(filters = filters) }
        load(0, append = false, showLoading = true)
    }

    fun applySort(sort: ProductSort) {
        _uiState.update { it.copy(sort = sort) }
        load(0, append = false, showLoading = true)
    }

    fun applyCriteria(filters: ProductFilters, sort: ProductSort) {
        _uiState.update { it.copy(filters = filters, sort = sort) }
        load(0, append = false, showLoading = true)
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(searchQuery = "", filters = ProductFilters(), sort = defaultQuery.sort)
        }
        load(0, append = false, showLoading = true)
    }

    fun refresh() {
        if (requestJob?.isActive == true) return
        _uiState.update { it.copy(isRefreshing = true) }
        load(0, append = false, showLoading = false)
    }

    fun retry() {
        load(0, append = false, showLoading = true)
    }

    fun loadNextPage() {
        if (!hasNext || requestJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingMore = true) }
        load(page + 1, append = true, showLoading = false)
    }

    private fun loadCategoryOptions() {
        viewModelScope.launch {
            when (val result = getCategoryOptions()) {
                is AppResult.Success -> _uiState.update { it.copy(categoryOptions = result.data) }
                is AppResult.Failure -> Unit
            }
        }
    }

    private fun load(targetPage: Int, append: Boolean, showLoading: Boolean) {
        if (requestJob?.isActive == true) {
            if (append) return
            requestJob?.cancel()
        }
        if (showLoading) {
            _uiState.update { it.copy(content = ProductListUiState.Content.Loading) }
        }
        requestJob = viewModelScope.launch {
            val state = _uiState.value
            val query = ProductsQuery(
                page = targetPage,
                pageSize = defaultQuery.pageSize,
                searchText = state.searchQuery.trim().ifBlank { null },
                filters = state.filters,
                sort = state.sort
            )
            when (val result = getProducts(query)) {
                is AppResult.Success -> onPageSuccess(result.data, append)
                is AppResult.Failure -> onPageFailure(result.error.type, result.error.message, append)
            }
        }
    }

    private fun onPageSuccess(pageData: PagedProducts, append: Boolean) {
        if (!append) products.clear()
        products.addAll(pageData.products)
        page = pageData.page
        hasNext = pageData.hasNextPage

        val state = _uiState.value
        val hasCriteria = state.searchQuery.isNotBlank() || state.filters != ProductFilters()
        val content = if (products.isEmpty()) {
            ProductListUiState.Content.Empty(
                message = when {
                    state.searchQuery.isNotBlank() -> "No products match your search."
                    hasCriteria -> "No products match the selected filters."
                    else -> "No products found."
                },
                showClearFilters = hasCriteria
            )
        } else {
            ProductListUiState.Content.Success(products.toList(), hasNext)
        }

        _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, content = content) }
    }

    private fun onPageFailure(type: FailureType, message: String, append: Boolean) {
        if (type == FailureType.UNAUTHORIZED) _events.tryEmit(ProductEvent.SessionExpired)
        if (append && products.isNotEmpty()) {
            _events.tryEmit(ProductEvent.Message(message.ifBlank { "Unable to load more products." }))
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    content = ProductListUiState.Content.Success(products.toList(), hasNext)
                )
            }
            return
        }
        val content = if (type == FailureType.CONTRACT_MISSING) {
            ProductListUiState.Content.Unavailable(message)
        } else {
            ProductListUiState.Content.Error(message.ifBlank { "Unable to load products. Please try again." })
        }
        _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, content = content) }
    }
}

class ProductDetailsViewModel(
    private val productId: String,
    private val getProductDetails: GetProductDetailsUseCase,
    private val performProductAdminAction: PerformProductAdminActionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductDetailsUiState())
    val uiState: StateFlow<ProductDetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ProductEvent> = _events.asSharedFlow()

    private var requestJob: Job? = null

    init {
        load(showLoading = true)
    }

    fun retry() = load(showLoading = true)

    fun refresh() {
        if (requestJob?.isActive == true) return
        _uiState.update { it.copy(isRefreshing = true) }
        load(showLoading = false)
    }

    fun performAction(action: ProductAdminAction) {
        if (_uiState.value.actionInProgress != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = action) }
            when (val result = performProductAdminAction(productId, action)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(actionInProgress = null) }
                    _events.tryEmit(ProductEvent.ActionSucceeded(action))
                    // A deleted product can no longer be reloaded by id, so skip the refresh.
                    if (action != ProductAdminAction.DELETE) {
                        load(showLoading = false)
                    }
                }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(actionInProgress = null) }
                    if (result.error.type == FailureType.UNAUTHORIZED) {
                        _events.tryEmit(ProductEvent.SessionExpired)
                    } else {
                        _events.tryEmit(ProductEvent.Message(result.error.message))
                    }
                    if (result.error.code == 409) load(showLoading = false)
                }
            }
        }
    }

    private fun load(showLoading: Boolean) {
        if (requestJob?.isActive == true) return
        if (showLoading) {
            _uiState.update { it.copy(content = ProductDetailsUiState.Content.Loading) }
        }
        requestJob = viewModelScope.launch {
            when (val result = getProductDetails(productId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isRefreshing = false, content = ProductDetailsUiState.Content.Success(result.data))
                }

                is AppResult.Failure -> {
                    if (result.error.type == FailureType.UNAUTHORIZED) {
                        _events.tryEmit(ProductEvent.SessionExpired)
                    }
                    val content = if (result.error.type == FailureType.CONTRACT_MISSING) {
                        ProductDetailsUiState.Content.Unavailable(result.error.message)
                    } else {
                        ProductDetailsUiState.Content.Error(
                            result.error.message.ifBlank { "Unable to load product. Please try again." }
                        )
                    }
                    _uiState.update { it.copy(isRefreshing = false, content = content) }
                }
            }
        }
    }
}

class ProductFormViewModel(
    private val mode: ProductFormUiState.Mode,
    private val productId: String?,
    private val getProductDetails: GetProductDetailsUseCase?,
    private val getCategoryOptions: GetProductCategoryOptionsUseCase,
    private val createProduct: CreateProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val uploadProductImage: UploadProductImageUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductFormUiState(mode = mode))
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ProductEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var selectedImage: ProductImageUpload? = null
    private var persistedCreateProductId: String? = null

    init {
        loadCategoryOptions()
        if (mode == ProductFormUiState.Mode.EDIT) {
            loadProductForEdit()
        }
    }

    fun onNameChanged(value: String) {
        _uiState.update {
            it.copy(name = value, isDirty = true, fieldErrors = it.fieldErrors.copy(name = null))
        }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update { it.copy(description = value, isDirty = true) }
    }

    fun onCategorySelected(categoryId: String?) {
        _uiState.update {
            it.copy(selectedCategoryId = categoryId, isDirty = true, fieldErrors = it.fieldErrors.copy(category = null))
        }
    }

    fun onPriceChanged(value: String) {
        _uiState.update {
            it.copy(price = value, isDirty = true, fieldErrors = it.fieldErrors.copy(price = null))
        }
    }

    fun onDiscountChanged(value: String) {
        _uiState.update {
            it.copy(discountPercent = value, isDirty = true, fieldErrors = it.fieldErrors.copy(discount = null))
        }
    }

    fun onStockChanged(value: String) {
        _uiState.update {
            it.copy(stock = value, isDirty = true, fieldErrors = it.fieldErrors.copy(stock = null))
        }
    }

    fun onSkuChanged(value: String) {
        _uiState.update { it.copy(sku = value, isDirty = true) }
    }

    fun onUnitChanged(value: String) {
        _uiState.update { it.copy(unit = value, isDirty = true) }
    }

    fun onImageUrlChanged(value: String) {
        _uiState.update { it.copy(imageUrl = value, isDirty = true) }
    }

    fun onImageSelected(image: ProductImageUpload) {
        selectedImage = image
        _uiState.update { it.copy(isDirty = true, imageFileName = image.fileName) }
    }

    fun onImageSelectionCleared() {
        selectedImage = null
        _uiState.update { it.copy(isDirty = true, imageFileName = null) }
    }

    fun hasUnsavedChanges(): Boolean = _uiState.value.isDirty

    fun retry() {
        if (mode == ProductFormUiState.Mode.EDIT) loadProductForEdit()
        loadCategoryOptions()
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val existingCreatedId = persistedCreateProductId
        if (mode == ProductFormUiState.Mode.CREATE && existingCreatedId != null) {
            if (selectedImage != null) {
                uploadOnly(existingCreatedId, selectedImage!!)
            } else {
                _uiState.update { it.copy(isSaving = false, isDirty = false) }
                _events.tryEmit(ProductEvent.SavedSuccessfully)
            }
            return
        }

        val errors = validate(state)
        if (!errors.isEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        val draft = ProductDraft(
            name = state.name.trim(),
            description = state.description.trim().ifBlank { null },
            categoryId = state.selectedCategoryId,
            price = state.price.trim(),
            discountPercent = state.discountPercent.trim().ifBlank { null },
            stock = state.stock.trim().ifBlank { null },
            sku = state.sku.trim().ifBlank { null },
            unit = state.unit.trim().ifBlank { null },
            imageUrl = state.imageUrl.trim().ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (mode == ProductFormUiState.Mode.CREATE) {
                createProduct(draft)
            } else {
                updateProduct(requireNotNull(productId), draft)
            }

            when (result) {
                is AppResult.Success -> {
                    val saved = result.data
                    val image = selectedImage
                    if (image != null) {
                        when (val upload = uploadProductImage(saved.productId, image)) {
                            is AppResult.Success -> {
                                _uiState.update { it.copy(isSaving = false, isDirty = false) }
                                _events.tryEmit(ProductEvent.SavedSuccessfully)
                            }
                            is AppResult.Failure -> {
                                if (mode == ProductFormUiState.Mode.CREATE) {
                                    persistedCreateProductId = saved.productId
                                }
                                _uiState.update { it.copy(isSaving = false) }
                                _events.tryEmit(
                                    ProductEvent.Message(
                                        "Product saved, but photo upload failed: ${upload.error.message}"
                                    )
                                )
                            }
                        }
                    } else {
                        _uiState.update { it.copy(isSaving = false, isDirty = false) }
                        _events.tryEmit(ProductEvent.SavedSuccessfully)
                    }
                }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    when {
                        result.error.type == FailureType.UNAUTHORIZED ->
                            _events.tryEmit(ProductEvent.SessionExpired)

                        result.error.type == FailureType.CONTRACT_MISSING ->
                            _uiState.update {
                                it.copy(content = ProductFormUiState.Content.Unavailable(result.error.message))
                            }

                        else -> _events.tryEmit(ProductEvent.Message(result.error.message))
                    }
                }
            }
        }
    }

    private fun uploadOnly(productId: String, image: ProductImageUpload) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val upload = uploadProductImage(productId, image)) {
                is AppResult.Success -> {
                    persistedCreateProductId = null
                    _uiState.update { it.copy(isSaving = false, isDirty = false) }
                    _events.tryEmit(ProductEvent.SavedSuccessfully)
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.tryEmit(ProductEvent.Message("Photo upload failed: ${upload.error.message}"))
                }
            }
        }
    }

    private fun validate(state: ProductFormUiState): ProductFormUiState.FieldErrors {
        val nameError = if (state.name.isBlank()) "Product name is required." else null
        val categoryError = if (state.selectedCategoryId.isNullOrBlank()) "Category is required." else null

        val priceError = when {
            state.price.isBlank() -> "Price is required."
            state.price.trim().toBigDecimalOrNull() == null -> "Enter a valid price."
            state.price.trim().toBigDecimalOrNull()!! < BigDecimal.ZERO -> "Price cannot be negative."
            else -> null
        }

        val discountError = if (state.discountPercent.isNotBlank()) {
            val parsed = state.discountPercent.trim().toBigDecimalOrNull()
            when {
                parsed == null -> "Enter a valid discount percentage."
                parsed < BigDecimal.ZERO || parsed > BigDecimal(100) ->
                    "Discount must be between 0 and 100."
                else -> null
            }
        } else null

        val stockError = if (state.stock.isNotBlank()) {
            val parsed = state.stock.trim().toIntOrNull()
            when {
                parsed == null -> "Enter a valid whole number for stock."
                parsed < 0 -> "Stock cannot be negative."
                else -> null
            }
        } else null

        return ProductFormUiState.FieldErrors(
            name = nameError,
            category = categoryError,
            price = priceError,
            discount = discountError,
            stock = stockError
        )
    }

    private fun loadCategoryOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true) }
            when (val result = getCategoryOptions()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isLoadingCategories = false,
                        categoryOptions = result.data,
                        categoryOptionsUnavailableMessage = null
                    )
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoadingCategories = false,
                        categoryOptions = emptyList(),
                        categoryOptionsUnavailableMessage = result.error.message
                    )
                }
            }
        }
    }

    private fun loadProductForEdit() {
        val id = productId ?: return
        val useCase = getProductDetails ?: return
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(content = ProductFormUiState.Content.Loading, isLoadingDetails = true) }
        loadJob = viewModelScope.launch {
            when (val result = useCase(id)) {
                is AppResult.Success -> {
                    val product = result.data
                    _uiState.update {
                        it.copy(
                            isLoadingDetails = false,
                            content = ProductFormUiState.Content.Editing,
                            name = product.name,
                            description = product.description.orEmpty(),
                            selectedCategoryId = product.categoryId,
                            price = product.price?.toPlainString().orEmpty(),
                            discountPercent = product.discountPercent?.toPlainString().orEmpty(),
                            stock = product.stock?.toString().orEmpty(),
                            sku = product.sku.orEmpty(),
                            unit = product.unit.orEmpty(),
                            imageUrl = product.imageUrl.orEmpty(),
                            isDirty = false
                        )
                    }
                }

                is AppResult.Failure -> {
                    if (result.error.type == FailureType.UNAUTHORIZED) {
                        _events.tryEmit(ProductEvent.SessionExpired)
                    }
                    val content = if (result.error.type == FailureType.CONTRACT_MISSING) {
                        ProductFormUiState.Content.Unavailable(result.error.message)
                    } else {
                        ProductFormUiState.Content.Error(
                            result.error.message.ifBlank { "Unable to load product. Please try again." }
                        )
                    }
                    _uiState.update { it.copy(isLoadingDetails = false, content = content) }
                }
            }
        }
    }
}

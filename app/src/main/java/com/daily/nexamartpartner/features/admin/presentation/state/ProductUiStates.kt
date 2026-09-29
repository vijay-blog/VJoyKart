package com.daily.nexamartpartner.features.admin.presentation.state

import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductFilters
import com.daily.nexamartpartner.features.admin.domain.model.ProductSort
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary

data class ProductListUiState(
    val searchQuery: String = "",
    val filters: ProductFilters = ProductFilters(),
    val sort: ProductSort = ProductSort.NEWEST,
    val categoryOptions: List<CategoryOption> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val content: Content = Content.Loading
) {
    sealed interface Content {
        data object Loading : Content
        data class Success(val products: List<ProductSummary>, val hasNextPage: Boolean) : Content
        data class Empty(val message: String, val showClearFilters: Boolean) : Content
        data class Error(val message: String) : Content
        data class Unavailable(val message: String) : Content
    }
}

data class ProductDetailsUiState(
    val isRefreshing: Boolean = false,
    val actionInProgress: ProductAdminAction? = null,
    val content: Content = Content.Loading
) {
    sealed interface Content {
        data object Loading : Content
        data class Success(val product: ProductDetails) : Content
        data class Error(val message: String) : Content
        data class Unavailable(val message: String) : Content
    }
}

data class ProductFormUiState(
    val mode: Mode = Mode.CREATE,
    val isLoadingDetails: Boolean = false,
    val isLoadingCategories: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val name: String = "",
    val description: String = "",
    val selectedCategoryId: String? = null,
    val categoryOptions: List<CategoryOption> = emptyList(),
    val categoryOptionsUnavailableMessage: String? = null,
    val price: String = "",
    val discountPercent: String = "",
    val stock: String = "",
    val sku: String = "",
    val unit: String = "",
    val imageUrl: String = "",
    val imageFileName: String? = null,
    val fieldErrors: FieldErrors = FieldErrors(),
    val content: Content = Content.Editing
) {
    enum class Mode { CREATE, EDIT }

    data class FieldErrors(
        val name: String? = null,
        val category: String? = null,
        val price: String? = null,
        val discount: String? = null,
        val stock: String? = null
    ) {
        fun isEmpty(): Boolean =
            name == null && category == null && price == null && discount == null && stock == null
    }

    sealed interface Content {
        data object Loading : Content
        data object Editing : Content
        data class Error(val message: String) : Content
        data class Unavailable(val message: String) : Content
    }
}

package com.daily.nexamartpartner.features.admin.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.daily.nexamartpartner.features.admin.domain.usecase.CreateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductCategoryOptionsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductDetailsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.GetProductsUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.PerformProductAdminActionUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UpdateProductUseCase
import com.daily.nexamartpartner.features.admin.domain.usecase.UploadProductImageUseCase
import com.daily.nexamartpartner.features.admin.presentation.state.ProductFormUiState

class ProductListViewModelFactory(
    private val getProducts: GetProductsUseCase,
    private val getCategoryOptions: GetProductCategoryOptionsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProductListViewModel::class.java))
        return ProductListViewModel(getProducts, getCategoryOptions) as T
    }
}

class ProductDetailsViewModelFactory(
    private val productId: String,
    private val getProductDetails: GetProductDetailsUseCase,
    private val performProductAdminAction: PerformProductAdminActionUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProductDetailsViewModel::class.java))
        return ProductDetailsViewModel(productId, getProductDetails, performProductAdminAction) as T
    }
}

class ProductFormViewModelFactory(
    private val mode: ProductFormUiState.Mode,
    private val productId: String?,
    private val getProductDetails: GetProductDetailsUseCase?,
    private val getCategoryOptions: GetProductCategoryOptionsUseCase,
    private val createProduct: CreateProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val uploadProductImage: UploadProductImageUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProductFormViewModel::class.java))
        return ProductFormViewModel(
            mode,
            productId,
            getProductDetails,
            getCategoryOptions,
            createProduct,
            updateProduct,
            uploadProductImage
        ) as T
    }
}

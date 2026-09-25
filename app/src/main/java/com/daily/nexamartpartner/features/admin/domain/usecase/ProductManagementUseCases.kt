package com.daily.nexamartpartner.features.admin.domain.usecase

import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.admin.domain.model.CategoryOption
import com.daily.nexamartpartner.features.admin.domain.model.PagedProducts
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDetails
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
import com.daily.nexamartpartner.features.admin.domain.repository.ProductManagementRepository

class GetProductsUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(query: ProductsQuery): AppResult<PagedProducts> =
        repository.getProducts(query)
}

class GetProductDetailsUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(productId: String): AppResult<ProductDetails> =
        repository.getProductDetails(productId)
}

class GetProductCategoryOptionsUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(): AppResult<List<CategoryOption>> =
        repository.getCategoryOptions()
}

class CreateProductUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(draft: ProductDraft): AppResult<ProductDetails> =
        repository.createProduct(draft)
}

class UpdateProductUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(productId: String, draft: ProductDraft): AppResult<ProductDetails> =
        repository.updateProduct(productId, draft)
}



class UploadProductImageUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(productId: String, image: ProductImageUpload): AppResult<ProductDetails> =
        repository.uploadProductImage(productId, image)
}

class PerformProductAdminActionUseCase(private val repository: ProductManagementRepository) {
    suspend operator fun invoke(productId: String, action: ProductAdminAction): AppResult<Unit> =
        repository.performProductAction(productId, action)
}

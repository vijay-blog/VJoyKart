package com.daily.nexamartpartner.features.admin.data.source

import com.daily.nexamartpartner.core.network.ApiCallExecutor
import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.contract.ProductManagementContract
import com.daily.nexamartpartner.features.admin.data.model.CategoryOptionDto
import com.daily.nexamartpartner.features.admin.data.model.ProductDetailsDto
import com.daily.nexamartpartner.features.admin.data.model.ProductsPageDto
import com.daily.nexamartpartner.features.admin.domain.model.ProductAdminAction
import com.daily.nexamartpartner.features.admin.domain.model.ProductDraft
import com.daily.nexamartpartner.features.admin.domain.model.ProductsQuery
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface ProductManagementApi {
    @GET
    suspend fun getProducts(
        @Url path: String,
        @QueryMap params: Map<String, String>
    ): Response<ProductsPageDto>

    @GET
    suspend fun getProductDetails(@Url path: String): Response<ProductDetailsDto>

    @GET
    suspend fun getCategoryOptions(@Url path: String): Response<List<CategoryOptionDto>>

    @POST
    suspend fun createProduct(
        @Url path: String,
        @Body body: Map<String, String>
    ): Response<ProductDetailsDto>

    @PATCH
    suspend fun updateProduct(
        @Url path: String,
        @Body body: Map<String, String>
    ): Response<ProductDetailsDto>


    @Multipart
    @POST
    suspend fun uploadProductImage(
        @Url path: String,
        @Part image: MultipartBody.Part
    ): Response<ProductDetailsDto>

    @PATCH
    suspend fun performProductAction(
        @Url path: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}

interface ProductManagementRemoteDataSource {
    suspend fun getProducts(query: ProductsQuery): AppResult<ProductsPageDto>
    suspend fun getProductDetails(productId: String): AppResult<ProductDetailsDto>
    suspend fun getCategoryOptions(): AppResult<List<CategoryOptionDto>>
    suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetailsDto>
    suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetailsDto>
    suspend fun uploadProductImage(productId: String, image: com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload): AppResult<ProductDetailsDto>
    suspend fun performProductAction(productId: String, action: ProductAdminAction): AppResult<Unit>
}

class ProductManagementRemoteDataSourceImpl(
    private val api: ProductManagementApi,
    private val contract: ProductManagementContract,
    private val executor: ApiCallExecutor
) : ProductManagementRemoteDataSource {

    override suspend fun getProducts(query: ProductsQuery): AppResult<ProductsPageDto> {
        val path = contract.listProductsPath
            ?: return contractMissing("Product list API contract is not confirmed yet.")
        val params = contract.buildProductListQuery(query)
            ?: return contractMissing("Product list query contract is not confirmed yet.")
        return executor.execute { api.getProducts(path, params) }
    }

    override suspend fun getProductDetails(productId: String): AppResult<ProductDetailsDto> {
        val path = contract.resolvePath(contract.productDetailsPathTemplate, productId)
            ?: return contractMissing("Product details API contract is not confirmed yet.")
        return executor.execute { api.getProductDetails(path) }
    }

    override suspend fun getCategoryOptions(): AppResult<List<CategoryOptionDto>> {
        val path = contract.categoryOptionsPath
            ?: return contractMissing("Category lookup API contract is not confirmed yet.")
        return executor.execute { api.getCategoryOptions(path) }
    }

    override suspend fun createProduct(draft: ProductDraft): AppResult<ProductDetailsDto> {
        val path = contract.createProductPath
            ?: return contractMissing("Product creation API contract is not confirmed yet.")
        val body = contract.buildCreateProductBody(draft)
            ?: return contractMissing("Product creation request contract is not confirmed yet.")
        return executor.execute { api.createProduct(path, body) }
    }

    override suspend fun updateProduct(productId: String, draft: ProductDraft): AppResult<ProductDetailsDto> {
        val path = contract.resolvePath(contract.updateProductPathTemplate, productId)
            ?: return contractMissing("Product update API contract is not confirmed yet.")
        val body = contract.buildUpdateProductBody(draft)
            ?: return contractMissing("Product update request contract is not confirmed yet.")
        return executor.execute { api.updateProduct(path, body) }
    }



    override suspend fun uploadProductImage(
        productId: String,
        image: com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
    ): AppResult<ProductDetailsDto> {
        val path = contract.resolvePath(contract.productImagePathTemplate, productId)
            ?: return contractMissing("Product image upload API contract is not confirmed yet.")
        val body = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse(image.contentType),
            image.bytes
        )
        val part = MultipartBody.Part.createFormData("image", image.fileName, body)
        return executor.execute { api.uploadProductImage(path, part) }
    }

    override suspend fun performProductAction(
        productId: String,
        action: ProductAdminAction
    ): AppResult<Unit> {
        val path = contract.resolvePath(contract.productActionPathTemplate, productId)
            ?: return contractMissing("Product action API contract is not confirmed yet.")
        val body = contract.buildProductActionBody(action)
            ?: return contractMissing("Product action request contract is not confirmed yet.")
        return when (val result = executor.execute { api.performProductAction(path, body) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    private fun <T> contractMissing(message: String): AppResult<T> {
        return AppResult.Failure(AppFailure(message = message, type = FailureType.CONTRACT_MISSING))
    }
}

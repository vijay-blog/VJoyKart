package com.nexamart.customer.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Customer endpoints of the existing VJoyKart Spring Boot backend (base `…/api/v1/`).
 * Paths, methods and bodies are exactly the ones the Flutter app called. Bodies and responses
 * stay untyped JSON because the backend mixes numbers and numeric strings.
 */
interface CustomerApi {
    @POST("auth/customer/send-otp")
    suspend fun sendOtp(@Body body: RequestBody): Response<ResponseBody>

    @POST("auth/customer/verify-otp")
    suspend fun verifyOtp(@Body body: RequestBody): Response<ResponseBody>

    @GET("catalog/products")
    suspend fun products(
        @Query("search") search: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ResponseBody>

    @GET("customer/orders")
    suspend fun orders(): Response<ResponseBody>

    @POST("customer/orders")
    suspend fun createOrder(@Body body: RequestBody): Response<ResponseBody>

    @GET("customer/orders/{id}/tracking")
    suspend fun tracking(@Path("id") orderId: String): Response<ResponseBody>

    @POST("payments/create-order")
    suspend fun createPaymentOrder(@Body body: RequestBody): Response<ResponseBody>

    @POST("payments/verify")
    suspend fun verifyPayment(@Body body: RequestBody): Response<ResponseBody>
}

/** Public, unauthenticated Partner catalog used only to enrich product images. */
interface PartnerCatalogApi {
    @GET("api/v1/catalog/products")
    suspend fun products(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<ResponseBody>
}

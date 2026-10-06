package com.nexamart.customer.data.network

import android.util.Log
import com.nexamart.customer.BuildConfig
import com.nexamart.customer.core.ApiException
import com.nexamart.customer.core.AppConfig
import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.util.Json
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Builds the HTTP stack and executes calls with the error mapping of the Flutter ApiService. */
class ApiClient(private val prefs: CustomerPrefs) {
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.ENABLE_NETWORK_LOGGING) {
                addInterceptor(
                    HttpLoggingInterceptor { Log.d("VJoyKartApi", it) }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    private val customerClient: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(authInterceptor())
        .callTimeout(AppConfig.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val partnerClient: OkHttpClient = baseClient.newBuilder()
        .callTimeout(AppConfig.PARTNER_CATALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val customerApi: CustomerApi = Retrofit.Builder()
        .baseUrl(AppConfig.apiBaseUrl + "/")
        .client(customerClient)
        .build()
        .create(CustomerApi::class.java)

    val partnerCatalogApi: PartnerCatalogApi = Retrofit.Builder()
        .baseUrl(AppConfig.catalogImageBaseUrl + "/")
        .client(partnerClient)
        .build()
        .create(PartnerCatalogApi::class.java)

    /** `vk.accessToken ?? vk.guestAccessToken`, read for every request like the Flutter app. */
    private fun authInterceptor() = Interceptor { chain ->
        val token = prefs.getString(CustomerPrefs.ACCESS_TOKEN) ?: prefs.getString(CustomerPrefs.GUEST_ACCESS_TOKEN)
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token") }
            .build()
        chain.proceed(request)
    }

    /**
     * Executes a call and returns the decoded JSON (Map/List/primitive) or null for an empty 2xx body.
     * Throws [ApiException] with exactly the messages the Flutter ApiService produced.
     */
    suspend fun execute(call: suspend () -> Response<ResponseBody>): Any? = withContext(Dispatchers.IO) {
        executeInternal(call)
    }

    private suspend fun executeInternal(call: suspend () -> Response<ResponseBody>): Any? {
        AppConfig.runtimeConfigurationIssue()?.let { throw ApiException(it, 0) }
        val response = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog("Network error", e)
            throw ApiException(ApiException.CONNECT_MESSAGE, 0)
        }
        val code = response.code()
        if (response.isSuccessful) {
            val text = try {
                response.body()?.string().orEmpty()
            } catch (e: Exception) {
                debugLog("Body read error", e)
                throw ApiException(ApiException.CONNECT_MESSAGE, 0)
            }
            if (text.isEmpty()) return null
            return try {
                Json.decode(text)
            } catch (e: Exception) {
                if (e !is JsonEncodingException && e !is JsonDataException && e !is java.io.IOException) {
                    debugLog("Unexpected parse error", e)
                }
                throw ApiException(ApiException.FORMAT_MESSAGE, 0)
            }
        }
        var message = ApiException.messageForStatus(code)
        try {
            val errorText = response.errorBody()?.string().orEmpty()
            val decoded = Json.decode(errorText)
            if (decoded is Map<*, *>) {
                val candidate = decoded["message"] ?: decoded["error"] ?: decoded["detail"]
                if (candidate != null && candidate.toString().trim().isNotEmpty()) message = candidate.toString()
            }
        } catch (_: Exception) {
            // Keep the friendly status-based message for non-JSON responses.
        }
        throw ApiException(message, code)
    }

    private fun debugLog(label: String, error: Throwable) {
        if (BuildConfig.DEBUG) Log.w("VJoyKartApi", "$label: $error", error)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun jsonBody(body: Map<String, Any?>): RequestBody = Json.encode(body).toRequestBody(JSON_MEDIA_TYPE)
    }
}

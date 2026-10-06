package com.nexamart.customer.repository

import android.util.Base64
import com.nexamart.customer.core.ApiException
import com.nexamart.customer.core.UserFacingException
import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.data.network.ApiClient
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.JsonMap
import com.nexamart.customer.util.asJsonMap
import com.nexamart.customer.util.jsonDoubleOrNull
import com.nexamart.customer.util.jsonString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OtpSendResult(
    val success: Boolean,
    val message: String,
    val expiresInSeconds: Int,
    val deliveryChannel: String,
    val debugOtp: String?,
) {
    companion object {
        fun fromJson(json: JsonMap) = OtpSendResult(
            success = json["success"] == true,
            message = json["message"].jsonString() ?: "OTP sent successfully.",
            expiresInSeconds = (json["expiresInSeconds"] as? Number)?.toInt() ?: 300,
            deliveryChannel = json["deliveryChannel"].jsonString() ?: "SMS",
            debugOtp = json["debugOtp"].jsonString(),
        )
    }
}

/** Port of lib/services/customer_session.dart (CUSTOMER OTP auth, never the partner endpoints). */
class SessionRepository(
    private val prefs: CustomerPrefs,
    private val api: ApiClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val base64UrlDecoder: (String) -> ByteArray = { Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) },
) {
    private val _authenticated = MutableStateFlow(isAuthenticated())
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    val customerPhone: String? get() = prefs.getString(CustomerPrefs.CUSTOMER_PHONE)

    private fun currentToken(): String? =
        prefs.getString(CustomerPrefs.ACCESS_TOKEN) ?: prefs.getString(CustomerPrefs.GUEST_ACCESS_TOKEN)

    fun isAuthenticated(): Boolean {
        val id = prefs.getInt(CustomerPrefs.CUSTOMER_ID)
        val token = currentToken()
        return id != null && id > 0 && !token.isNullOrEmpty() && !isExpired(token)
    }

    /** Re-evaluates token expiry (e.g. when returning to the app). */
    fun refreshState() {
        _authenticated.value = isAuthenticated()
    }

    fun ensureCustomerId(): Int {
        val existing = prefs.getInt(CustomerPrefs.CUSTOMER_ID)
        val token = currentToken()
        if (existing != null && existing > 0 && token != null && !isExpired(token)) return existing
        _authenticated.value = false
        throw UserFacingException("Please verify your mobile number before checkout.")
    }

    suspend fun sendOtp(phone: String): OtpSendResult {
        val response = api.execute {
            api.customerApi.sendOtp(ApiClient.jsonBody(mapOf("phone" to phone)))
        }.asJsonMap() ?: throw ApiException("Invalid OTP response from server.", 500)
        return OtpSendResult.fromJson(response)
    }

    suspend fun verifyOtp(phone: String, otp: String): Int {
        val response = api.execute {
            api.customerApi.verifyOtp(ApiClient.jsonBody(mapOf("phone" to phone, "otp" to otp)))
        }.asJsonMap() ?: throw ApiException("Invalid login response from server.", 500)
        return saveSession(response)
    }

    /** Clears the session exactly like CustomerSession.logout(). */
    fun logout() {
        prefs.remove(
            CustomerPrefs.CUSTOMER_ID,
            CustomerPrefs.ACCESS_TOKEN,
            CustomerPrefs.REFRESH_TOKEN,
            CustomerPrefs.GUEST_ACCESS_TOKEN,
            CustomerPrefs.GUEST_REFRESH_TOKEN,
            CustomerPrefs.CUSTOMER_PHONE,
        )
        _authenticated.value = false
    }

    private fun saveSession(response: JsonMap): Int {
        val user = response["user"].asJsonMap()
        val id = (user?.get("id") as? Number)?.toInt() ?: 0
        val accessToken = response["accessToken"].jsonString().orEmpty()
        val refreshToken = response["refreshToken"].jsonString().orEmpty()
        if (id <= 0 || accessToken.isEmpty() || refreshToken.isEmpty()) {
            throw UserFacingException("Unable to start your VJoyKart session.")
        }
        prefs.putInt(CustomerPrefs.CUSTOMER_ID, id)
        prefs.putString(CustomerPrefs.ACCESS_TOKEN, accessToken)
        prefs.putString(CustomerPrefs.REFRESH_TOKEN, refreshToken)
        // Legacy keys kept for parity with the Flutter app.
        prefs.putString(CustomerPrefs.GUEST_ACCESS_TOKEN, accessToken)
        prefs.putString(CustomerPrefs.GUEST_REFRESH_TOKEN, refreshToken)
        val phone = user?.get("phone").jsonString()
        if (!phone.isNullOrEmpty()) prefs.putString(CustomerPrefs.CUSTOMER_PHONE, phone)
        _authenticated.value = true
        return id
    }

    internal fun isExpired(token: String): Boolean = try {
        val parts = token.split('.')
        if (parts.size != 3) {
            true
        } else {
            val payload = Json.decode(String(base64UrlDecoder(parts[1]), Charsets.UTF_8)).asJsonMap()
            val expiresAt = payload?.get("exp").jsonDoubleOrNull()?.toLong()
            expiresAt == null || now() >= expiresAt * 1000
        }
    } catch (_: Exception) {
        true
    }
}

package com.nexamart.customer.core

/** Port of ApiException (lib/core/api_client.dart). [statusCode] is 0 for network/config errors. */
class ApiException(override val message: String, val statusCode: Int) : Exception(message) {
    companion object {
        const val CONNECT_MESSAGE = "Unable to connect to VjoyKart. Please check your internet connection."
        const val FORMAT_MESSAGE = "We could not process the server response. Please try again."

        fun messageForStatus(code: Int): String = when (code) {
            400 -> "Bad request. Please check your input."
            401 -> "Your session has expired. Please sign in again."
            403 -> "You don't have permission to perform this action."
            404 -> "We couldn't find the requested information."
            409 -> "This item or order was updated. Please try again."
            422 -> "Validation error. Please check submitted data."
            429 -> "Too many requests. Please try again in a moment."
            500, 502, 503 -> "VjoyKart is temporarily unavailable. Please try again shortly."
            else -> if (code >= 500) {
                "VjoyKart is temporarily unavailable. Please try again shortly."
            } else {
                "Unable to complete your request right now."
            }
        }
    }
}

/** A domain error whose message is safe to show to the customer. */
class UserFacingException(override val message: String) : Exception(message)

/** Converts any error into the user-facing message the Flutter screens displayed. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is ApiException -> message
    is UserFacingException -> message
    else -> fallback
}

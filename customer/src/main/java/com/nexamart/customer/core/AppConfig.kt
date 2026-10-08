package com.nexamart.customer.core

import com.nexamart.customer.BuildConfig

/** Port of lib/core/app_config.dart. */
object AppConfig {
    const val API_PREFIX = "/api/v1"

    /** e.g. https://zeptopluse-production.up.railway.app/api/v1 (no trailing slash). */
    val apiBaseUrl: String = normalizeBaseUrl(BuildConfig.API_BASE_URL)

    /**
     * Product image binaries are served by the Partner catalog/image backend. Kept separate
     * from the customer API so catalog images keep rendering when both are deployed independently.
     */
    val catalogImageBaseUrl: String = normalizeBaseUrl(BuildConfig.CATALOG_IMAGE_BASE_URL)

    /** Host part of [apiBaseUrl] without the /api/v1 suffix. */
    val apiHost: String get() = apiBaseUrl.removeSuffix(API_PREFIX)
    val accountDeletionUrl: String = BuildConfig.ACCOUNT_DELETION_URL.trim()

    const val TIMEOUT_SECONDS = 15L
    const val PARTNER_CATALOG_TIMEOUT_SECONDS = 8L
    const val SUPPORT_NUMBER = "9959095202"
    const val STORE_MAPS_FALLBACK = "https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9"

    /** Mirrors AppConfig.runtimeConfigurationIssue: release builds must use a public HTTPS backend. */
    fun runtimeConfigurationIssue(baseUrl: String = apiBaseUrl, isRelease: Boolean = !BuildConfig.DEBUG): String? {
        if (!isRelease) return null
        val value = baseUrl.lowercase()
        val issue = "VjoyKart is not configured for production yet."
        if (value.isEmpty() || !value.startsWith("https://")) return issue
        val host = value.removePrefix("https://").substringBefore('/').substringBefore(':')
        val private172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
        if (value.contains("railway.internal") || host.startsWith("10.") || host.startsWith("192.168.") ||
            private172 || host == "127.0.0.1" || host == "localhost"
        ) return issue
        return null
    }

    fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
}

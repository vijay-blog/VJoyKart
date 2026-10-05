package com.nexamart.customer.util

import com.nexamart.customer.core.AppConfig

/** Port of the URL resolution in lib/widgets/catalog_image.dart. Returns null for "no image". */
object ImageUrls {
    private const val PRODUCT_IMAGE_PATH = "/api/v1/catalog/products/"

    fun resolve(
        source: String?,
        apiHost: String = AppConfig.apiHost,
        catalogImageBaseUrl: String = AppConfig.catalogImageBaseUrl,
    ): String? {
        val raw = source?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        // Admin catalog images may be stored as a relative backend path such as
        // /uploads/products/abc.jpg. Product binaries are served by the Partner catalog.
        if (raw.startsWith("/")) {
            val base = if (raw.startsWith(PRODUCT_IMAGE_PATH)) catalogImageBaseUrl else apiHost
            return base + raw
        }
        // Bundled asset paths such as assets/images/products/rice.png (cached/legacy data).
        return "file:///android_asset/" + raw.removePrefix("assets/")
    }
}

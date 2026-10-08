package com.nexamart.customer.repository

import com.nexamart.customer.core.ApiException
import com.nexamart.customer.core.userMessage
import com.nexamart.customer.data.network.ApiClient
import com.nexamart.customer.model.Product
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.JsonMap
import com.nexamart.customer.util.asJsonMap
import com.nexamart.customer.util.jsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CatalogState(
    val products: List<Product> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

/** Port of lib/providers/catalog_provider.dart. */
class CatalogRepository(private val api: ApiClient) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()
    private val loadMutex = Mutex()

    val products: List<Product> get() = _state.value.products

    suspend fun load() = loadMutex.withLock {
        _state.update { it.copy(loading = true, error = null) }
        try {
            // Categories are local UI navigation; /catalog/categories is not used (same as Flutter).
            var products = withContext(Dispatchers.Default) {
                fetchAll(null).map(Product::fromJson).filter(::isListable)
            }
            // Best-effort enrichment from the public Partner catalog when only one image is known.
            if (products.any { it.images.size < 2 }) products = hydratePartnerImages(products)
            _state.value = CatalogState(products = products, loading = false, error = null, loaded = true)
        } catch (e: CancellationException) {
            _state.update { it.copy(loading = false) }
            throw e
        } catch (e: Exception) {
            _state.value = CatalogState(products = emptyList(), loading = false, error = e.userMessage(ApiException.FORMAT_MESSAGE), loaded = true)
        }
    }

    suspend fun searchRemote(query: String): List<Product> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return products
        return withContext(Dispatchers.Default) {
            fetchAll(normalized).map(Product::fromJson).filter(::isListable)
        }
    }

    fun productById(id: Int): Product? = products.firstOrNull { it.id == id }

    private fun isListable(product: Product): Boolean =
        product.id > 0 && product.name.isNotEmpty() && product.available && isFashionProduct(product)

    private suspend fun fetchAll(search: String?): List<JsonMap> {
        val items = mutableListOf<JsonMap>()
        var page = 0
        var hasNext = true
        while (hasNext) {
            val data = api.execute { api.customerApi.products(search, page, PAGE_SIZE) }
            if (data !is Map<*, *>) throw ApiException(ApiException.FORMAT_MESSAGE, 0)
            val content = data["content"] as? List<*> ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
            content.forEach { row -> row.asJsonMap()?.let(items::add) }
            hasNext = data["hasNextPage"] == true
            page++
            if (page >= MAX_PAGES) break
        }
        return items
    }

    internal suspend fun hydratePartnerImages(current: List<Product>): List<Product> = withContext(Dispatchers.IO) {
        try {
            val byId = linkedMapOf<Int, List<String>>()
            var page = 0
            var hasNext = true
            while (hasNext && page < MAX_PAGES) {
                val response = api.partnerCatalogApi.products(page, PAGE_SIZE)
                if (!response.isSuccessful) break
                val decoded = Json.decode(response.body()?.string().orEmpty())
                if (decoded !is Map<*, *> || decoded["content"] !is List<*>) break
                collectPartnerImages(decoded["content"] as List<*>, byId)
                hasNext = decoded["hasNextPage"] == true
                page++
            }
            mergePartnerImages(current, byId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            current
        }
    }

    companion object {
        private const val PAGE_SIZE = 100
        /** Safety cap against a backend that never stops reporting hasNextPage. */
        private const val MAX_PAGES = 50

        private val FASHION_KEYWORDS = listOf(
            "cloth", "clothing", "apparel", "fashion", "wear", "men", "mens",
            "man", "women", "womens", "woman", "boy", "boys", "girl", "girls",
            "kid", "kids", "t-shirt", "tshirt", "shirt", "jeans", "trouser",
            "pant", "pants", "dress", "saree", "sari", "kurti", "kurta", "top",
            "skirt", "shorts", "jacket", "hoodie", "sweatshirt", "sweater",
            "coat", "blazer", "ethnic", "western", "innerwear", "underwear",
            "nightwear", "sleepwear", "sportswear", "winterwear", "leggings",
            "palazzo", "salwar", "lehenga", "dupatta", "blouse", "track pant",
        )

        fun isFashionProduct(product: Product): Boolean {
            val text = listOf(
                product.name,
                product.categoryName,
                product.description,
                product.brand,
                product.unit,
                product.size,
                product.attributes.values.joinToString(" "),
            ).joinToString(" ").lowercase()
            return FASHION_KEYWORDS.any { text.contains(it) }
        }

        internal fun collectPartnerImages(rows: List<*>, byId: MutableMap<Int, List<String>>) {
            for (raw in rows) {
                val row = raw.asJsonMap() ?: continue
                val id = (row["productId"] ?: row["id"] ?: "").jsonString()?.toIntOrNull() ?: continue
                if (id <= 0) continue
                val images = mutableListOf<String>()
                (row["images"] as? List<*>)?.forEach { image ->
                    val map = image.asJsonMap()
                    val value = if (map != null) map["url"] ?: map["imageUrl"] ?: map["path"] else image
                    val url = value.jsonString()?.trim().orEmpty()
                    if (url.isNotEmpty() && url !in images) images += url
                }
                val primary = row["imageUrl"].jsonString()?.trim().orEmpty()
                if (images.isEmpty() && primary.isNotEmpty()) images += primary
                if (images.isNotEmpty()) byId[id] = images.take(3)
            }
        }

        internal fun mergePartnerImages(current: List<Product>, byId: Map<Int, List<String>>): List<Product> {
            if (byId.isEmpty()) return current
            return current.map { product ->
                val partnerImages = byId[product.id]
                if (partnerImages.isNullOrEmpty()) return@map product
                val unique = mutableListOf<String>()
                for (image in partnerImages + product.images) {
                    val trimmed = image.trim()
                    if (trimmed.isNotEmpty() && trimmed !in unique) unique += trimmed
                    if (unique.size == 3) break
                }
                product.copy(images = unique)
            }
        }
    }
}

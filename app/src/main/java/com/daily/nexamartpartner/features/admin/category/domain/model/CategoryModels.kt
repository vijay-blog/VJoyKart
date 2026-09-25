package com.daily.nexamartpartner.features.admin.category.domain.model

data class Category(
    val categoryId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val active: Boolean?,
    val productCount: Int?,
    val sortOrder: Int?,
    val createdAt: String?,
    val updatedAt: String?,
    val allowedActions: List<CategoryAdminAction>
)

enum class CategoryAdminAction(val backendValue: String) { ACTIVATE("ACTIVATE"), DEACTIVATE("DEACTIVATE"), DELETE("DELETE"), EDIT("EDIT") }

data class CategoryDraft(
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val sortOrder: String?
)
data class CategoryQuery(val page: Int, val pageSize: Int, val search: String?, val active: Boolean?)
data class PagedCategories(val categories: List<Category>, val page: Int, val pageSize: Int, val totalPages: Int, val totalElements: Long, val hasNextPage: Boolean)

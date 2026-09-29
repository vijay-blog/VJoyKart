package com.daily.nexamartpartner.features.admin.category.data.contract

import com.daily.nexamartpartner.features.admin.category.domain.model.CategoryAdminAction
import com.daily.nexamartpartner.features.admin.category.domain.model.CategoryDraft
import com.daily.nexamartpartner.features.admin.category.domain.model.CategoryQuery

interface CategoryManagementContract {
    val listPath: String?
    val detailsPathTemplate: String?
    val createPath: String?
    val updatePathTemplate: String?
    val actionPathTemplate: String?
    fun buildListQuery(query: CategoryQuery): Map<String, String>?
    fun buildDraftBody(draft: CategoryDraft): Map<String, String>?
    fun buildActionBody(action: CategoryAdminAction): Map<String, String>?
    fun resolvePath(template: String?, id: String): String?
}

class BackendCategoryManagementContract : CategoryManagementContract {
    override val listPath = "admin/catalog/categories"
    override val detailsPathTemplate = "admin/catalog/categories/{id}"
    override val createPath = "admin/catalog/categories"
    override val updatePathTemplate = "admin/catalog/categories/{id}"
    override val actionPathTemplate = "admin/catalog/categories/{id}/actions"

    override fun buildListQuery(query: CategoryQuery): Map<String, String> = buildMap {
        put("page", query.page.toString())
        put("pageSize", query.pageSize.toString())
        query.search?.takeIf { it.isNotBlank() }?.let { put("search", it) }
        query.active?.let { put("active", it.toString()) }
    }

    override fun buildDraftBody(draft: CategoryDraft): Map<String, String> = buildMap {
        put("name", draft.name)
        draft.description?.let { put("description", it) }
        draft.imageUrl?.let { put("imageUrl", it) }
        draft.sortOrder?.let { put("sortOrder", it) }
    }

    override fun buildActionBody(action: CategoryAdminAction) =
        mapOf("action" to action.backendValue)

    override fun resolvePath(template: String?, id: String): String? =
        template?.replace("{id}", id)
}

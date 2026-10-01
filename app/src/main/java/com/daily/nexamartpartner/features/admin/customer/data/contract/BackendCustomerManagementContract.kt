package com.daily.nexamartpartner.features.admin.customer.data.contract

import com.daily.nexamartpartner.features.admin.customer.domain.model.CustomerAdminAction
import com.daily.nexamartpartner.features.admin.customer.domain.model.CustomerQuery

/** `/api/v1/admin/customers` endpoints (AdminController). */
class BackendCustomerManagementContract : CustomerManagementContract {
    override val listPath: String = "admin/customers"
    override val detailsPathTemplate: String = "admin/customers/{id}"
    override val actionPathTemplate: String = "admin/customers/{id}/actions"

    override fun buildListQuery(query: CustomerQuery): Map<String, String> = buildMap {
        put("page", query.page.coerceAtLeast(0).toString())
        put("pageSize", query.pageSize.coerceIn(1, 100).toString())
        query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
    }

    override fun buildActionBody(action: CustomerAdminAction): Map<String, String> = mapOf("action" to action.backendValue)

    override fun resolvePath(template: String?, id: String): String? {
        val value = id.trim()
        if (template == null || value.isEmpty() || !value.all(Char::isDigit)) return null
        return template.replace("{id}", value)
    }
}
package com.daily.nexamartpartner.features.admin.data.contract

import com.daily.nexamartpartner.features.admin.domain.model.DeliveryPartnersQuery
import com.daily.nexamartpartner.features.admin.domain.model.PartnerAdminAction

/** `/api/v1/admin/delivery-partners` endpoints (AdminController). */
class BackendDeliveryPartnerContract : DeliveryPartnerContract {
    override val listPath: String = "admin/delivery-partners"
    override val detailsPathTemplate: String = "admin/delivery-partners/{id}"
    override val actionPathTemplate: String = "admin/delivery-partners/{id}/actions"

    override fun buildListQuery(query: DeliveryPartnersQuery): Map<String, String> = buildMap {
        put("page", query.page.coerceAtLeast(0).toString())
        put("pageSize", query.pageSize.coerceIn(1, 100).toString())
        query.searchText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
    }

    override fun buildActionBody(action: PartnerAdminAction, reason: String?): Map<String, String> = buildMap {
        put("action", action.backendValue)
        reason?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reason", it) }
    }

    override fun resolvePath(template: String?, partnerId: String): String? {
        val id = partnerId.trim()
        if (template == null || id.isEmpty() || !id.all(Char::isDigit)) return null
        return template.replace("{id}", id)
    }
}
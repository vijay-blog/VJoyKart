package com.daily.nexamartpartner.features.admin.data.contract

import com.daily.nexamartpartner.features.admin.domain.model.AdminOrdersQuery
import com.daily.nexamartpartner.features.admin.domain.model.OrderStatus

/** Contracts for the Spring Boot `/api/v1/admin` endpoints (AdminController). */
class BackendAdminDashboardContract : AdminDashboardContract {
    override val dashboardEndpointPath: String = "admin/dashboard"
}

class BackendAdminOrdersContract : AdminOrdersContract {
    override val listOrdersPath: String = "admin/orders"
    override val orderDetailsPathTemplate: String = "admin/orders/{orderId}"
    override val updateStatusPathTemplate: String = "admin/orders/{orderId}/status"
    override val cancelOrderPathTemplate: String = "admin/orders/{orderId}/cancel"
    override val assignDeliveryPathTemplate: String = "admin/orders/{orderId}/assign-delivery"

    override fun buildOrderListQuery(query: AdminOrdersQuery): Map<String, String> = buildMap {
        put("page", query.page.coerceAtLeast(0).toString())
        put("pageSize", query.pageSize.coerceIn(1, 100).toString())
        query.searchText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
        query.filters.status?.takeIf { it != OrderStatus.UNKNOWN }?.let { put("status", it.backendValue) }
    }

    override fun buildUpdateStatusBody(status: OrderStatus): Map<String, String> = mapOf("status" to status.backendValue)

    override fun buildCancelOrderBody(reason: String?): Map<String, String> =
        reason?.trim()?.takeIf { it.isNotEmpty() }?.let { mapOf("reason" to it) } ?: emptyMap()

    override fun resolvePath(template: String?, orderId: String): String? {
        val id = orderId.trim()
        if (template == null || id.isEmpty() || !id.all(Char::isDigit)) return null
        return template.replace("{orderId}", id)
    }
}

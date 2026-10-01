package com.daily.nexamartpartner.features.delivery.data.contract

import com.daily.nexamartpartner.features.delivery.availability.data.contract.DeliveryAvailabilityContract
import com.daily.nexamartpartner.features.delivery.availability.domain.model.DeliveryAvailabilityUpdate
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrdersQuery
import com.daily.nexamartpartner.features.delivery.earnings.data.contract.DeliveryEarningsContract
import com.daily.nexamartpartner.features.delivery.earnings.domain.model.DeliveryEarningsQuery
import com.daily.nexamartpartner.features.delivery.notifications.data.contract.DeliveryNotificationsContract
import com.daily.nexamartpartner.features.delivery.notifications.domain.model.DeliveryNotificationQuery
import com.daily.nexamartpartner.features.delivery.profile.data.contract.DeliveryPartnerProfileContract
import com.daily.nexamartpartner.features.delivery.profile.domain.model.DeliveryPartnerProfileUpdate

/** Contracts for the Spring Boot `/api/v1/delivery` endpoints (DeliveryController). */
class BackendDeliveryDashboardContract : DeliveryDashboardContract {
    override val dashboardPath: String = "delivery/dashboard"
}

class BackendDeliveryOrderWorkflowContract : DeliveryOrderWorkflowContract {
    override val listAssignedOrdersPath: String = "delivery/orders"
    override val listHistoryOrdersPath: String = "delivery/history"
    override val orderDetailsPathTemplate: String = "delivery/orders/{orderId}"
    override val actionPathTemplate: String = "delivery/orders/{orderId}/status"

    override fun buildListQuery(query: DeliveryOrdersQuery): Map<String, String> = buildMap {
        put("page", query.page.coerceAtLeast(0).toString())
        put("pageSize", query.pageSize.coerceIn(1, 100).toString())
        query.searchText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
    }

    override fun buildActionBody(action: DeliveryOrderAction): Map<String, String> = mapOf("status" to action.backendValue)

    override fun resolvePath(template: String?, orderId: String): String? {
        val id = orderId.trim()
        if (template == null || id.isEmpty() || !id.all(Char::isDigit)) return null
        return template.replace("{orderId}", id)
    }
}

class BackendDeliveryAvailabilityContract : DeliveryAvailabilityContract {
    override val getPath: String = "delivery/availability"
    override val updatePath: String = "delivery/availability"
    override fun buildUpdateBody(update: DeliveryAvailabilityUpdate): Any = mapOf("available" to update.available)
}

class BackendDeliveryNotificationsContract : DeliveryNotificationsContract {
    override val listPath: String = "delivery/notifications"
    override val markReadPath: String = "delivery/notifications/{id}/read"
    override val markAllReadPath: String = "delivery/notifications/read-all"
    override fun buildListQuery(q: DeliveryNotificationQuery): Map<String, String> = mapOf(
        "page" to q.page.coerceAtLeast(0).toString(),
        "pageSize" to q.pageSize.coerceIn(1, 100).toString(),
        "unreadOnly" to q.unreadOnly.toString()
    )
}

class BackendDeliveryPartnerProfileContract : DeliveryPartnerProfileContract {
    override val profilePath: String = "delivery/profile"
    override val updatePath: String = "delivery/profile"
    override fun buildUpdateBody(update: DeliveryPartnerProfileUpdate): Any = buildMap<String, String> {
        update.name?.trim()?.takeIf { it.isNotEmpty() }?.let { put("name", it) }
        update.email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        update.vehicleType?.trim()?.takeIf { it.isNotEmpty() }?.let { put("vehicleType", it) }
        update.vehicleNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { put("vehicleNumber", it) }
        update.licenseReference?.trim()?.takeIf { it.isNotEmpty() }?.let { put("licenseReference", it) }
    }
}

class BackendDeliveryEarningsContract : DeliveryEarningsContract {
    override val summaryPath: String = "delivery/earnings/summary"
    override val historyPath: String = "delivery/earnings/history"
    override fun buildSummaryQuery(query: DeliveryEarningsQuery): Map<String, String> = emptyMap()
    override fun buildHistoryQuery(query: DeliveryEarningsQuery): Map<String, String> = mapOf(
        "page" to query.page.coerceAtLeast(0).toString(),
        "pageSize" to query.pageSize.coerceIn(1, 100).toString()
    )
}

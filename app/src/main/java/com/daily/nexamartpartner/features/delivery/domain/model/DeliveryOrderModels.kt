package com.daily.nexamartpartner.features.delivery.domain.model

import java.math.BigDecimal

data class DeliveryOrdersQuery(
    val page: Int = 0,
    val pageSize: Int = 20,
    val searchText: String? = null,
    val status: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null
)

data class PagedDeliveryOrders(
    val orders: List<DeliveryOrderSummary>,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalElements: Long,
    val hasNextPage: Boolean
)

data class DeliveryOrderSummary(
    val orderId: String,
    val customerName: String,
    val customerPhone: String?,
    val address: String?,
    val totalAmount: BigDecimal?,
    val currencyCode: String?,
    val status: String,
    val paymentStatus: String?,
    val assignedAt: String?,
    val createdAt: String?,
    val statusLabel: String? = null,
    val pickupName: String = DEFAULT_PICKUP_NAME
)

data class DeliveryOrderDetails(
    val orderId: String,
    val customerName: String,
    val customerPhone: String?,
    val address: String?,
    val totalAmount: BigDecimal?,
    val currencyCode: String?,
    val status: String,
    val paymentStatus: String?,
    val items: List<DeliveryOrderItem>,
    val assignedAt: String?,
    val createdAt: String?,
    val timeline: List<DeliveryOrderTimeline>,
    val allowedActions: List<DeliveryOrderAction>,
    val proofOfDeliveryRequired: Boolean = false,
    val proofOfDeliveryStatus: String? = null,
    val proofOfDeliveryUrl: String? = null,
    val statusLabel: String? = null,
    val pickupName: String = DEFAULT_PICKUP_NAME,
    val pickupAddress: String? = null,
    val pickupMapsUrl: String? = null,
    val paymentMethod: String? = null
)

data class DeliveryOrderItem(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal?,
    val lineTotal: BigDecimal?
)

data class DeliveryOrderTimeline(val status: String, val timestamp: String?)

const val DEFAULT_PICKUP_NAME = "VJoyKart Store"

/**
 * Delivery progress actions. The backend returns only the single valid next step in
 * `allowedActions`, so exactly one of these buttons is enabled at a time:
 * DELIVERY_ASSIGNED -> PACKING -> ON_THE_WAY -> ARRIVED -> DELIVERED.
 */
enum class DeliveryOrderAction(val backendValue: String, val label: String) {
    PACKING("PACKING", "Start Packing"),
    ON_THE_WAY("ON_THE_WAY", "On the Way"),
    ARRIVED("ARRIVED", "Arrived"),
    DELIVERED("DELIVERED", "Delivered");

    companion object {
        fun fromBackend(value: String): DeliveryOrderAction? = entries.firstOrNull { it.backendValue.equals(value.trim(), true) }
    }
}

/** Human-readable delivery status shown in the partner app. */
fun deliveryStatusLabel(status: String?): String = when (status?.uppercase()) {
    "ORDER_PLACED" -> "Order placed"
    "DELIVERY_ASSIGNED" -> "Delivery boy assigned"
    "PACKING" -> "Packing"
    "ON_THE_WAY" -> "On the way"
    "ARRIVED" -> "Arrived"
    "DELIVERED" -> "Delivered"
    "CANCELLED" -> "Cancelled"
    null, "" -> "Status unavailable"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
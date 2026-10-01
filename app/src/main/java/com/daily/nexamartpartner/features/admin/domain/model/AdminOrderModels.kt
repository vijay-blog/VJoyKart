package com.daily.nexamartpartner.features.admin.domain.model

import java.math.BigDecimal

data class PagedAdminOrders(
    val orders: List<AdminOrderSummary>,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalElements: Long,
    val hasNextPage: Boolean
)

data class AdminOrderSummary(
    val orderId: String,
    val customerName: String,
    val customerPhone: String?,
    val itemCount: Int?,
    val totalAmount: BigDecimal?,
    val currencyCode: String?,
    val orderStatus: OrderStatus,
    val paymentStatus: PaymentStatus,
    val deliveryStatus: String?,
    val createdAt: String?
)

data class AdminOrderDetails(
    val orderId: String,
    val createdAt: String?,
    val currentStatus: OrderStatus,
    val customer: OrderCustomer,
    val items: List<OrderItem>,
    val payment: PaymentInfo?,
    val totals: OrderTotals?,
    val delivery: DeliveryInfo?,
    val timeline: List<OrderTimelineEntry>,
    val allowedTransitions: List<OrderStatus>,
    val canCancel: Boolean
)

data class OrderCustomer(
    val name: String,
    val phone: String?,
    val address: String?
)

data class OrderItem(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal?,
    val lineTotal: BigDecimal?,
    val currencyCode: String?
)

data class PaymentInfo(
    val method: PaymentMethod,
    val status: PaymentStatus,
    val transactionReference: String?
)

data class OrderTotals(
    val subtotal: BigDecimal?,
    val deliveryFee: BigDecimal?,
    val discount: BigDecimal?,
    val tax: BigDecimal?,
    val grandTotal: BigDecimal?,
    val currencyCode: String?
)

data class DeliveryInfo(
    val status: String?,
    val partnerName: String?,
    val assignedAt: String?,
    val partnerId: String? = null,
    val partnerPhone: String? = null,
    val assignmentStatus: String? = null
)

data class OrderTimelineEntry(
    val status: OrderStatus,
    val timestamp: String?
)

data class AdminOrderFilters(
    val status: OrderStatus? = null,
    val paymentStatus: PaymentStatus? = null
)

enum class AdminOrderSort(val backendValue: String) {
    NEWEST("NEWEST"),
    OLDEST("OLDEST"),
    AMOUNT_HIGH_TO_LOW("AMOUNT_HIGH_TO_LOW"),
    AMOUNT_LOW_TO_HIGH("AMOUNT_LOW_TO_HIGH")
}

data class AdminOrdersQuery(
    val page: Int,
    val pageSize: Int,
    val searchText: String?,
    val filters: AdminOrderFilters,
    val sort: AdminOrderSort
)

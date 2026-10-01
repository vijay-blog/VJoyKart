package com.daily.nexamartpartner.features.admin.data.model

import com.squareup.moshi.Json

data class AdminOrdersPageDto(
    @field:Json(name = "content") val content: List<AdminOrderSummaryDto>?,
    @field:Json(name = "number") val number: Int?,
    @field:Json(name = "size") val size: Int?,
    @field:Json(name = "totalPages") val totalPages: Int?,
    @field:Json(name = "totalElements") val totalElements: Long?,
    @field:Json(name = "last") val last: Boolean?
)

data class AdminOrderSummaryDto(
    @field:Json(name = "orderId") val orderId: String?,
    @field:Json(name = "customerName") val customerName: String?,
    @field:Json(name = "customerPhone") val customerPhone: String?,
    @field:Json(name = "itemCount") val itemCount: Int?,
    @field:Json(name = "totalAmount") val totalAmount: String?,
    @field:Json(name = "currencyCode") val currencyCode: String?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "paymentStatus") val paymentStatus: String?,
    @field:Json(name = "deliveryStatus") val deliveryStatus: String?,
    @field:Json(name = "createdAt") val createdAt: String?
)

data class AdminOrderDetailsDto(
    @field:Json(name = "orderId") val orderId: String?,
    @field:Json(name = "createdAt") val createdAt: String?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "customer") val customer: OrderCustomerDto?,
    @field:Json(name = "items") val items: List<OrderItemDto>?,
    @field:Json(name = "payment") val payment: PaymentInfoDto?,
    @field:Json(name = "totals") val totals: OrderTotalsDto?,
    @field:Json(name = "delivery") val delivery: DeliveryInfoDto?,
    @field:Json(name = "timeline") val timeline: List<OrderTimelineDto>?,
    @field:Json(name = "allowedTransitions") val allowedTransitions: List<String>?,
    @field:Json(name = "canCancel") val canCancel: Boolean?
)

data class OrderCustomerDto(
    @field:Json(name = "name") val name: String?,
    @field:Json(name = "phone") val phone: String?,
    @field:Json(name = "address") val address: String?
)

data class OrderItemDto(
    @field:Json(name = "productName") val productName: String?,
    @field:Json(name = "quantity") val quantity: Int?,
    @field:Json(name = "unitPrice") val unitPrice: String?,
    @field:Json(name = "lineTotal") val lineTotal: String?
)

data class PaymentInfoDto(
    @field:Json(name = "method") val method: String?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "transactionReference") val transactionReference: String?
)

data class OrderTotalsDto(
    @field:Json(name = "subtotal") val subtotal: String?,
    @field:Json(name = "deliveryFee") val deliveryFee: String?,
    @field:Json(name = "discount") val discount: String?,
    @field:Json(name = "tax") val tax: String?,
    @field:Json(name = "grandTotal") val grandTotal: String?,
    @field:Json(name = "currencyCode") val currencyCode: String?
)

data class DeliveryInfoDto(
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "partnerName") val partnerName: String?,
    @field:Json(name = "assignedAt") val assignedAt: String?,
    @field:Json(name = "partnerId") val partnerId: String? = null,
    @field:Json(name = "partnerPhone") val partnerPhone: String? = null,
    @field:Json(name = "assignmentStatus") val assignmentStatus: String? = null
)

data class OrderTimelineDto(
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "timestamp") val timestamp: String?
)

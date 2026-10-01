package com.daily.nexamartpartner.features.delivery.data.model

import com.squareup.moshi.Json

data class DeliveryOrdersPageDto(
    @field:Json(name="content") val content: List<DeliveryOrderSummaryDto>?,
    @field:Json(name="number") val number: Int?,
    @field:Json(name="size") val size: Int?,
    @field:Json(name="totalPages") val totalPages: Int?,
    @field:Json(name="totalElements") val totalElements: Long?,
    @field:Json(name="last") val last: Boolean?
)

data class DeliveryOrderSummaryDto(
    @field:Json(name="orderId") val orderId: String?,
    @field:Json(name="customerName") val customerName: String?,
    @field:Json(name="customerPhone") val customerPhone: String?,
    @field:Json(name="address") val address: String?,
    @field:Json(name="totalAmount") val totalAmount: String?,
    @field:Json(name="currencyCode") val currencyCode: String?,
    @field:Json(name="status") val status: String?,
    @field:Json(name="paymentStatus") val paymentStatus: String?,
    @field:Json(name="assignedAt") val assignedAt: String?,
    @field:Json(name="createdAt") val createdAt: String?,
    @field:Json(name="amount") val amount: String?,
    @field:Json(name="deliveryStatus") val deliveryStatus: String? = null,
    @field:Json(name="deliveryStatusLabel") val deliveryStatusLabel: String? = null,
    @field:Json(name="pickupName") val pickupName: String? = null,
    @field:Json(name="store") val store: DeliveryStoreDto? = null
)

data class DeliveryOrderDetailsDto(
    @field:Json(name="orderId") val orderId: String?,
    @field:Json(name="customerName") val customerName: String?,
    @field:Json(name="customerPhone") val customerPhone: String?,
    @field:Json(name="address") val address: String?,
    @field:Json(name="totalAmount") val totalAmount: String?,
    @field:Json(name="currencyCode") val currencyCode: String?,
    @field:Json(name="status") val status: String?,
    @field:Json(name="paymentStatus") val paymentStatus: String?,
    @field:Json(name="items") val items: List<DeliveryOrderItemDto>?,
    @field:Json(name="assignedAt") val assignedAt: String?,
    @field:Json(name="createdAt") val createdAt: String?,
    @field:Json(name="timeline") val timeline: List<DeliveryOrderTimelineDto>?,
    @field:Json(name="allowedActions") val allowedActions: List<String>?,
    @field:Json(name="proofOfDeliveryRequired") val proofOfDeliveryRequired: Boolean?,
    @field:Json(name="proofOfDeliveryStatus") val proofOfDeliveryStatus: String?,
    @field:Json(name="proofOfDeliveryUrl") val proofOfDeliveryUrl: String?,
    @field:Json(name="deliveryStatus") val deliveryStatus: String? = null,
    @field:Json(name="deliveryStatusLabel") val deliveryStatusLabel: String? = null,
    @field:Json(name="store") val store: DeliveryStoreDto? = null,
    @field:Json(name="payment") val payment: DeliveryPaymentDto? = null
)

/** Fixed pickup location (VJoyKart Store) returned by the backend. */
data class DeliveryStoreDto(
    @field:Json(name="name") val name: String?,
    @field:Json(name="latitude") val latitude: Double?,
    @field:Json(name="longitude") val longitude: Double?,
    @field:Json(name="mapsUrl") val mapsUrl: String?,
    @field:Json(name="address") val address: String?
)

data class DeliveryPaymentDto(@field:Json(name="method") val method: String?, @field:Json(name="status") val status: String?)

data class DeliveryOrderItemDto(
    @field:Json(name="productName") val productName: String?,
    @field:Json(name="quantity") val quantity: Int?,
    @field:Json(name="unitPrice") val unitPrice: String?,
    @field:Json(name="lineTotal") val lineTotal: String?
)

data class DeliveryOrderTimelineDto(@field:Json(name="status") val status: String?, @field:Json(name="timestamp") val timestamp: String?)

package com.daily.nexamartpartner.features.admin.data.model

import com.squareup.moshi.Json

data class AdminDashboardResponseDto(
    @field:Json(name = "totalOrders") val totalOrders: Long?,
    @field:Json(name = "todayOrders") val todayOrders: Long?,
    @field:Json(name = "pendingOrders") val pendingOrders: Long?,
    @field:Json(name = "outForDelivery") val outForDelivery: Long?,
    @field:Json(name = "deliveredToday") val deliveredToday: Long?,
    @field:Json(name = "todaySales") val todaySales: Double?,
    @field:Json(name = "currencyCode") val currencyCode: String?,
    @field:Json(name = "recentOrders") val recentOrders: List<AdminRecentOrderDto>?,
    @field:Json(name = "unreadNotifications") val unreadNotifications: Long? = 0L,
    @field:Json(name = "notifications") val notifications: List<AdminNotificationDto>? = emptyList()
)

data class AdminRecentOrderDto(
    @field:Json(name = "orderId") val orderId: String?,
    @field:Json(name = "customerName") val customerName: String?,
    @field:Json(name = "amount") val amount: Double?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "createdAt") val createdAt: String?
)


data class AdminNotificationDto(
    @field:Json(name = "id") val id: String?,
    @field:Json(name = "title") val title: String?,
    @field:Json(name = "message") val message: String?,
    @field:Json(name = "createdAt") val createdAt: String?,
    @field:Json(name = "read") val read: Boolean?,
    @field:Json(name = "orderId") val orderId: Long?
)

package com.daily.nexamartpartner.features.admin.domain.model

import java.math.BigDecimal

data class AdminDashboard(
    val kpis: DashboardKpis?,
    val recentOrders: List<RecentOrderSummary>,
    val recentOrdersAvailable: Boolean,
    val unreadNotifications: Long = 0L,
    val notifications: List<AdminNotification> = emptyList()
) {
    val hasData: Boolean
        get() = kpis != null || recentOrders.isNotEmpty()
}

data class DashboardKpis(
    val totalOrders: Long,
    val todayOrders: Long,
    val pendingOrders: Long,
    val outForDelivery: Long,
    val deliveredToday: Long,
    val todaySales: BigDecimal,
    val currencyCode: String?
)

data class RecentOrderSummary(
    val orderId: String,
    val customerName: String,
    val amount: BigDecimal?,
    val currencyCode: String?,
    val status: String,
    val createdAt: String?
)


data class AdminNotification(
    val id: String,
    val title: String,
    val message: String,
    val createdAt: String?,
    val read: Boolean,
    val orderId: Long?
)

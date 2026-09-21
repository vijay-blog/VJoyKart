package com.daily.nexamartpartner.features.admin.data.repository

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.model.AdminDashboardResponseDto
import com.daily.nexamartpartner.features.admin.domain.model.AdminDashboard
import com.daily.nexamartpartner.features.admin.domain.model.AdminNotification
import com.daily.nexamartpartner.features.admin.domain.model.DashboardKpis
import com.daily.nexamartpartner.features.admin.domain.model.RecentOrderSummary
import com.daily.nexamartpartner.features.admin.domain.repository.AdminDashboardRepository
import com.daily.nexamartpartner.features.admin.data.source.AdminDashboardRemoteDataSource
import java.math.BigDecimal

class AdminDashboardRepositoryImpl(
    private val remoteDataSource: AdminDashboardRemoteDataSource
) : AdminDashboardRepository {

    override suspend fun getDashboard(): AppResult<AdminDashboard> {
        return when (val result = remoteDataSource.getDashboard()) {
            is AppResult.Success -> mapDashboard(result.data)
            is AppResult.Failure -> result
        }
    }

    private fun mapDashboard(dto: AdminDashboardResponseDto): AppResult<AdminDashboard> {
        val hasKpiData = listOf(
            dto.totalOrders,
            dto.todayOrders,
            dto.pendingOrders,
            dto.outForDelivery,
            dto.deliveredToday,
            dto.todaySales
        ).any { it != null }

        val kpis = if (hasKpiData) {
            DashboardKpis(
                totalOrders = dto.totalOrders ?: 0L,
                todayOrders = dto.todayOrders ?: 0L,
                pendingOrders = dto.pendingOrders ?: 0L,
                outForDelivery = dto.outForDelivery ?: 0L,
                deliveredToday = dto.deliveredToday ?: 0L,
                todaySales = dto.todaySales?.toBigDecimal() ?: BigDecimal.ZERO,
                currencyCode = dto.currencyCode
            )
        } else {
            null
        }

        val mappedRecentOrders = dto.recentOrders?.map { order ->
            val orderId = order.orderId?.trim()
            val status = order.status?.trim()
            if (orderId.isNullOrEmpty() || status.isNullOrEmpty()) {
                return AppResult.Failure(
                    AppFailure(
                        message = "Invalid dashboard response from server.",
                        type = FailureType.SERVER
                    )
                )
            }
            RecentOrderSummary(
                orderId = orderId,
                customerName = order.customerName?.trim().orEmpty(),
                amount = order.amount?.toBigDecimal(),
                currencyCode = dto.currencyCode,
                status = status,
                createdAt = order.createdAt?.trim()
            )
        }.orEmpty()

        return AppResult.Success(
            AdminDashboard(
                kpis = kpis,
                recentOrders = mappedRecentOrders,
                recentOrdersAvailable = dto.recentOrders != null,
                unreadNotifications = dto.unreadNotifications ?: 0L,
                notifications = dto.notifications.orEmpty().mapNotNull { n ->
                    val id = n.id?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
                    AdminNotification(
                        id = id,
                        title = n.title?.trim().orEmpty(),
                        message = n.message?.trim().orEmpty(),
                        createdAt = n.createdAt?.trim(),
                        read = n.read == true,
                        orderId = n.orderId
                    )
                }
            )
        )
    }
}

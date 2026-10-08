package com.daily.nexamartpartner.features.admin.data.repository

import com.daily.nexamartpartner.core.result.AppFailure
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.admin.data.model.AdminOrderDetailsDto
import com.daily.nexamartpartner.features.admin.data.model.AdminOrderSummaryDto
import com.daily.nexamartpartner.features.admin.data.model.AdminOrdersPageDto
import com.daily.nexamartpartner.features.admin.data.model.OrderItemDto
import com.daily.nexamartpartner.features.admin.data.model.OrderTimelineDto
import com.daily.nexamartpartner.features.admin.data.source.AdminOrdersRemoteDataSource
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderDetails
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderFilters
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderSort
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderSummary
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrdersQuery
import com.daily.nexamartpartner.features.admin.domain.model.DeliveryInfo
import com.daily.nexamartpartner.features.admin.domain.model.OrderCustomer
import com.daily.nexamartpartner.features.admin.domain.model.OrderItem
import com.daily.nexamartpartner.features.admin.domain.model.OrderStatus
import com.daily.nexamartpartner.features.admin.domain.model.OrderTimelineEntry
import com.daily.nexamartpartner.features.admin.domain.model.OrderTotals
import com.daily.nexamartpartner.features.admin.domain.model.PagedAdminOrders
import com.daily.nexamartpartner.features.admin.domain.model.PaymentInfo
import com.daily.nexamartpartner.features.admin.domain.model.PaymentMethod
import com.daily.nexamartpartner.features.admin.domain.model.PaymentStatus
import com.daily.nexamartpartner.features.admin.domain.repository.AdminOrdersRepository
import java.math.BigDecimal

class AdminOrdersRepositoryImpl(
    private val remoteDataSource: AdminOrdersRemoteDataSource
) : AdminOrdersRepository {
    override suspend fun getOrders(query: AdminOrdersQuery): AppResult<PagedAdminOrders> {
        return when (val result = remoteDataSource.getOrders(query)) {
            is AppResult.Success -> mapOrderPage(result.data, query.page, query.pageSize)
            is AppResult.Failure -> result
        }
    }

    override suspend fun getOrderDetails(orderId: String): AppResult<AdminOrderDetails> {
        return when (val result = remoteDataSource.getOrderDetails(orderId)) {
            is AppResult.Success -> mapOrderDetails(result.data)
            is AppResult.Failure -> result
        }
    }

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): AppResult<Unit> {
        return remoteDataSource.updateOrderStatus(orderId, status)
    }

    override suspend fun cancelOrder(orderId: String, reason: String?): AppResult<Unit> {
        return remoteDataSource.cancelOrder(orderId, reason)
    }

    private fun mapOrderPage(
        dto: AdminOrdersPageDto,
        fallbackPage: Int,
        fallbackPageSize: Int
    ): AppResult<PagedAdminOrders> {
        val mappedOrders = dto.content?.map { summary ->
            mapOrderSummary(summary)
                ?: return AppResult.Failure(
                    AppFailure(
                        message = "Invalid order list response from server.",
                        type = FailureType.SERVER
                    )
                )
        }.orEmpty()

        val totalPages = dto.totalPages ?: 1
        val currentPage = dto.number ?: fallbackPage
        val hasNext = dto.last?.not() ?: (currentPage + 1 < totalPages)

        return AppResult.Success(
            PagedAdminOrders(
                orders = mappedOrders,
                page = currentPage,
                pageSize = dto.size ?: fallbackPageSize,
                totalPages = totalPages,
                totalElements = dto.totalElements ?: mappedOrders.size.toLong(),
                hasNextPage = hasNext
            )
        )
    }

    private fun mapOrderSummary(dto: AdminOrderSummaryDto): AdminOrderSummary? {
        val orderId = dto.orderId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val status = OrderStatus.fromRaw(dto.status)
        if (status == OrderStatus.UNKNOWN) return null

        return AdminOrderSummary(
            orderId = orderId,
            customerName = dto.customerName?.trim().orEmpty(),
            customerPhone = dto.customerPhone?.trim(),
            itemCount = dto.itemCount,
            totalAmount = dto.totalAmount?.toBigDecimalOrNull(),
            currencyCode = dto.currencyCode?.trim(),
            orderStatus = status,
            paymentStatus = PaymentStatus.fromRaw(dto.paymentStatus),
            deliveryStatus = dto.deliveryStatus?.trim(),
            createdAt = dto.createdAt?.trim()
        )
    }

    private fun mapOrderDetails(dto: AdminOrderDetailsDto): AppResult<AdminOrderDetails> {
        val orderId = dto.orderId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return AppResult.Failure(
                AppFailure(
                    message = "Invalid order details response from server.",
                    type = FailureType.SERVER
                )
            )
        val status = OrderStatus.fromRaw(dto.status)
        if (status == OrderStatus.UNKNOWN) {
            return AppResult.Failure(
                AppFailure(
                    message = "Invalid order details response from server.",
                    type = FailureType.SERVER
                )
            )
        }

        val customer = OrderCustomer(
            name = dto.customer?.name?.trim().orEmpty(),
            phone = dto.customer?.phone?.trim(),
            address = dto.customer?.address?.trim()
        )

        val items = dto.items?.map { mapOrderItem(it, dto.totals?.currencyCode) }.orEmpty()
        val timeline = mapTimeline(dto.timeline, status)
        val allowedTransitions = dto.allowedTransitions
            ?.map { OrderStatus.fromRaw(it) }
            ?.filter { it != OrderStatus.UNKNOWN }
            .orEmpty()

        val paymentInfo = dto.payment?.let {
            PaymentInfo(
                method = PaymentMethod.fromRaw(it.method),
                status = PaymentStatus.fromRaw(it.status),
                transactionReference = it.transactionReference?.trim()
            )
        }

        val totals = dto.totals?.let {
            OrderTotals(
                subtotal = parseAmount(it.subtotal),
                deliveryFee = parseAmount(it.deliveryFee),
                discount = parseAmount(it.discount),
                tax = parseAmount(it.tax),
                grandTotal = parseAmount(it.grandTotal),
                currencyCode = it.currencyCode?.trim()
            )
        }

        val delivery = dto.delivery?.let {
            DeliveryInfo(
                status = it.status?.trim(),
                partnerName = it.partnerName?.trim(),
                assignedAt = it.assignedAt?.trim(),
                partnerId = it.partnerId?.trim(),
                partnerPhone = it.partnerPhone?.trim()?.takeIf { p -> p.isNotEmpty() },
                assignmentStatus = it.assignmentStatus?.trim()
            )
        }

        return AppResult.Success(
            AdminOrderDetails(
                orderId = orderId,
                createdAt = dto.createdAt?.trim(),
                currentStatus = status,
                customer = customer,
                items = items,
                payment = paymentInfo,
                totals = totals,
                delivery = delivery,
                timeline = timeline,
                allowedTransitions = allowedTransitions,
                canCancel = dto.canCancel == true
            )
        )
    }

    private fun mapOrderItem(dto: OrderItemDto, currencyCode: String?): OrderItem {
        return OrderItem(
            productName = dto.productName?.trim().orEmpty(),
            quantity = dto.quantity ?: 0,
            unitPrice = parseAmount(dto.unitPrice),
            lineTotal = parseAmount(dto.lineTotal),
            currencyCode = currencyCode
        )
    }

    private fun mapTimeline(rawTimeline: List<OrderTimelineDto>?, currentStatus: OrderStatus): List<OrderTimelineEntry> {
        if (rawTimeline != null) {
            return rawTimeline.mapNotNull { timeline ->
                val status = OrderStatus.fromRaw(timeline.status)
                if (status == OrderStatus.UNKNOWN) return@mapNotNull null
                OrderTimelineEntry(status = status, timestamp = timeline.timestamp?.trim())
            }
        }
        return OrderStatus.entries
            .filter { it != OrderStatus.UNKNOWN }
            .map { status ->
                val completed = status.ordinal <= currentStatus.ordinal
                OrderTimelineEntry(status = status, timestamp = if (completed) null else null)
            }
    }

    private fun parseAmount(raw: String?): BigDecimal? {
        return raw?.trim()?.takeUnless { it.isEmpty() }?.toBigDecimalOrNull()
    }
}

fun defaultAdminOrdersQuery(): AdminOrdersQuery {
    return AdminOrdersQuery(
        page = 0,
        pageSize = 20,
        searchText = null,
        filters = AdminOrderFilters(),
        sort = AdminOrderSort.NEWEST
    )
}

package com.nexamart.customer.model

import com.nexamart.customer.util.IsoDates
import com.nexamart.customer.util.JsonMap
import com.nexamart.customer.util.asJsonList
import com.nexamart.customer.util.asJsonMap
import com.nexamart.customer.util.jsonDouble
import com.nexamart.customer.util.jsonDoubleOrNull
import com.nexamart.customer.util.jsonInt
import com.nexamart.customer.util.jsonString

/** Port of lib/models/order.dart. Enum constant names match the Dart enum names (persisted in the cache). */
enum class OrderStatus(val label: String) {
    created("Finding a delivery partner"),
    paymentPending("Awaiting payment"),
    partnerSearching("Finding a delivery partner"),
    partnerAssigned("Finding a delivery partner"),
    partnerAccepted("Finding a delivery partner"),
    picking("Packing"),
    packed("Packed"),
    deliverySearching("Finding delivery partner"),
    deliveryAssigned("Delivery boy assigned"),
    pickedUp("Picked up"),
    outForDelivery("On the way"),
    arrived("Arrived"),
    delivered("Delivered"),
    cancelled("Cancelled"),
    outOfStock("Out of stock"),
    deliveryFailed("Delivery failed"),
    returnRequested("Return requested"),
    returned("Returned"),
}

val deliveryStepOrder = listOf("DELIVERY_ASSIGNED", "PACKING", "ON_THE_WAY", "ARRIVED", "DELIVERED")

val deliveryStepLabels = mapOf(
    "DELIVERY_ASSIGNED" to "Delivery boy assigned",
    "PACKING" to "Packing",
    "ON_THE_WAY" to "On the way",
    "ARRIVED" to "Arrived",
    "DELIVERED" to "Delivered",
)

/** Delivery boy contact exposed by the backend (name + phone only). */
data class DeliveryPartnerInfo(val id: String, val name: String, val phone: String) {
    fun toJson(): JsonMap = linkedMapOf("id" to id, "name" to name, "phone" to phone)

    companion object {
        fun fromJson(raw: Any?): DeliveryPartnerInfo? {
            val json = raw.asJsonMap() ?: return null
            val name = json["name"].jsonString().orEmpty()
            if (name.isEmpty() && json["id"] == null) return null
            return DeliveryPartnerInfo(
                id = json["id"].jsonString().orEmpty(),
                name = name,
                phone = json["phone"].jsonString().orEmpty(),
            )
        }
    }
}

/** The fixed VJoyKart Store (pickup) location, owned by the backend. */
data class StoreInfo(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val mapsUrl: String,
    val address: String? = null,
) {
    fun toJson(): JsonMap = linkedMapOf(
        "name" to name,
        "latitude" to latitude,
        "longitude" to longitude,
        "mapsUrl" to mapsUrl,
        "address" to address,
    )

    companion object {
        fun fromJson(raw: Any?): StoreInfo? {
            val json = raw.asJsonMap() ?: return null
            val lat = json["latitude"].jsonDoubleOrNull() ?: return null
            val lng = json["longitude"].jsonDoubleOrNull() ?: return null
            return StoreInfo(
                name = json["name"].jsonString() ?: "VJoyKart Store",
                latitude = lat,
                longitude = lng,
                mapsUrl = json["mapsUrl"].jsonString().orEmpty(),
                address = json["address"].jsonString(),
            )
        }
    }
}

data class DeliveryStep(
    val status: String,
    val label: String,
    val state: String,
    /** Epoch millis. */
    val at: Long? = null,
) {
    val completed: Boolean get() = state == "COMPLETED"
    val current: Boolean get() = state == "CURRENT"

    fun toJson(): JsonMap = linkedMapOf(
        "status" to status,
        "label" to label,
        "state" to state,
        "timestamp" to at?.let { IsoDates.formatUtc(it) },
    )

    companion object {
        fun fromJson(json: JsonMap): DeliveryStep = DeliveryStep(
            status = json["status"].jsonString().orEmpty(),
            label = json["label"].jsonString() ?: deliveryStepLabels[json["status"]] ?: "",
            state = json["state"].jsonString() ?: "PENDING",
            at = IsoDates.parse(json["timestamp"].jsonString()),
        )

        /** Builds the five steps locally from a delivery status (used for cached orders). */
        fun fromStatus(deliveryStatus: String): List<DeliveryStep> {
            val current = deliveryStepOrder.indexOf(deliveryStatus)
            return deliveryStepOrder.mapIndexed { i, step ->
                DeliveryStep(
                    status = step,
                    label = deliveryStepLabels.getValue(step),
                    state = when {
                        current < 0 -> "PENDING"
                        i < current || (i == current && step == "DELIVERED") -> "COMPLETED"
                        i == current -> "CURRENT"
                        else -> "PENDING"
                    },
                )
            }
        }
    }
}

/** Maps the backend delivery status to the app's order status enum. */
fun orderStatusFromDelivery(deliveryStatus: String?, legacy: String?): OrderStatus = when (deliveryStatus) {
    "ORDER_PLACED" -> OrderStatus.created
    "DELIVERY_ASSIGNED" -> OrderStatus.deliveryAssigned
    "PACKING" -> OrderStatus.picking
    "ON_THE_WAY" -> OrderStatus.outForDelivery
    "ARRIVED" -> OrderStatus.arrived
    "DELIVERED" -> OrderStatus.delivered
    "CANCELLED" -> OrderStatus.cancelled
    else -> parseOrderStatus(legacy)
}

fun parseOrderStatus(value: String?): OrderStatus {
    val normalized = value.orEmpty().lowercase().replace("_", "")
    OrderStatus.entries.firstOrNull { it.name.lowercase() == normalized }?.let { return it }
    if (normalized == "placed") return OrderStatus.created
    if (normalized == "packing") return OrderStatus.picking
    return OrderStatus.created
}

data class CustomerOrder(
    val id: String,
    val orderNumber: String = id,
    /** Epoch millis. */
    val createdAt: Long,
    val items: List<CartItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val discount: Double,
    val total: Double,
    val address: String,
    val paymentMethod: String,
    val paymentStatus: String = "CREATED",
    val status: OrderStatus = OrderStatus.created,
    val deliveryStatus: String = "ORDER_PLACED",
    val assignmentStatus: String = "AWAITING_ASSIGNMENT",
    val assignedAt: Long? = null,
    val deliveryPartner: DeliveryPartnerInfo? = null,
    val store: StoreInfo? = null,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val progress: List<DeliveryStep> = DeliveryStep.fromStatus(deliveryStatus),
) {
    val isFinished: Boolean
        get() = deliveryStatus == "DELIVERED" ||
            deliveryStatus == "CANCELLED" ||
            status == OrderStatus.delivered ||
            status == OrderStatus.cancelled ||
            status == OrderStatus.returned ||
            status == OrderStatus.deliveryFailed

    val hasDeliveryPartner: Boolean
        get() = deliveryPartner != null && assignmentStatus != "AWAITING_ASSIGNMENT"

    /** 0..1 progress across the five delivery steps. */
    val deliveryFraction: Double
        get() {
            val done = progress.count { it.completed }
            val current = if (progress.any { it.current }) 0.5 else 0.0
            return ((done + current) / deliveryStepOrder.size).coerceIn(0.05, 1.0)
        }

    val statusLabel: String
        get() {
            if (deliveryStatus == "CANCELLED") return "Cancelled"
            if (deliveryStatus == "ORDER_PLACED" || !hasDeliveryPartner) {
                return if (paymentMethod == "ONLINE" && paymentStatus != "PAID") {
                    "Awaiting payment"
                } else {
                    "Finding a delivery partner"
                }
            }
            return deliveryStepLabels[deliveryStatus] ?: status.label
        }

    /** Applies a `/customer/orders/{id}/tracking` (or full order) response. */
    fun withTracking(json: JsonMap): CustomerOrder {
        val newDeliveryStatus = json["deliveryStatus"].jsonString() ?: deliveryStatus
        val loc = json["deliveryLocation"].asJsonMap()
        val steps = json["progress"] ?: json["deliveryProgress"]
        return copy(
            deliveryStatus = newDeliveryStatus,
            status = orderStatusFromDelivery(
                newDeliveryStatus,
                json["orderStatus"].jsonString() ?: json["status"].jsonString(),
            ),
            assignmentStatus = json["assignmentStatus"].jsonString() ?: assignmentStatus,
            paymentStatus = json["paymentStatus"].jsonString() ?: paymentStatus,
            assignedAt = IsoDates.parse(json["assignedAt"].jsonString()) ?: assignedAt,
            deliveryPartner = DeliveryPartnerInfo.fromJson(json["deliveryPartner"]),
            store = StoreInfo.fromJson(json["store"]) ?: store,
            deliveryLatitude = loc?.get("latitude").jsonDoubleOrNull() ?: deliveryLatitude,
            deliveryLongitude = loc?.get("longitude").jsonDoubleOrNull() ?: deliveryLongitude,
            progress = if (steps is List<*>) {
                steps.mapNotNull { it.asJsonMap() }.map { DeliveryStep.fromJson(it) }
            } else {
                DeliveryStep.fromStatus(newDeliveryStatus)
            },
        )
    }

    fun toJson(): JsonMap = linkedMapOf(
        "id" to id,
        "orderNumber" to orderNumber,
        "createdAt" to IsoDates.formatUtc(createdAt),
        "items" to items.map { it.toJson() },
        "subtotal" to subtotal,
        "deliveryFee" to deliveryFee,
        "discount" to discount,
        "total" to total,
        "address" to address,
        "paymentMethod" to paymentMethod,
        "paymentStatus" to paymentStatus,
        "status" to status.name,
        "deliveryStatus" to deliveryStatus,
        "assignmentStatus" to assignmentStatus,
        "assignedAt" to assignedAt?.let { IsoDates.formatUtc(it) },
        "deliveryPartner" to deliveryPartner?.toJson(),
        "store" to store?.toJson(),
        "deliveryLocation" to if (deliveryLatitude == null || deliveryLongitude == null) {
            null
        } else {
            linkedMapOf("latitude" to deliveryLatitude, "longitude" to deliveryLongitude)
        },
        "progress" to progress.map { it.toJson() },
    )

    companion object {
        fun fromJson(json: JsonMap, now: () -> Long = System::currentTimeMillis): CustomerOrder {
            val totals = json["totals"].asJsonMap() ?: emptyMap()
            val payment = json["payment"].asJsonMap() ?: emptyMap()
            val itemList = (json["items"].asJsonList() ?: emptyList()).map { raw ->
                val item = raw.asJsonMap() ?: throw IllegalArgumentException("Invalid order item")
                if (item["product"] is Map<*, *>) {
                    CartItem.fromJson(item)
                } else {
                    CartItem(
                        product = Product.fromJson(
                            linkedMapOf(
                                "id" to item["productId"],
                                "sku" to item["productSku"],
                                "name" to item["productName"],
                                "brand" to item["productBrand"],
                                "imageUrl" to item["productImageUrl"],
                                "unit" to item["productUnit"],
                                "sellingPrice" to item["unitPrice"],
                                "mrp" to item["unitPrice"],
                                "available" to true,
                                "stockQuantity" to 1,
                            ),
                        ),
                        quantity = (item["quantity"] ?: 1).jsonInt(),
                    )
                }
            }
            val id = json["id"].jsonString() ?: json["orderId"].jsonString() ?: ""
            val createdRaw = json["createdAt"]
            val createdAt = if (createdRaw != null) {
                IsoDates.parse(createdRaw.jsonString())
                    ?: throw IllegalArgumentException("Invalid createdAt: $createdRaw")
            } else {
                now()
            }
            val order = CustomerOrder(
                id = id,
                orderNumber = json["orderNumber"].jsonString() ?: json["orderId"].jsonString() ?: id,
                createdAt = createdAt,
                items = itemList,
                subtotal = (json["subtotal"] ?: totals["subtotal"]).jsonDouble(),
                deliveryFee = (json["deliveryFee"] ?: totals["deliveryFee"]).jsonDouble(),
                discount = (json["discountAmount"] ?: json["discount"] ?: totals["discount"]).jsonDouble(),
                total = (json["totalAmount"] ?: json["total"] ?: totals["grandTotal"]).jsonDouble(),
                address = (json["addressSnapshot"] ?: json["address"]).jsonString().orEmpty(),
                paymentMethod = (json["paymentMethod"] ?: payment["method"]).jsonString() ?: "COD",
                paymentStatus = (json["paymentStatus"] ?: payment["status"]).jsonString() ?: "CREATED",
                status = parseOrderStatus(json["status"].jsonString()),
            )
            return if (json["deliveryStatus"] != null) order.withTracking(json) else order
        }
    }
}

/** Port of lib/models/payment.dart. */
data class PaymentOrder(
    val paymentId: Int,
    val orderId: Int,
    val keyId: String,
    val gatewayOrderId: String,
    val amount: Double,
    val currency: String,
) {
    companion object {
        fun fromJson(json: JsonMap): PaymentOrder {
            fun requiredNumber(key: String): Number =
                json[key] as? Number ?: throw IllegalArgumentException("Missing $key")
            return PaymentOrder(
                paymentId = requiredNumber("paymentId").toInt(),
                orderId = requiredNumber("orderId").toInt(),
                keyId = json["keyId"].jsonString() ?: "null",
                gatewayOrderId = json["gatewayOrderId"].jsonString() ?: "null",
                amount = requiredNumber("amount").toDouble(),
                currency = json["currency"].jsonString() ?: "INR",
            )
        }
    }
}

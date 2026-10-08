package com.nexamart.customer

import com.nexamart.customer.model.CustomerOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of app/test/delivery_tracking_test.dart. */
class DeliveryTrackingTest {
    private fun tracking(status: String, partner: Boolean = true): Map<String, Any?> = mapOf(
        "orderId" to "18",
        "orderStatus" to "ASSIGNED",
        "paymentMethod" to "COD",
        "paymentStatus" to "PENDING",
        "totalAmount" to "210.00",
        "deliveryStatus" to status,
        "assignmentStatus" to if (partner) "ASSIGNED" else "AWAITING_ASSIGNMENT",
        "assignedAt" to if (partner) "2026-01-01T10:00:00Z" else null,
        "deliveryPartner" to if (partner) mapOf("id" to "7", "name" to "Ravi Kumar", "phone" to "9876543210") else null,
        "store" to mapOf(
            "name" to "VJoyKart Store",
            "latitude" to 17.3899091,
            "longitude" to 78.383089,
            "mapsUrl" to "https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9",
        ),
        "deliveryLocation" to mapOf("latitude" to 17.40, "longitude" to 78.40),
    )

    private fun order() = CustomerOrder(
        id = "18",
        createdAt = 0L,
        items = emptyList(),
        subtotal = 180.0,
        deliveryFee = 30.0,
        discount = 0.0,
        total = 210.0,
        address = "Customer street",
        paymentMethod = "COD",
    )

    @Test
    fun trackingShowsDeliveryBoyAndStore() {
        val o = order().withTracking(tracking("DELIVERY_ASSIGNED"))
        assertTrue(o.hasDeliveryPartner)
        assertEquals("Ravi Kumar", o.deliveryPartner!!.name)
        assertEquals("9876543210", o.deliveryPartner!!.phone)
        assertEquals("VJoyKart Store", o.store!!.name)
        assertEquals("https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9", o.store!!.mapsUrl)
        assertNotEquals(o.store!!.latitude, o.deliveryLatitude)
        assertEquals("Delivery boy assigned", o.statusLabel)
    }

    @Test
    fun progressIsExactlyFiveDeliverySteps() {
        val o = order().withTracking(tracking("ON_THE_WAY"))
        assertEquals(
            listOf("Delivery boy assigned", "Packing", "On the way", "Arrived", "Delivered"),
            o.progress.map { it.label },
        )
        assertEquals(
            listOf("COMPLETED", "COMPLETED", "CURRENT", "PENDING", "PENDING"),
            o.progress.map { it.state },
        )
    }

    @Test
    fun noDeliveryBoyWhileAwaitingAssignment() {
        val o = order().withTracking(tracking("ORDER_PLACED", partner = false))
        assertFalse(o.hasDeliveryPartner)
        assertEquals("Finding a delivery partner", o.statusLabel)
        assertTrue(o.progress.all { it.state == "PENDING" })
    }

    @Test
    fun deliveredOrdersAreFinishedAndSurviveLocalCache() {
        val o = order().withTracking(tracking("DELIVERED"))
        assertTrue(o.isFinished)
        val cached = CustomerOrder.fromJson(o.toJson())
        assertEquals("DELIVERED", cached.deliveryStatus)
        assertEquals("9876543210", cached.deliveryPartner!!.phone)
        assertTrue(cached.progress.all { it.completed })
    }
}

package com.daily.nexamartpartner

import com.daily.nexamartpartner.features.admin.data.contract.BackendAdminOrdersContract
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderFilters
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderSort
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrdersQuery
import com.daily.nexamartpartner.features.auth.data.contract.RegistrationRequestContract
import com.daily.nexamartpartner.features.auth.domain.model.RegistrationCredentials
import com.daily.nexamartpartner.features.delivery.availability.domain.model.DeliveryAvailabilityUpdate
import com.daily.nexamartpartner.features.delivery.data.contract.BackendDeliveryAvailabilityContract
import com.daily.nexamartpartner.features.delivery.data.contract.BackendDeliveryNotificationsContract
import com.daily.nexamartpartner.features.delivery.data.contract.BackendDeliveryOrderWorkflowContract
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrdersQuery
import com.daily.nexamartpartner.features.delivery.domain.model.deliveryStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryBackendContractTest {
    private val workflow = BackendDeliveryOrderWorkflowContract()

    @Test
    fun workflowPathsTargetDeliveryController() {
        assertEquals("delivery/orders", workflow.listAssignedOrdersPath)
        assertEquals("delivery/history", workflow.listHistoryOrdersPath)
        assertEquals("delivery/orders/42", workflow.resolvePath(workflow.orderDetailsPathTemplate, "42"))
        assertEquals("delivery/orders/42/status", workflow.resolvePath(workflow.actionPathTemplate, " 42 "))
    }

    @Test
    fun rejectsNonNumericOrderIds() {
        assertNull(workflow.resolvePath(workflow.actionPathTemplate, "../admin/orders/1"))
        assertNull(workflow.resolvePath(workflow.actionPathTemplate, ""))
    }

    @Test
    fun actionBodyCarriesBackendDeliveryStatus() {
        assertEquals(mapOf("status" to "PACKING"), workflow.buildActionBody(DeliveryOrderAction.PACKING))
        assertEquals(mapOf("status" to "ON_THE_WAY"), workflow.buildActionBody(DeliveryOrderAction.ON_THE_WAY))
        assertEquals(mapOf("status" to "ARRIVED"), workflow.buildActionBody(DeliveryOrderAction.ARRIVED))
        assertEquals(mapOf("status" to "DELIVERED"), workflow.buildActionBody(DeliveryOrderAction.DELIVERED))
    }

    @Test
    fun actionLabelsMatchDeliveryFlow() {
        assertEquals(listOf("Start Packing", "On the Way", "Arrived", "Delivered"), DeliveryOrderAction.entries.map { it.label })
        assertEquals(DeliveryOrderAction.ON_THE_WAY, DeliveryOrderAction.fromBackend("on_the_way"))
        assertNull(DeliveryOrderAction.fromBackend("PICKED_UP"))
    }

    @Test
    fun statusLabelsUseFiveStepFlow() {
        assertEquals("Delivery boy assigned", deliveryStatusLabel("DELIVERY_ASSIGNED"))
        assertEquals("Packing", deliveryStatusLabel("PACKING"))
        assertEquals("On the way", deliveryStatusLabel("ON_THE_WAY"))
        assertEquals("Arrived", deliveryStatusLabel("ARRIVED"))
        assertEquals("Delivered", deliveryStatusLabel("DELIVERED"))
    }

    @Test
    fun listQueryUsesBackendParameterNames() {
        val query = workflow.buildListQuery(DeliveryOrdersQuery(page = 1, pageSize = 500, searchText = " 18 "))
        assertEquals(mapOf("page" to "1", "pageSize" to "100", "q" to "18"), query)
    }

    @Test
    fun availabilityAndNotificationContracts() {
        assertEquals(mapOf("available" to true), BackendDeliveryAvailabilityContract().buildUpdateBody(DeliveryAvailabilityUpdate(true)))
        val notifications = BackendDeliveryNotificationsContract()
        assertEquals("delivery/notifications/7/read", notifications.buildMarkReadPath("7"))
    }

    @Test
    fun registrationSendsNormalizedPhone() {
        val body = RegistrationRequestContract().buildBody(RegistrationCredentials("Ravi", "Ravi@X.com", "secret123", "+91 98765-43210"))
        assertEquals("+919876543210", body["phone"])
        assertEquals("ravi@x.com", body["email"])
    }

    @Test
    fun adminOrdersContractTargetsAdminController() {
        val admin = BackendAdminOrdersContract()
        assertEquals("admin/orders/9/assign-delivery", admin.resolvePath(admin.assignDeliveryPathTemplate, "9"))
        val query = admin.buildOrderListQuery(AdminOrdersQuery(0, 20, null, AdminOrderFilters(), AdminOrderSort.entries.first()))
        assertEquals(mapOf("page" to "0", "pageSize" to "20"), query)
    }
}

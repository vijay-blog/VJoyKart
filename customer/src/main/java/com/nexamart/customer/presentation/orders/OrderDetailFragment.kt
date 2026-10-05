package com.nexamart.customer.presentation.orders

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentOrderDetailBinding
import com.nexamart.customer.databinding.ItemCheckoutLineBinding
import com.nexamart.customer.model.CustomerOrder
import com.nexamart.customer.model.DeliveryStep
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.dial
import com.nexamart.customer.presentation.common.dp
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.loadCatalogImage
import com.nexamart.customer.presentation.common.openExternal
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.util.Formats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Port of OrderDetailScreen with 10 s live tracking polling while visible. */
class OrderDetailFragment : Fragment() {
    private var _binding: FragmentOrderDetailBinding? = null
    private val binding get() = _binding!!
    private val offline = MutableStateFlow(false)
    private var refreshing = false
    private var closed = false
    private lateinit var orderId: String

    private val primary by lazy { ContextCompat.getColor(requireContext(), R.color.vk_primary) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        orderId = requireArguments().getString(Nav.ARG_ORDER_ID).orEmpty()
        val orders = requireContext().appContainer.orders
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.swipeRefresh.setColorSchemeResources(R.color.vk_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                refresh()
                _binding?.swipeRefresh?.isRefreshing = false
            }
        }
        binding.storeLabel.compoundDrawableTintList = ColorStateList.valueOf(0xFFFF5722.toInt())
        binding.homeLabel.compoundDrawableTintList = ColorStateList.valueOf(primary)
        binding.subtotalRow.label.setText(R.string.subtotal)
        binding.deliveryRow.label.setText(R.string.delivery)
        binding.discountRow.label.setText(R.string.discount)
        binding.totalRow.label.setText(R.string.total)
        binding.totalRow.label.setTypeface(null, Typeface.BOLD)
        binding.totalRow.value.setTypeface(null, Typeface.BOLD)

        launchOnStarted {
            launch {
                combine(orders.state, offline) { s, o -> s to o }.collect { (state, isOffline) ->
                    val order = orders.orderById(orderId)
                    if (order == null) {
                        if (state.initialized && !state.refreshing && !closed) {
                            closed = true
                            findNavController().navigateUp()
                        }
                        return@collect
                    }
                    bind(order, isOffline)
                }
            }
            // Polling (also restarts on every return to the foreground, like didChangeAppLifecycleState).
            while (true) {
                refresh()
                val order = orders.orderById(orderId)
                if (order == null || order.isFinished) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        if (refreshing) return
        val orders = requireContext().appContainer.orders
        val order = orders.orderById(orderId) ?: return
        refreshing = true
        try {
            orders.refreshTracking(order)
            offline.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            offline.value = true
        } finally {
            refreshing = false
        }
    }

    private fun bind(order: CustomerOrder, isOffline: Boolean) {
        val b = binding
        b.toolbar.title = "Order #${order.orderNumber}"
        b.summaryTitle.text = "Order #${order.orderNumber}"
        b.summaryTotal.text = Formats.rupees(order.total)
        b.summaryInfo.removeAllViews()
        addInfo("Order date", Formats.orderDateTimeComma(order.createdAt))
        addInfo("Payment", if (order.paymentMethod == "ONLINE") "Online Payment" else "Cash on Delivery")
        addInfo("Payment status", order.paymentStatus)

        bindTrackingHeader(order, isOffline)

        val storeName = order.store?.name ?: "VJoyKart Store"
        val moving = order.deliveryStatus in setOf("ON_THE_WAY", "ARRIVED", "DELIVERED")
        b.map.active = moving
        b.rider.visibleIf(moving)
        b.storeLabel.text = "$storeName • Pickup"
        b.storeInMaps.setOnClickListener { openStoreInMaps(order) }
        b.myAddressInMaps.setOnClickListener { openDeliveryInMaps(order) }

        bindPartner(order)
        bindTimeline(order)

        b.itemsContainer.removeAllViews()
        order.items.forEach { item ->
            val line = ItemCheckoutLineBinding.inflate(layoutInflater, b.itemsContainer, false)
            line.image.layoutParams = line.image.layoutParams.apply { width = 48.dp; height = 48.dp }
            line.image.loadCatalogImage(item.product.imageAsset)
            line.name.text = item.product.name
            line.name.setTypeface(null, Typeface.BOLD)
            line.detail.text = "${item.product.unit} × ${item.quantity}"
            line.total.text = Formats.rupees(item.total)
            b.itemsContainer.addView(line.root)
        }
        b.subtotalRow.value.text = Formats.rupees(order.subtotal)
        b.deliveryRow.value.text = Formats.rupees(order.deliveryFee)
        b.discountRow.value.text = Formats.rupees(order.discount)
        b.totalRow.value.text = Formats.rupees(order.total)

        b.pickupName.text = "Pickup: $storeName"
        val storeAddress = order.store?.address.orEmpty()
        b.pickupAddress.visibleIf(storeAddress.isNotEmpty())
        b.pickupAddress.text = storeAddress
        b.deliveryAddress.text = order.address
    }

    private fun bindTrackingHeader(order: CustomerOrder, isOffline: Boolean) {
        val cancelled = order.deliveryStatus == "CANCELLED"
        val delivered = order.deliveryStatus == "DELIVERED"
        val moving = order.deliveryStatus == "ON_THE_WAY" || order.deliveryStatus == "ARRIVED"
        val color = when {
            cancelled -> 0xFFD32F2F.toInt()
            delivered || moving -> 0xFF388E3C.toInt()
            else -> 0xFFEF6C00.toInt()
        }
        binding.trackingIconBox.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color and 0x00FFFFFF) or 0x1F000000)
        }
        binding.trackingIcon.setImageResource(
            when {
                cancelled -> R.drawable.ic_cancel_outline
                delivered -> R.drawable.ic_check_circle
                moving -> R.drawable.ic_navigation_round
                else -> R.drawable.ic_route_round
            },
        )
        binding.trackingIcon.imageTintList = ColorStateList.valueOf(color)
        binding.trackingSubtitle.text = when {
            cancelled -> "This order was cancelled."
            delivered -> "Your order has been delivered. Enjoy!"
            order.hasDeliveryPartner -> "${order.statusLabel} • updates automatically"
            else -> "Finding a delivery partner near ${order.store?.name ?: "VJoyKart Store"}…"
        }
        binding.reconnecting.visibleIf(isOffline)
    }

    private fun bindPartner(order: CustomerOrder) {
        val cancelled = order.deliveryStatus == "CANCELLED"
        val partner = order.deliveryPartner
        val assigned = order.hasDeliveryPartner && partner != null
        binding.assigningCard.visibleIf(!cancelled && !assigned)
        binding.partnerCard.visibleIf(!cancelled && assigned)
        binding.assigningText.text = if (order.paymentMethod == "ONLINE" && order.paymentStatus != "PAID") {
            "A delivery boy is assigned once payment is confirmed."
        } else {
            "We will show your delivery boy here as soon as one is assigned."
        }
        if (partner == null || !assigned) return
        binding.assignedAt.visibleIf(order.assignedAt != null)
        order.assignedAt?.let { binding.assignedAt.text = Formats.time(it) }
        binding.partnerInitial.text = partner.name.trim().firstOrNull()?.uppercase() ?: "?"
        binding.partnerName.text = partner.name
        binding.partnerPhone.text = partner.phone
        binding.callPartner.visibleIf(partner.phone.isNotEmpty() && !order.isFinished)
        binding.callPartner.setOnClickListener {
            requireContext().dial(partner.phone.replace(" ", ""), failureMessage = "Call ${partner.phone}")
        }
    }

    private fun bindTimeline(order: CustomerOrder) {
        val container = binding.timeline
        container.removeAllViews()
        order.progress.forEachIndexed { index, step ->
            container.addView(timelineRow(order, step, index == order.progress.lastIndex))
        }
    }

    private fun timelineRow(order: CustomerOrder, step: DeliveryStep, last: Boolean): View {
        val context = requireContext()
        val done = step.completed
        val current = step.current && order.deliveryStatus != "CANCELLED"
        val active = done || current
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(34.dp, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(26.dp, 26.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (done) primary else 0xFFFFFFFF.toInt())
                setStroke(if (current) 3.dp else 2.dp, if (active) primary else 0x42000000)
            }
            if (current) elevation = 4f
            when {
                done -> addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                    layoutParams = FrameLayout.LayoutParams(16.dp, 16.dp, Gravity.CENTER)
                })
                current -> addView(View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(primary) }
                    layoutParams = FrameLayout.LayoutParams(9.dp, 9.dp, Gravity.CENTER)
                })
            }
        }
        left.addView(dot)
        if (!last) {
            left.addView(View(context).apply {
                setBackgroundColor(if (done) primary else 0x1F000000)
                layoutParams = LinearLayout.LayoutParams(2.dp, 30.dp)
            })
        }
        row.addView(left)

        val right = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 3.dp, 0, 16.dp)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10.dp }
        }
        right.addView(TextView(context).apply {
            text = step.label
            textSize = 15f
            setTypeface(null, if (current || done) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                when {
                    current -> primary
                    done -> ContextCompat.getColor(context, R.color.vk_text)
                    else -> 0x61000000
                },
            )
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (current) {
            right.addView(TextView(context).apply {
                text = "Now"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(primary)
                setPadding(8.dp, 3.dp, 8.dp, 3.dp)
                background = GradientDrawable().apply { cornerRadius = 10.dp.toFloat(); setColor(0xFFE9EDFF.toInt()) }
            })
        } else if (done && step.at != null) {
            right.addView(TextView(context).apply {
                text = Formats.time(step.at)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.vk_text_secondary))
            })
        }
        row.addView(right)
        return row
    }

    private fun addInfo(label: String, value: String) {
        val context = requireContext()
        binding.summaryInfo.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 6.dp)
            addView(TextView(context).apply {
                text = "$label: "
                setTextColor(ContextCompat.getColor(context, R.color.vk_text_secondary))
            })
            addView(TextView(context).apply {
                text = value
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.vk_text))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        })
    }

    /** Opens the backend-provided VJoyKart Store location (never the customer's address). */
    private fun openStoreInMaps(order: CustomerOrder) {
        val store = order.store
        val url = when {
            store != null && store.mapsUrl.isNotEmpty() -> store.mapsUrl
            store != null -> "https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}"
            else -> STORE_MAPS_FALLBACK
        }
        requireContext().openExternal(url)
    }

    private fun openDeliveryInMaps(order: CustomerOrder) {
        val query = if (order.deliveryLatitude != null && order.deliveryLongitude != null) {
            "${order.deliveryLatitude},${order.deliveryLongitude}"
        } else {
            Uri.encode(order.address.ifEmpty { "Hyderabad" })
        }
        requireContext().openExternal("https://www.google.com/maps/search/?api=1&query=$query")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10_000L
        const val STORE_MAPS_FALLBACK = "https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9"
    }
}

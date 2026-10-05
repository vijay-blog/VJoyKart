package com.nexamart.customer.presentation.orders

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentOrdersBinding
import com.nexamart.customer.databinding.ItemOrderBinding
import com.nexamart.customer.model.CustomerOrder
import com.nexamart.customer.presentation.common.Nav.openOrderDetail
import com.nexamart.customer.presentation.common.dpF
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.util.Formats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Port of OrdersScreen (Orders tab): All / Active / Completed filters. */
class OrdersFragment : Fragment() {
    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private val filter = MutableStateFlow(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        filter.value = savedInstanceState?.getInt(KEY_FILTER) ?: filter.value
        val orders = requireContext().appContainer.orders
        val adapter = OrderAdapter { openOrderDetail(it.id) }
        binding.ordersList.layoutManager = LinearLayoutManager(requireContext())
        binding.ordersList.adapter = adapter
        val refresh = { viewLifecycleOwner.lifecycleScope.launch { orders.refresh() } }
        binding.refresh.setOnClickListener { refresh() }
        binding.swipeRefresh.setColorSchemeResources(R.color.vk_primary)
        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.filterAll.setOnClickListener { filter.value = 0 }
        binding.filterActive.setOnClickListener { filter.value = 1 }
        binding.filterCompleted.setOnClickListener { filter.value = 2 }

        launchOnStarted {
            combine(orders.state, filter) { s, f -> s to f }.collect { (state, selected) ->
                val all = state.orders
                val active = all.filter { !it.isFinished }
                val completed = all.filter { it.isFinished }
                bindFilter(binding.filterAll, "All  ${all.size}", selected == 0)
                bindFilter(binding.filterActive, "Active  ${active.size}", selected == 1)
                bindFilter(binding.filterCompleted, "Completed  ${completed.size}", selected == 2)
                val list = when (selected) {
                    1 -> active
                    2 -> completed
                    else -> all
                }
                adapter.submitList(list)
                binding.swipeRefresh.isRefreshing = state.refreshing
                binding.emptyState.visibleIf(list.isEmpty())
                binding.emptyTitle.text = when (selected) {
                    1 -> "No active orders"
                    2 -> "No completed orders"
                    else -> "No orders yet"
                }
            }
        }
    }

    private fun bindFilter(view: android.widget.TextView, text: String, selected: Boolean) {
        view.text = text
        view.background = GradientDrawable().apply {
            cornerRadius = 14f.dpF
            setColor(if (selected) ContextCompat.getColor(requireContext(), R.color.vk_primary) else 0xFFFFFFFF.toInt())
        }
        view.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xDE000000.toInt())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FILTER, filter.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class OrderAdapter(private val onOpen: (CustomerOrder) -> Unit) :
        ListAdapter<CustomerOrder, OrderAdapter.Holder>(DIFF) {
        class Holder(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val order = getItem(position)
            val b = holder.b
            val finished = order.isFinished
            b.root.setOnClickListener { onOpen(order) }
            b.statusIcon.setImageResource(if (finished) R.drawable.ic_check_circle_outline else R.drawable.ic_local_shipping_outline)
            b.orderNumber.text = "Order #${order.orderNumber}"
            b.orderDate.text = Formats.orderDateTime(order.createdAt)
            b.orderTotal.text = Formats.rupees(order.total)
            b.statusChip.text = order.statusLabel
            b.statusChip.setBackgroundResource(if (finished) R.drawable.bg_chip_finished else R.drawable.bg_chip_active)
            b.statusChip.setTextColor(if (finished) 0xFF2E7D32.toInt() else 0xFFE65100.toInt())
            b.itemCount.text = "${order.items.size} ${if (order.items.size == 1) "item" else "items"}"
            b.progress.progress = (order.deliveryFraction * 1000).toInt()
            b.statusLine.text = if (order.hasDeliveryPartner && !finished) {
                "${order.statusLabel} • ${order.deliveryPartner!!.name}"
            } else {
                order.statusLabel
            }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<CustomerOrder>() {
                override fun areItemsTheSame(oldItem: CustomerOrder, newItem: CustomerOrder) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: CustomerOrder, newItem: CustomerOrder) = oldItem == newItem
            }
        }
    }

    private companion object {
        const val KEY_FILTER = "filter"
    }
}

package com.nexamart.customer.presentation.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentOrderSuccessBinding
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.Nav.openOrderDetail
import com.nexamart.customer.presentation.common.dp
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.main.MainTabsViewModel
import com.nexamart.customer.presentation.main.returnToMainTab
import com.nexamart.customer.util.Formats

/**
 * Port of OrderSuccessScreen. CONTINUE SHOPPING (and system Back) pop everything above the single
 * Main screen and select the Home tab, so the user can never return to this screen or checkout.
 */
class OrderSuccessFragment : Fragment() {
    private var _binding: FragmentOrderSuccessBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrderSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val orderId = requireArguments().getString(Nav.ARG_ORDER_ID).orEmpty()
        val orders = requireContext().appContainer.orders

        binding.continueShopping.setOnClickListener { returnToMainTab(MainTabsViewModel.TAB_HOME) }
        binding.viewOrder.setOnClickListener { openOrderDetail(orderId) }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToMainTab(MainTabsViewModel.TAB_HOME)
        })

        launchOnStarted {
            orders.state.collect {
                val order = orders.orderById(orderId)
                binding.details.removeAllViews()
                addItem("Order Number", orderId)
                if (order != null) {
                    addItem("Total Amount", Formats.rupees(order.total))
                    addItem("Payment", if (order.paymentMethod == "ONLINE") "Online Payment" else "Cash on Delivery")
                    addItem("Delivery Address", order.address)
                }
            }
        }
    }

    private fun addItem(label: String, value: String) {
        val context = requireContext()
        binding.details.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12.dp)
            addView(TextView(context).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.vk_text_secondary))
            })
            addView(TextView(context).apply {
                text = value
                setPadding(0, 2.dp, 0, 0)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.vk_text))
            })
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

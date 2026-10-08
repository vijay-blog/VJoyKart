package com.nexamart.customer.presentation.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.Fragment
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentCheckoutBinding
import com.nexamart.customer.databinding.ItemCheckoutLineBinding
import com.nexamart.customer.databinding.ItemPaymentOptionBinding
import com.nexamart.customer.presentation.auth.OtpLoginViewModel
import com.nexamart.customer.presentation.auth.bindOtpLogin
import com.nexamart.customer.presentation.common.Nav.openOrderSuccess
import com.nexamart.customer.presentation.common.Nav.openSavedAddresses
import com.nexamart.customer.presentation.common.appViewModels
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.loadCatalogImage
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.repository.CartMath
import com.nexamart.customer.util.Formats
import com.razorpay.Checkout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Port of CheckoutScreen (session gate, address, items, COD/Razorpay, backend-verified payment). */
class CheckoutFragment : Fragment() {
    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CheckoutViewModel by appViewModels { c, h -> CheckoutViewModel(c, h) }
    private val otpViewModel: OtpLoginViewModel by appViewModels { c, _ -> OtpLoginViewModel(c.session) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = requireContext().appContainer
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.addressAction.setOnClickListener { openSavedAddresses() }
        binding.subtotalRow.label.setText(R.string.subtotal)
        binding.deliveryRow.label.setText(R.string.delivery)
        binding.discountRow.label.setText(R.string.discount)
        binding.totalRow.label.setText(R.string.total)
        binding.totalRow.label.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.totalRow.value.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.codTile.root.setOnClickListener { viewModel.selectPayment(CheckoutViewModel.COD) }
        binding.onlineTile.root.setOnClickListener { viewModel.selectPayment(CheckoutViewModel.ONLINE) }
        binding.codTile.icon.setImageResource(R.drawable.ic_payments_outline)
        binding.codTile.title.setText(R.string.cash_on_delivery)
        binding.codTile.subtitle.setText(R.string.cod_subtitle)
        binding.onlineTile.icon.setImageResource(R.drawable.ic_lock_outline)
        binding.onlineTile.subtitle.setText(R.string.online_subtitle)
        binding.placeOrder.setOnClickListener { viewModel.place() }

        bindOtpLogin(binding.otpView, otpViewModel, onMessage = ::showMessage, onAuthenticated = viewModel::onAuthenticated)

        launchOnStarted {
            launch {
                combine(viewModel.state, container.cart.items, container.addresses.state) { s, items, addr ->
                    Triple(s, items, addr)
                }.collect { (state, items, addresses) ->
                    binding.sessionProgress.visibleIf(state.checkingSession)
                    binding.otpView.root.visibleIf(!state.checkingSession && !state.authenticated)
                    binding.checkoutContent.visibleIf(!state.checkingSession && state.authenticated)

                    val selected = addresses.selected
                    if (selected == null) {
                        binding.addressIcon.setImageResource(R.drawable.ic_location_on_outline)
                        binding.addressTitle.setText(R.string.no_address_selected)
                        binding.addressTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                        binding.addressSubtitle.setText(R.string.no_address_selected_desc)
                        binding.addressAction.setText(R.string.manage)
                    } else {
                        binding.addressIcon.setImageResource(R.drawable.ic_home_outline)
                        binding.addressTitle.text = selected.name
                        binding.addressTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                        binding.addressSubtitle.text = "${selected.oneLine}\n${selected.mobile}"
                        binding.addressAction.setText(R.string.change)
                    }

                    binding.itemsContainer.removeAllViews()
                    items.forEach { item ->
                        val line = ItemCheckoutLineBinding.inflate(layoutInflater, binding.itemsContainer, false)
                        line.image.loadCatalogImage(item.product.imageAsset)
                        line.name.text = item.product.name
                        line.detail.text = "${item.product.unit} × ${item.quantity}"
                        line.total.text = Formats.rupees(item.total)
                        binding.itemsContainer.addView(line.root)
                    }

                    val subtotal = CartMath.subtotal(items)
                    val total = CartMath.total(items)
                    val online = state.paymentMethod == CheckoutViewModel.ONLINE
                    binding.onlineTile.title.text = "Pay ${Formats.rupees(total)} securely"
                    bindTile(binding.codTile, !online)
                    bindTile(binding.onlineTile, online)
                    binding.razorpayInfo.visibleIf(online)

                    binding.subtotalRow.value.text = Formats.rupees(subtotal)
                    binding.deliveryRow.value.text = Formats.rupees(CartMath.delivery(subtotal))
                    binding.discountRow.value.text = "-" + Formats.rupees(CartMath.savings(items))
                    binding.totalRow.value.text = Formats.rupees(total)

                    binding.placeOrder.isEnabled = !state.loading && items.isNotEmpty()
                    binding.placeProgress.visibleIf(state.loading)
                    binding.placeOrder.text = when {
                        state.loading -> ""
                        online -> "Pay Securely • ${Formats.rupees(total)}"
                        else -> "Place COD Order • ${Formats.rupees(total)}"
                    }
                }
            }
            launch {
                container.paymentResults.results.collect { result ->
                    container.paymentResults.consume()
                    viewModel.onPaymentResult(result)
                }
            }
            viewModel.events.collect { event ->
                when (event) {
                    is CheckoutEvent.Message -> showMessage(event.text)
                    is CheckoutEvent.OpenRazorpay -> openRazorpay(event)
                    is CheckoutEvent.OrderPlaced -> openOrderSuccess(event.orderId)
                }
            }
        }
    }

    private fun bindTile(tile: ItemPaymentOptionBinding, selected: Boolean) {
        tile.check.setImageResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_radio_button_unchecked)
        tile.check.setColorFilter(ContextCompat.getColor(requireContext(), if (selected) R.color.vk_primary else R.color.vk_grey))
    }

    private fun openRazorpay(event: CheckoutEvent.OpenRazorpay) {
        try {
            val checkout = Checkout()
            checkout.setKeyID(event.keyId)
            checkout.open(requireActivity(), event.options)
        } catch (e: Exception) {
            viewModel.onPaymentResult(PaymentResult.Error(-1, e.message))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

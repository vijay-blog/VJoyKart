package com.nexamart.customer.presentation.cart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentCartBinding
import com.nexamart.customer.databinding.ItemCartBinding
import com.nexamart.customer.model.CartItem
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.Nav.openCheckout
import com.nexamart.customer.presentation.common.ProductAdapter
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.loadCatalogImage
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.presentation.main.MainTabsViewModel
import com.nexamart.customer.presentation.main.returnToMainTab
import com.nexamart.customer.repository.CartMath
import com.nexamart.customer.repository.CartRepository
import com.nexamart.customer.util.Formats
import androidx.lifecycle.ViewModelProvider

/** Port of CartScreen. Used both as the Cart tab (embedded) and as a pushed screen (standalone). */
class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val standalone get() = arguments?.getBoolean(Nav.ARG_STANDALONE, true) ?: true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val cart = requireContext().appContainer.cart
        if (standalone) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        }
        binding.subtotalRow.label.setText(R.string.subtotal)
        binding.deliveryRow.label.setText(R.string.delivery)
        binding.discountRow.label.setText(R.string.discount)
        binding.continueShopping.setOnClickListener {
            if (standalone) {
                findNavController().navigateUp()
            } else {
                ViewModelProvider(requireActivity())[MainTabsViewModel::class.java].select(MainTabsViewModel.TAB_HOME)
            }
        }
        binding.continueButton.setOnClickListener { openCheckout() }

        val adapter = CartAdapter(cart)
        binding.cartList.layoutManager = LinearLayoutManager(requireContext())
        binding.cartList.adapter = adapter

        launchOnStarted {
            cart.items.collect { items ->
                val empty = items.isEmpty()
                binding.emptyState.visibleIf(empty)
                binding.cartList.visibleIf(!empty)
                binding.summary.visibleIf(!empty)
                adapter.submitList(items)
                if (empty) return@collect
                val subtotal = CartMath.subtotal(items)
                val total = CartMath.total(items)
                binding.subtotalRow.value.text = Formats.rupees(subtotal)
                binding.deliveryRow.value.text = Formats.rupees(CartMath.delivery(subtotal))
                binding.discountRow.value.text = "-" + Formats.rupees(CartMath.savings(items))
                binding.totalValue.text = Formats.rupees(total)
                binding.freeDeliveryHint.visibleIf(subtotal < CartMath.FREE_DELIVERY_THRESHOLD)
                binding.freeDeliveryHint.text =
                    "Add ${Formats.rupees(CartMath.FREE_DELIVERY_THRESHOLD - subtotal)} more for free delivery"
                binding.continueButton.text = "Continue • ${Formats.rupees(total)}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CartAdapter(private val cart: CartRepository) :
        ListAdapter<CartItem, CartAdapter.Holder>(DIFF) {
        class Holder(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            b.image.clipToOutline = true
            return Holder(b)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            val b = holder.b
            b.image.loadCatalogImage(item.product.imageAsset)
            b.name.text = item.product.name
            b.unit.text = item.product.unit
            b.unit.visibleIf(item.product.unit.isNotEmpty())
            b.price.text = Formats.rupees(item.product.sellingPrice)
            b.quantity.text = item.quantity.toString()
            b.minus.setOnClickListener { cart.remove(item.product) }
            b.plus.setOnClickListener {
                if (!cart.add(item.product)) {
                    Toast.makeText(b.root.context, ProductAdapter.STOCK_LIMIT_MESSAGE, Toast.LENGTH_SHORT).show()
                }
            }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
                override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem.product.id == newItem.product.id
                override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) =
                    oldItem.quantity == newItem.quantity && oldItem.product == newItem.product
            }
        }
    }

    companion object {
        fun embedded() = CartFragment().apply { arguments = bundleOf(Nav.ARG_STANDALONE to false) }
    }
}

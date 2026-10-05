package com.nexamart.customer.presentation.common

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.ItemProductCardBinding
import com.nexamart.customer.model.Product
import com.nexamart.customer.presentation.common.Nav.openProduct
import com.nexamart.customer.repository.CartRepository
import com.nexamart.customer.util.Formats
import kotlin.math.roundToInt

/** Port of widgets/product_card.dart, shared by Home rows and product grids. */
class ProductAdapter(
    private val cart: CartRepository,
    private val onOpen: (Product) -> Unit,
    private val fixedWidthPx: Int? = null,
    private val aspectRatio: Float = 0.63f,
) : ListAdapter<Product, ProductAdapter.Holder>(DIFF) {

    private var quantities: Map<Int, Int> = emptyMap()

    fun submitCart(quantities: Map<Int, Int>) {
        if (quantities == this.quantities) return
        val changed = (this.quantities.keys + quantities.keys).filter { this.quantities[it] != quantities[it] }.toSet()
        this.quantities = quantities
        currentList.forEachIndexed { index, product -> if (product.id in changed) notifyItemChanged(index, PAYLOAD_QTY) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemProductCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.card.aspectRatio = aspectRatio
        fixedWidthPx?.let { binding.root.layoutParams = RecyclerView.LayoutParams(it, ViewGroup.LayoutParams.WRAP_CONTENT) }
        binding.image.clipToOutline = true
        binding.mrp.paintFlags = binding.mrp.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_QTY }) {
            holder.bindQuantity(getItem(position))
        } else {
            holder.bind(getItem(position))
        }
    }

    inner class Holder(private val b: ItemProductCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(product: Product) {
            b.root.setOnClickListener { onOpen(product) }
            b.image.loadCatalogImage(product.imageAsset)
            b.discount.visibleIf(product.discount >= 1)
            b.discount.text = "${product.discount.roundToInt()}% OFF"
            b.name.text = product.name
            b.brand.text = product.brand
            b.unit.text = product.unit
            b.outOfStock.visibleIf(!product.available)
            b.price.text = Formats.rupees(product.sellingPrice)
            b.mrp.text = Formats.rupees(product.mrp)
            b.addButton.isEnabled = product.available
            b.addButton.setOnClickListener { add(product) }
            b.stepper.minus.setOnClickListener { cart.remove(product) }
            b.stepper.plus.setOnClickListener { add(product) }
            bindQuantity(product)
        }

        fun bindQuantity(product: Product) {
            val quantity = quantities[product.id] ?: 0
            b.addButton.visibleIf(quantity == 0)
            b.stepper.root.visibleIf(quantity > 0)
            b.stepper.quantity.text = quantity.toString()
        }

        private fun add(product: Product) {
            if (!cart.add(product)) {
                Toast.makeText(b.root.context, STOCK_LIMIT_MESSAGE, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val PAYLOAD_QTY = "qty"
        const val STOCK_LIMIT_MESSAGE = "No more stock available for this item."

        val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
        }

        /** Grid/row adapter bound to the app cart that opens product details. */
        fun forFragment(fragment: Fragment, fixedWidthPx: Int? = null, aspectRatio: Float = 0.63f) = ProductAdapter(
            cart = fragment.requireContext().appContainer.cart,
            onOpen = { fragment.openProduct(it) },
            fixedWidthPx = fixedWidthPx,
            aspectRatio = aspectRatio,
        )
    }
}

fun cartQuantities(items: List<com.nexamart.customer.model.CartItem>): Map<Int, Int> =
    items.associate { it.product.id to it.quantity }

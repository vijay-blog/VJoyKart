package com.nexamart.customer.presentation.catalog

import android.graphics.Rect
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexamart.customer.R
import com.nexamart.customer.databinding.ViewProductGridBinding
import com.nexamart.customer.model.Product
import com.nexamart.customer.presentation.common.ProductAdapter
import com.nexamart.customer.presentation.common.dp
import com.nexamart.customer.presentation.common.visibleIf

/** Two-column product grid (crossAxisSpacing 12, padding 16, aspect .63) with loading/empty/error states. */
class ProductGrid(private val fragment: Fragment, private val binding: ViewProductGridBinding) {
    val adapter = ProductAdapter.forFragment(fragment)

    init {
        binding.productGrid.layoutManager = GridLayoutManager(fragment.requireContext(), 2)
        binding.productGrid.adapter = adapter
        binding.productGrid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val half = 6.dp
                outRect.set(half, half, half, half)
            }
        })
    }

    fun showProducts(products: List<Product>, emptyText: String, emptyIcon: Int = R.drawable.ic_search_off) {
        binding.gridLoading.visibleIf(false)
        adapter.submitList(products)
        binding.productGrid.visibleIf(products.isNotEmpty())
        showMessage(if (products.isEmpty()) emptyText else null, emptyIcon, null)
    }

    fun showLoading() {
        binding.gridLoading.visibleIf(true)
        binding.productGrid.visibleIf(false)
        showMessage(null, 0, null)
    }

    fun showError(text: String, onRetry: (() -> Unit)?) {
        binding.gridLoading.visibleIf(false)
        binding.productGrid.visibleIf(false)
        showMessage(text, R.drawable.ic_cloud_off, onRetry)
    }

    private fun showMessage(text: String?, icon: Int, onRetry: (() -> Unit)?) {
        binding.gridMessage.visibleIf(text != null)
        if (text == null) return
        binding.gridMessageText.text = text
        binding.gridMessageIcon.isVisible = icon != 0
        if (icon != 0) binding.gridMessageIcon.setImageResource(icon)
        binding.gridRetry.visibleIf(onRetry != null)
        binding.gridRetry.setOnClickListener { onRetry?.invoke() }
    }
}

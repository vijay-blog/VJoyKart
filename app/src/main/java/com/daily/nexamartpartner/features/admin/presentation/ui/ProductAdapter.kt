package com.daily.nexamartpartner.features.admin.presentation.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.format.ValueFormatter
import com.daily.nexamartpartner.databinding.ItemProductSummaryBinding
import com.daily.nexamartpartner.features.admin.domain.model.ProductSummary

class ProductAdapter(
    private val onClick: (ProductSummary) -> Unit
) : ListAdapter<ProductSummary, ProductAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ItemProductSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemProductSummaryBinding,
        private val onClick: (ProductSummary) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProductSummary) {
            val context = binding.root.context
            val unavailable = context.getString(R.string.admin_product_details_unavailable_value)

            binding.productNameText.text = item.name.ifBlank { unavailable }
            binding.productMetaText.text = context.getString(
                R.string.admin_products_meta_template,
                item.categoryName ?: unavailable,
                item.stock?.toString() ?: unavailable,
                item.unit ?: unavailable
            )
            val priceValue = item.discountedPrice ?: item.price
            binding.productPriceText.text = priceValue?.let {
                if (item.currencyCode.isNullOrBlank()) it.toPlainString()
                else ValueFormatter.formatCurrency(it, item.currencyCode)
            } ?: context.getString(R.string.admin_dashboard_amount_unavailable)
            binding.productStatusText.text = item.status.backendValue

            binding.productImagePlaceholder.contentDescription =
                context.getString(R.string.cd_product_image_unavailable)
            binding.productImagePlaceholder.load(item.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
                fallback(android.R.drawable.ic_menu_gallery)
            }

            binding.root.contentDescription = context.getString(
                R.string.cd_product_summary_card,
                item.name,
                item.status.backendValue
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ProductSummary>() {
        override fun areItemsTheSame(oldItem: ProductSummary, newItem: ProductSummary): Boolean =
            oldItem.productId == newItem.productId

        override fun areContentsTheSame(oldItem: ProductSummary, newItem: ProductSummary): Boolean =
            oldItem == newItem
    }
}

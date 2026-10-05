package com.nexamart.customer.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.nexamart.customer.R
import com.nexamart.customer.databinding.ItemCategoryBinding
import com.nexamart.customer.model.FashionCategory
import com.nexamart.customer.presentation.common.dpF
import com.nexamart.customer.presentation.common.loadCatalogImage

/** Port of widgets/fashion_banner_slider.dart banners. */
data class Banner(val asset: String, val title: String)

val FASHION_BANNERS = listOf(
    Banner("images/fashion_banners/launch_10_70.png", "Grand Launch Offer"),
    Banner("images/fashion_banners/fashion_family.png", "Fashion For Everyone"),
    Banner("images/fashion_banners/new_styles.png", "New Season Styles"),
)

class BannerAdapter(private val onClick: () -> Unit) : RecyclerView.Adapter<BannerAdapter.Holder>() {
    class Holder(val image: ShapeableImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val image = ShapeableImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(22f.dpF).build()
            setBackgroundResource(R.color.vk_image_bg)
        }
        return Holder(image)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val banner = FASHION_BANNERS[position]
        holder.image.contentDescription = banner.title
        holder.image.load("file:///android_asset/${banner.asset}")
        holder.image.setOnClickListener { onClick() }
    }

    override fun getItemCount() = FASHION_BANNERS.size
}

class CategoryAdapter(private val onClick: (FashionCategory) -> Unit) : RecyclerView.Adapter<CategoryAdapter.Holder>() {
    private val items = com.nexamart.customer.model.FashionCatalog.categories

    class Holder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val category = items[position]
        holder.binding.categoryName.text = category.name
        holder.binding.categoryImage.loadCatalogImage(category.asset)
        holder.binding.root.setOnClickListener { onClick(category) }
    }

    override fun getItemCount() = items.size
}

package com.nexamart.customer.presentation.product

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentProductDetailBinding
import com.nexamart.customer.databinding.ItemKeyValueBinding
import com.nexamart.customer.model.Product
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.Nav.openCart
import com.nexamart.customer.presentation.common.Nav.openCheckout
import com.nexamart.customer.presentation.common.Nav.openSearch
import com.nexamart.customer.presentation.common.ProductAdapter
import com.nexamart.customer.presentation.common.cartQuantities
import com.nexamart.customer.presentation.common.dp
import com.nexamart.customer.presentation.common.dpF
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.loadCatalogImage
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.repository.CartMath
import com.nexamart.customer.util.Formats
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

/** Port of ProductDetailScreen. */
class ProductDetailFragment : Fragment() {
    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var product: Product
    private lateinit var images: List<String>
    private var currentImage = 0
    private var detailsExpanded = true
    private val handler = Handler(Looper.getMainLooper())
    private val autoSlide = object : Runnable {
        override fun run() {
            val b = _binding ?: return
            if (images.size >= 2) b.gallery.setCurrentItem((currentImage + 1) % images.size, true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parsed = Nav.productFrom(arguments)
        if (parsed == null) {
            findNavController().navigateUp()
            return
        }
        product = parsed
        detailsExpanded = savedInstanceState?.getBoolean(KEY_EXPANDED, true) ?: true
        images = product.images.map { it.trim() }.filter { it.isNotEmpty() }.take(3).ifEmpty { listOf("") }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.searchButton.setOnClickListener { openSearch() }
        binding.shareButton.setOnClickListener { showMessage("Product sharing will be available soon.") }
        binding.cartButton.root.setOnClickListener { openCart() }
        binding.viewCart.setOnClickListener { openCart() }

        setupGallery()
        bindProduct()

        val container = requireContext().appContainer
        binding.addToCart.isEnabled = product.available
        binding.buyNow.isEnabled = product.available
        binding.addToCart.setOnClickListener {
            if (!container.cart.add(product)) {
                Toast.makeText(requireContext(), ProductAdapter.STOCK_LIMIT_MESSAGE, Toast.LENGTH_SHORT).show()
            }
        }
        binding.buyNow.setOnClickListener { buyNow() }

        val similarAdapter = ProductAdapter.forFragment(this, fixedWidthPx = 175.dp)
        binding.similarList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.similarList.adapter = similarAdapter
        binding.similarList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                if (parent.getChildAdapterPosition(view) > 0) outRect.left = 12.dp
            }
        })

        launchOnStarted {
            combine(container.catalog.state, container.cart.items) { c, items -> c to items }.collect { (catalog, items) ->
                val count = CartMath.count(items)
                binding.cartButton.cartBadge.visibleIf(count > 0)
                binding.cartButton.cartBadge.text = count.toString()

                val inCart = items.firstOrNull { it.product.id == product.id }
                binding.cartNotice.visibleIf(inCart != null)
                if (inCart != null) {
                    binding.cartNoticeText.text = "${inCart.quantity} ${if (inCart.quantity == 1) "item" else "items"} in your cart"
                }

                val similar = catalog.products
                    .filter { it.id != product.id && it.categoryId == product.categoryId }
                    .take(8)
                binding.similarSection.visibleIf(similar.isNotEmpty())
                similarAdapter.submitList(similar)
                similarAdapter.submitCart(cartQuantities(items))
            }
        }
    }

    private fun buyNow() {
        if (!product.available) return
        val cart = requireContext().appContainer.cart
        try {
            if (cart.items.value.none { it.product.id == product.id }) cart.add(product)
            openCheckout()
        } catch (_: Exception) {
            showMessage("Unable to start checkout right now. Please try again.")
        }
    }

    private fun setupGallery() {
        binding.galleryBox.clipToOutline = true
        binding.gallery.adapter = GalleryAdapter(images) { showNext() }
        val multiple = images.size > 1
        binding.previousImage.visibleIf(multiple)
        binding.nextImage.visibleIf(multiple)
        binding.imageCounter.visibleIf(multiple)
        binding.galleryDots.visibleIf(multiple)
        binding.previousImage.setOnClickListener {
            if (images.size >= 2) binding.gallery.setCurrentItem((currentImage - 1 + images.size) % images.size, true)
        }
        binding.nextImage.setOnClickListener { showNext() }
        binding.gallery.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentImage = position
                updateGalleryIndicators()
                restartAutoSlide()
            }
        })
        updateGalleryIndicators()
    }

    private fun showNext() {
        if (images.size < 2) return
        binding.gallery.setCurrentItem((currentImage + 1) % images.size, true)
    }

    private fun updateGalleryIndicators() {
        val b = _binding ?: return
        b.imageCounter.text = "${currentImage + 1}/${images.size}"
        b.galleryDots.removeAllViews()
        images.indices.forEach { index ->
            val active = index == currentImage
            b.galleryDots.addView(View(requireContext()).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 10f.dpF
                    setColor(if (active) 0xFF2874F0.toInt() else 0xFFE0E0E0.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(if (active) 22.dp else 7.dp, 7.dp).apply {
                    marginStart = 3.dp
                    marginEnd = 3.dp
                }
            })
        }
    }

    private fun restartAutoSlide() {
        handler.removeCallbacks(autoSlide)
        if (images.size >= 2 && _binding != null) handler.postDelayed(autoSlide, AUTO_SLIDE_MS)
    }

    private fun bindProduct() {
        val b = binding
        b.discountBadge.visibleIf(product.discount >= 1)
        b.discountBadge.text = "${product.discount.roundToInt()}% OFF"
        b.productName.text = product.name
        b.productBrand.visibleIf(product.brand.isNotBlank())
        b.productBrand.text = product.brand
        b.categoryRow.visibleIf(product.categoryName.isNotBlank())
        b.categoryName.text = product.categoryName
        b.sellingPrice.text = Formats.rupees(product.sellingPrice)
        b.mrpPrice.visibleIf(product.mrp > product.sellingPrice)
        b.mrpPrice.text = Formats.rupees(product.mrp)
        b.mrpPrice.paintFlags = b.mrpPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        b.taxNote.visibleIf(product.unit.isNotBlank())
        b.taxNote.text = "Inclusive of all applicable taxes • ${product.unit}"

        b.availabilityIcon.setImageResource(if (product.available) R.drawable.ic_check_circle else R.drawable.ic_cancel)
        b.availabilityIcon.setColorFilter(if (product.available) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        b.availabilityText.text = if (product.available) "In Stock" else "Currently unavailable"
        b.stockLeft.visibleIf(product.stock > 0)
        b.stockLeft.text = "Only ${product.stock} left in stock"

        bindHighlights()
        b.description.text = product.description.trim().ifEmpty { "Product information will be updated by the seller." }
        bindAbout()
        b.aboutHeader.setOnClickListener {
            detailsExpanded = !detailsExpanded
            bindAbout()
        }
        bindDetailsTable()
        bindPromises()
    }

    private fun bindAbout() {
        binding.description.visibleIf(detailsExpanded)
        binding.aboutToggle.setImageResource(if (detailsExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down)
    }

    private fun bindHighlights() {
        val entries = mutableListOf<Pair<String, String>>()
        fun add(label: String, value: String) {
            if (value.isNotBlank() && entries.size < 10) entries += label to value.trim()
        }
        add("Brand", product.brand)
        add("Category", product.categoryName)
        add("Quantity", product.unit)
        add("Weight", product.weight)
        add("Size", product.size)
        product.attributes.forEach { (key, value) ->
            if (key.isNotBlank() && entries.none { it.first.lowercase() == key.lowercase() }) add(key, value)
        }

        val container = binding.highlights
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.background = null
            container.addView(TextView(requireContext()).apply {
                text = "No additional product highlights have been provided by the seller."
                setTextColor(ContextCompat.getColor(context, R.color.vk_grey_600))
            })
            return
        }
        container.setBackgroundResource(R.drawable.bg_highlights)
        entries.forEachIndexed { index, (key, value) ->
            if (index > 0) {
                container.addView(View(requireContext()).apply {
                    setBackgroundColor(0xFFEEEEEE.toInt())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp)
                })
            }
            val row = ItemKeyValueBinding.inflate(layoutInflater, container, false)
            row.key.text = key
            row.value.text = value
            row.value.setTypeface(row.value.typeface, android.graphics.Typeface.NORMAL)
            (row.value.layoutParams as LinearLayout.LayoutParams).marginStart = 14.dp
            row.root.setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            container.addView(row.root)
        }
    }

    private fun bindDetailsTable() {
        val details = buildList {
            if (product.categoryName.isNotEmpty()) add("Category" to product.categoryName)
            if (product.unit.isNotEmpty()) add("Unit" to product.unit)
            if (product.weight.isNotEmpty()) add("Weight" to product.weight)
            if (product.size.isNotEmpty()) add("Size" to product.size)
            if (product.deliveryType.isNotEmpty()) add("Delivery type" to product.deliveryType)
            if (product.stock >= 0) add("Stock available" to product.stock.toString())
            product.attributes.forEach { (k, v) -> add(k to v) }
        }
        binding.detailsSection.visibleIf(details.isNotEmpty())
        binding.detailsTable.removeAllViews()
        details.forEach { (key, value) ->
            val row = ItemKeyValueBinding.inflate(layoutInflater, binding.detailsTable, false)
            row.key.text = key
            row.value.text = value
            row.root.setPadding(0, 0, 0, 12.dp)
            binding.detailsTable.addView(row.root)
        }
    }

    private fun bindPromises() {
        val promises = listOf(
            R.drawable.ic_shopping_bag_outline to "Easy\nordering",
            R.drawable.ic_local_shipping_outline to "Delivery\ntracking",
            R.drawable.ic_currency_rupee_round to "Online\npayment",
            R.drawable.ic_support_agent_outline to "Order\nsupport",
        )
        binding.promises.removeAllViews()
        promises.forEach { (icon, label) ->
            binding.promises.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(ContextCompat.getColor(context, R.color.vk_pink))
                    layoutParams = LinearLayout.LayoutParams(26.dp, 26.dp)
                })
                addView(TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.vk_text))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = 6.dp }
                })
            })
        }
    }

    override fun onStart() {
        super.onStart()
        restartAutoSlide()
    }

    override fun onStop() {
        handler.removeCallbacks(autoSlide)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_EXPANDED, detailsExpanded)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(autoSlide)
        super.onDestroyView()
        _binding = null
    }

    private class GalleryAdapter(
        private val images: List<String>,
        private val onTap: () -> Unit,
    ) : RecyclerView.Adapter<GalleryAdapter.Holder>() {
        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setPadding(18.dp, 18.dp, 18.dp, 18.dp)
                contentDescription = null
            },
        )

        override fun getItemCount() = images.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.image.loadCatalogImage(images[position])
            holder.image.setOnClickListener { onTap() }
        }
    }

    private companion object {
        const val AUTO_SLIDE_MS = 3_000L
        const val KEY_EXPANDED = "detailsExpanded"
    }
}

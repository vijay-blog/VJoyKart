package com.nexamart.customer.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentHomeBinding
import com.nexamart.customer.databinding.ViewProductSectionBinding
import com.nexamart.customer.databinding.ViewUpcomingCardBinding
import com.nexamart.customer.model.FashionCatalog
import com.nexamart.customer.model.Product
import com.nexamart.customer.presentation.common.Nav.openCart
import com.nexamart.customer.presentation.common.Nav.openCategory
import com.nexamart.customer.presentation.common.Nav.openSearch
import com.nexamart.customer.presentation.common.ProductAdapter
import com.nexamart.customer.presentation.common.cartQuantities
import com.nexamart.customer.presentation.common.dp
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.visibleIf
import com.nexamart.customer.repository.CartMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Port of the `_HomeTab` in home_screen.dart (fashion-first home). */
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private data class Section(val binding: ViewProductSectionBinding, val adapter: ProductAdapter)
    private val sections = mutableListOf<Section>()
    private var bannerPage = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = requireContext().appContainer
        val catalog = container.catalog

        binding.swipeRefresh.setColorSchemeResources(R.color.vk_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                catalog.load()
                binding.swipeRefresh.isRefreshing = false
            }
        }
        binding.cartButton.root.setOnClickListener { openCart() }
        binding.searchBox.setOnClickListener { openSearch() }
        binding.errorRetry.setOnClickListener { viewLifecycleOwner.lifecycleScope.launch { catalog.load() } }

        setupBanners()
        binding.categoryList.adapter = CategoryAdapter { openCategory(it.name, it.keywords) }

        bindUpcoming(binding.upcomingSlippers, "Slippers", "Footwear", R.drawable.ic_hiking_round)
        bindUpcoming(binding.upcomingElectronics, "Electronics", "Coming soon", R.drawable.ic_headphones_round)
        bindUpcoming(binding.upcomingMobiles, "Mobiles", "Coming soon", R.drawable.ic_phone_iphone_round)

        sections.clear()
        listOf(binding.sectionTrending, binding.sectionMen, binding.sectionWomen, binding.sectionKids).forEach { section ->
            val adapter = ProductAdapter.forFragment(this, fixedWidthPx = 176.dp, aspectRatio = 176f / 286f)
            section.sectionList.adapter = adapter
            section.sectionList.addItemDecoration(SpacingDecoration(12.dp))
            sections += Section(section, adapter)
        }

        launchOnStarted {
            catalog.state.combine(container.cart.items) { state, items -> state to items }.collect { (state, items) ->
                render(state.products, state.loading, state.error, state.loaded)
                val quantities = cartQuantities(items)
                sections.forEach { it.adapter.submitCart(quantities) }
                val count = CartMath.count(items)
                binding.cartButton.cartBadge.visibleIf(count > 0)
                binding.cartButton.cartBadge.text = count.toString()
            }
        }
        launchOnStarted {
            while (true) {
                delay(3000)
                if (!isHidden && _binding != null) {
                    val next = (bannerPage + 1) % FASHION_BANNERS.size
                    binding.bannerPager.setCurrentItem(next, true)
                }
            }
        }
        if (!catalog.state.value.loaded && !catalog.state.value.loading) {
            viewLifecycleOwner.lifecycleScope.launch { catalog.load() }
        }
    }

    private fun render(products: List<Product>, loading: Boolean, error: String?, loaded: Boolean) {
        val men = products.filter { FashionCatalog.matchesHomeSection(it, FashionCatalog.MEN) }.take(10)
        val women = products.filter { FashionCatalog.matchesHomeSection(it, FashionCatalog.WOMEN) }.take(10)
        val kids = products.filter { FashionCatalog.matchesHomeSection(it, FashionCatalog.KIDS) }.take(10)
        bindSection(0, "Trending Fashion", products.take(12)) { openCategory("Trending Fashion", emptyList()) }
        bindSection(1, "Men's Wear", men) { openCategory("Men", FashionCatalog.MEN) }
        bindSection(2, "Women's Wear", women) { openCategory("Women", FashionCatalog.WOMEN) }
        bindSection(3, "Kids & Girls Wear", kids) { openCategory("Kids & Girls Wear", FashionCatalog.KIDS) }

        val showLoading = loading || (!loaded && products.isEmpty())
        binding.loading.visibleIf(showLoading)
        binding.errorCard.visibleIf(!showLoading && products.isEmpty() && error != null)
        binding.emptyState.visibleIf(!showLoading && products.isEmpty() && error == null)
        if (!loading) binding.swipeRefresh.isRefreshing = false
    }

    private fun bindSection(index: Int, title: String, products: List<Product>, onSeeAll: () -> Unit) {
        val section = sections[index]
        section.binding.root.visibleIf(products.isNotEmpty())
        section.binding.sectionTitle.text = title
        section.binding.seeAll.setOnClickListener { onSeeAll() }
        section.adapter.submitList(products)
    }

    private fun bindUpcoming(card: ViewUpcomingCardBinding, title: String, subtitle: String, icon: Int) {
        card.upcomingTitle.text = title
        card.upcomingSubtitle.text = subtitle
        card.upcomingIcon.setImageResource(icon)
    }

    private fun setupBanners() {
        binding.bannerPager.adapter = BannerAdapter {
            val men = FashionCatalog.categories.first()
            openCategory(men.name, men.keywords)
        }
        binding.bannerPager.offscreenPageLimit = 1
        val dots = binding.bannerDots
        dots.removeAllViews()
        repeat(FASHION_BANNERS.size) {
            dots.addView(
                View(requireContext()).apply {
                    setBackgroundResource(R.drawable.bg_dot)
                    layoutParams = LinearLayout.LayoutParams(7.dp, 7.dp).apply { marginStart = 3.dp; marginEnd = 3.dp }
                },
            )
        }
        binding.bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bannerPage = position
                updateDots(position)
            }
        })
        updateDots(binding.bannerPager.currentItem)
    }

    private fun updateDots(selected: Int) {
        val dots = _binding?.bannerDots ?: return
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i)
            val active = i == selected
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply { width = if (active) 22.dp else 7.dp }
            dot.background.mutate().setTint(
                ContextCompat.getColor(requireContext(), if (active) R.color.vk_primary else R.color.vk_black_26),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sections.clear()
        _binding = null
    }
}

class SpacingDecoration(private val spacePx: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: androidx.recyclerview.widget.RecyclerView,
        state: androidx.recyclerview.widget.RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position > 0) outRect.left = spacePx
    }
}

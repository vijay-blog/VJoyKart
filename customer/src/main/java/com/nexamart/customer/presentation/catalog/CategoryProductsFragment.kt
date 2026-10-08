package com.nexamart.customer.presentation.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentCategoryProductsBinding
import com.nexamart.customer.model.FashionCatalog
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.cartQuantities
import com.nexamart.customer.presentation.common.launchOnStarted
import kotlinx.coroutines.flow.combine

/** Port of FashionCategoryScreen (local keyword filter over the loaded fashion catalog). */
class CategoryProductsFragment : Fragment() {
    private var _binding: FragmentCategoryProductsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = requireArguments().getString(Nav.ARG_TITLE).orEmpty()
        val keywords = requireArguments().getStringArray(Nav.ARG_KEYWORDS)?.toList().orEmpty()
        val container = requireContext().appContainer
        val grid = ProductGrid(this, binding.grid)
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        launchOnStarted {
            combine(container.catalog.state, container.cart.items) { c, items -> c to items }.collect { (catalog, items) ->
                grid.adapter.submitCart(cartQuantities(items))
                // An empty keyword list is used by "See all" on Trending Fashion: show every product.
                val products = if (keywords.isEmpty()) {
                    catalog.products
                } else {
                    catalog.products.filter { FashionCatalog.matchesCategory(it, keywords) }
                }
                if (catalog.loading && products.isEmpty()) {
                    grid.showLoading()
                } else {
                    grid.showProducts(products, getString(R.string.more_styles_coming_soon), R.drawable.ic_checkroom_outline)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

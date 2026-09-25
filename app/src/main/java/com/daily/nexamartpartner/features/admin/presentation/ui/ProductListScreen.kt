package com.daily.nexamartpartner.features.admin.presentation.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentAdminProductsBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.ProductFilters
import com.daily.nexamartpartner.features.admin.domain.model.ProductSort
import com.daily.nexamartpartner.features.admin.domain.model.ProductStatus
import com.daily.nexamartpartner.features.admin.presentation.state.ProductListUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductEvent
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductListViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductListViewModelFactory
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import kotlinx.coroutines.launch

class ProductListScreen : Fragment(R.layout.fragment_admin_products) {
    private var _binding: FragmentAdminProductsBinding? = null
    private val binding: FragmentAdminProductsBinding
        get() = requireNotNull(_binding)

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: ProductListViewModel by viewModels {
        ProductListViewModelFactory(
            getProducts = requireContext().appContainer.provideGetProductsUseCase(),
            getCategoryOptions = requireContext().appContainer.provideGetProductCategoryOptionsUseCase()
        )
    }

    private val adapter = ProductAdapter { product ->
        findNavController().navigate(
            R.id.adminProductDetailsFragment,
            bundleOf("productId" to product.productId)
        )
    }

    private val searchWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            viewModel.onSearchQueryChanged(s?.toString().orEmpty())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminProductsBinding.bind(view)
        setupRecyclerView()
        setupActions()
        collectUi()
        collectEvents()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun setupRecyclerView() {
        val manager = LinearLayoutManager(requireContext())
        binding.productsRecyclerView.layoutManager = manager
        binding.productsRecyclerView.adapter = adapter
        binding.productsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = manager.itemCount
                val lastVisible = manager.findLastVisibleItemPosition()
                if (lastVisible >= total - 4) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun setupActions() {
        binding.productsSearchInput.addTextChangedListener(searchWatcher)
        binding.productsSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.productsRefreshButton.setOnClickListener { viewModel.refresh() }
        binding.productsRetryButton.setOnClickListener { viewModel.retry() }
        binding.productsClearFiltersButton.setOnClickListener { viewModel.clearFilters() }
        binding.productsFilterButton.setOnClickListener { showFilterDialog() }
        binding.addProductButton.setOnClickListener {
            findNavController().navigate(
                R.id.adminProductFormFragment,
                bundleOf("mode" to "CREATE", "productId" to null)
            )
        }

        binding.productStatusAllChip.setOnClickListener {
            viewModel.applyFilters(viewModel.uiState.value.filters.copy(status = null))
        }
        binding.productStatusActiveChip.setOnClickListener {
            viewModel.applyFilters(viewModel.uiState.value.filters.copy(status = ProductStatus.ACTIVE))
        }
        binding.productStatusInactiveChip.setOnClickListener {
            viewModel.applyFilters(viewModel.uiState.value.filters.copy(status = ProductStatus.INACTIVE))
        }
        binding.productStatusOutOfStockChip.setOnClickListener {
            viewModel.applyFilters(viewModel.uiState.value.filters.copy(status = ProductStatus.OUT_OF_STOCK))
        }
    }

    private fun showFilterDialog() {
        val state = viewModel.uiState.value
        val sortOptions = ProductSort.entries
        val categoryOptions = viewModel.uiState.value.categoryOptions
        val categoryNames = listOf(getString(R.string.filter_all)) + categoryOptions.map { it.name }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.product_filter_dialog_padding)
            setPadding(padding, 0, padding, 0)
        }
        val sortSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                sortOptions.map { it.backendValue }
            )
            setSelection(sortOptions.indexOf(state.sort).coerceAtLeast(0))
        }
        val categorySpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                categoryNames
            )
            setSelection(
                (categoryOptions.indexOfFirst { it.categoryId == state.filters.categoryId } + 1)
                    .coerceAtLeast(0)
            )
        }
        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.admin_product_sort_label)
        })
        content.addView(sortSpinner)
        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.admin_product_category_filter_label)
        })
        content.addView(categorySpinner)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.admin_orders_filter))
            .setView(content)
            .setNeutralButton(getString(R.string.admin_orders_clear_filters)) { _, _ ->
                viewModel.clearFilters()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ ->
                val categoryPosition = categorySpinner.selectedItemPosition
                val categoryId = if (categoryPosition == 0) {
                    null
                } else {
                    categoryOptions[categoryPosition - 1].categoryId
                }
                viewModel.applyCriteria(
                    filters = state.filters.copy(categoryId = categoryId),
                    sort = sortOptions[sortSpinner.selectedItemPosition]
                )
            }
            .show()
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ProductEvent.SessionExpired -> authCoordinatorViewModel.onSessionExpired()
                        is ProductEvent.Message -> com.daily.nexamartpartner.core.widgets.UiFeedback
                            .showSnackbar(binding.root, event.text)
                        is ProductEvent.ActionSucceeded,
                        is ProductEvent.SavedSuccessfully -> Unit
                    }
                }
            }
        }
    }

    private fun render(state: ProductListUiState) {
        binding.productsSwipeRefresh.isRefreshing = state.isRefreshing
        binding.productsPaginationProgress.isVisible = state.isLoadingMore
        when (val content = state.content) {
            is ProductListUiState.Content.Loading -> {
                binding.productsLoading.isVisible = true
                binding.productsRecyclerView.isVisible = false
                binding.productsStateSection.isVisible = false
            }

            is ProductListUiState.Content.Success -> {
                binding.productsLoading.isVisible = false
                binding.productsRecyclerView.isVisible = true
                binding.productsStateSection.isVisible = false
                adapter.submitList(content.products)
            }

            is ProductListUiState.Content.Empty -> {
                binding.productsLoading.isVisible = false
                binding.productsRecyclerView.isVisible = false
                binding.productsStateSection.isVisible = true
                binding.productsStateTitleText.text = getString(R.string.admin_products_empty_title)
                binding.productsStateDescriptionText.text = content.message
                binding.productsClearFiltersButton.isVisible = content.showClearFilters
                binding.productsRetryButton.isVisible = false
            }

            is ProductListUiState.Content.Error -> {
                binding.productsLoading.isVisible = false
                binding.productsRecyclerView.isVisible = false
                binding.productsStateSection.isVisible = true
                binding.productsStateTitleText.text = getString(R.string.admin_products_error_title)
                binding.productsStateDescriptionText.text = content.message
                binding.productsClearFiltersButton.isVisible = false
                binding.productsRetryButton.isVisible = true
            }

            is ProductListUiState.Content.Unavailable -> {
                binding.productsLoading.isVisible = false
                binding.productsRecyclerView.isVisible = false
                binding.productsStateSection.isVisible = true
                binding.productsStateTitleText.text = getString(R.string.admin_products_unavailable_title)
                binding.productsStateDescriptionText.text = content.message
                binding.productsClearFiltersButton.isVisible = false
                binding.productsRetryButton.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        binding.productsSearchInput.removeTextChangedListener(searchWatcher)
        binding.productsRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

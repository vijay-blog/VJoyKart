package com.nexamart.customer.presentation.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentSearchBinding
import com.nexamart.customer.model.Product
import com.nexamart.customer.presentation.common.cartQuantities
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    val remoteProducts: List<Product>? = null,
    val error: String? = null,
)

/** Port of SearchScreen: 300 ms debounce, remote `/catalog/products?search=`, stale results ignored. */
class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQueryChanged(raw: String) {
        val query = raw.trim()
        job?.cancel()
        if (query.isEmpty()) {
            _state.value = SearchState(query = "")
            return
        }
        _state.value = _state.value.copy(query = query, searching = true, error = null)
        job = viewModelScope.launch {
            delay(300)
            try {
                val result = catalog.searchRemote(query)
                _state.value = _state.value.copy(remoteProducts = result, searching = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(remoteProducts = emptyList(), error = "Unable to search products", searching = false)
            }
        }
    }
}

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchViewModel(requireContext().appContainer.catalog) as T
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = requireContext().appContainer
        val grid = ProductGrid(this, binding.grid)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.searchInput.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        if (savedInstanceState == null) {
            binding.searchInput.requestFocus()
            binding.searchInput.post {
                requireContext().getSystemService<InputMethodManager>()
                    ?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        launchOnStarted {
            combine(viewModel.state, container.catalog.state, container.cart.items) { s, c, items -> Triple(s, c, items) }
                .collect { (search, catalog, items) ->
                    grid.adapter.submitCart(cartQuantities(items))
                    when {
                        search.error != null -> grid.showError(search.error) {
                            viewModel.onQueryChanged(binding.searchInput.text.toString())
                        }
                        catalog.error != null && search.query.isEmpty() -> grid.showError(getString(R.string.unable_load_products), null)
                        search.searching -> grid.showLoading()
                        else -> grid.showProducts(
                            if (search.query.isEmpty()) catalog.products else search.remoteProducts.orEmpty(),
                            getString(R.string.no_products_found),
                        )
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

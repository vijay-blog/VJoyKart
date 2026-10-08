package com.nexamart.customer.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentMainBinding
import com.nexamart.customer.presentation.cart.CartFragment
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.home.HomeFragment
import com.nexamart.customer.presentation.orders.OrdersFragment
import com.nexamart.customer.presentation.auth.LoginForOrdersFragment
import com.nexamart.customer.presentation.profile.ProfileFragment
import com.nexamart.customer.repository.CartMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-scoped selected tab, so other screens can return to a specific tab (e.g. Continue Shopping). */
class MainTabsViewModel : ViewModel() {
    private val _tab = MutableStateFlow(TAB_HOME)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    fun select(tab: Int) {
        _tab.value = tab.coerceIn(TAB_HOME, TAB_PROFILE)
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_ORDERS = 1
        const val TAB_CART = 2
        const val TAB_PROFILE = 3
    }
}

/** Returns to the single existing Home screen, clearing checkout/order screens above it. */
fun Fragment.returnToMainTab(tab: Int) {
    val tabs = androidx.lifecycle.ViewModelProvider(requireActivity())[MainTabsViewModel::class.java]
    tabs.select(tab)
    val nav = findNavController()
    if (!nav.popBackStack(R.id.mainFragment, false)) {
        // Main screen is not in the back stack (should not happen): start a clean one.
        val options = NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
        runCatching { nav.navigate(R.id.mainFragment, null, options) }
    }
}

/** Port of HomeScreen's NavigationBar shell: Home / Orders / Cart (with badge) / Profile. */
class MainFragment : Fragment() {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val tabs: MainTabsViewModel by activityViewModels()

    private val tabIds = listOf(R.id.tab_home, R.id.tab_orders, R.id.tab_cart, R.id.tab_profile)
    private val tags = listOf("tab_home", "tab_orders", "tab_cart", "tab_profile")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val selected = tabIds.indexOf(item.itemId)
            if (selected == MainTabsViewModel.TAB_ORDERS &&
                !requireContext().appContainer.session.isAuthenticated()
            ) {
                findNavController().navigate(R.id.loginForOrdersFragment)
            } else {
                tabs.select(selected)
            }
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }

        val backToHome = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = tabs.select(MainTabsViewModel.TAB_HOME)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backToHome)

        launchOnStarted {
            tabs.tab.collect {
                backToHome.isEnabled = it != MainTabsViewModel.TAB_HOME
                showTab(it)
            }
        }
        launchOnStarted {
            requireContext().appContainer.cart.items.collect { items ->
                val count = CartMath.count(items)
                val badge = binding.bottomNav.getOrCreateBadge(R.id.tab_cart)
                badge.isVisible = count > 0
                badge.number = count
                badge.backgroundColor = requireContext().getColor(R.color.vk_primary)
            }
        }
    }

    private fun showTab(index: Int) {
        if (binding.bottomNav.selectedItemId != tabIds[index]) binding.bottomNav.selectedItemId = tabIds[index]
        val fm = childFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        tags.forEachIndexed { i, tag ->
            val existing = fm.findFragmentByTag(tag)
            if (i == index) {
                if (existing == null) {
                    transaction.add(R.id.tabContainer, createTab(i), tag)
                } else {
                    transaction.show(existing)
                }
            } else if (existing != null && !existing.isHidden) {
                transaction.hide(existing)
            }
        }
        transaction.commitNowAllowingStateLoss()
    }

    private fun createTab(index: Int): Fragment = when (index) {
        MainTabsViewModel.TAB_ORDERS -> OrdersFragment()
        MainTabsViewModel.TAB_CART -> CartFragment.embedded()
        MainTabsViewModel.TAB_PROFILE -> ProfileFragment()
        else -> HomeFragment()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.nexamart.customer.presentation.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port of SplashScreen: brand screen for 1.1 s, then Home (splash removed from the back stack). */
class SplashFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentSplashBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1100)
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.splashFragment, true)
                .setEnterAnim(R.anim.fade_in_short)
                .build()
            runCatching { findNavController().navigate(R.id.mainFragment, null, options) }
        }
    }
}

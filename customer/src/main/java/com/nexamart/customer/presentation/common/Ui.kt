package com.nexamart.customer.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import com.nexamart.customer.util.ImageUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.dpF: Float get() = this * Resources.getSystem().displayMetrics.density

/** Loads a catalog image (absolute URL, backend-relative path or bundled asset) with a placeholder fallback. */
fun ImageView.loadCatalogImage(source: String?) {
    val url = ImageUrls.resolve(source)
    if (url == null) {
        showImagePlaceholder()
        return
    }
    scaleType = ImageView.ScaleType.FIT_CENTER
    imageTintList = null
    load(url) {
        crossfade(true)
        listener(onError = { _, _ -> showImagePlaceholder() })
    }
}

private fun ImageView.showImagePlaceholder() {
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    setImageResource(R.drawable.ic_image_not_supported_outline)
    imageTintList = ContextCompat.getColorStateList(context, R.color.vk_grey)
}

/** Collects while the fragment view is at least STARTED. */
fun Fragment.launchOnStarted(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

fun Fragment.showMessage(message: String) {
    val root = view ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
}

fun Context.openExternal(uri: String, failureMessage: String = "Unable to open this link.") {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

fun Context.dial(phone: String, failureMessage: String = "Unable to open the phone dialer.") {
    try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

/** Fragment-scoped ViewModel built from the app container (with a SavedStateHandle for process death). */
inline fun <reified VM : androidx.lifecycle.ViewModel> Fragment.appViewModels(
    crossinline create: (com.nexamart.customer.AppContainer, androidx.lifecycle.SavedStateHandle) -> VM,
): Lazy<VM> = viewModels<VM>(
    factoryProducer = {
        androidx.lifecycle.viewmodel.viewModelFactory {
            initializer { create(requireContext().appContainer, createSavedStateHandle()) }
        }
    },
)

package com.daily.nexamartpartner.features.admin.presentation.ui

import android.os.Bundle
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.widgets.UiFeedback
import com.daily.nexamartpartner.core.config.AppConfig
import com.daily.nexamartpartner.databinding.FragmentAdminProductFormBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.presentation.state.ProductFormUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductEvent
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductFormViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.ProductFormViewModelFactory
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import kotlinx.coroutines.launch
import com.daily.nexamartpartner.features.admin.domain.model.ProductImageUpload
import coil.load

class ProductFormScreen : Fragment(R.layout.fragment_admin_product_form) {
    private var _binding: FragmentAdminProductFormBinding? = null
    private val binding: FragmentAdminProductFormBinding
        get() = requireNotNull(_binding)

    private val mode by lazy {
        if (requireArguments().getString(ARG_MODE) == "EDIT") {
            ProductFormUiState.Mode.EDIT
        } else {
            ProductFormUiState.Mode.CREATE
        }
    }
    private val productId by lazy { requireArguments().getString(ARG_PRODUCT_ID) }

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: ProductFormViewModel by viewModels {
        val container = requireContext().appContainer
        ProductFormViewModelFactory(
            mode = mode,
            productId = productId,
            getProductDetails = if (mode == ProductFormUiState.Mode.EDIT) {
                container.provideGetProductDetailsUseCase()
            } else {
                null
            },
            getCategoryOptions = container.provideGetProductCategoryOptionsUseCase(),
            createProduct = container.provideCreateProductUseCase(),
            updateProduct = container.provideUpdateProductUseCase(),
            uploadProductImage = container.provideUploadProductImageUseCase()
        )
    }

    private var categoryAdapter: ArrayAdapter<String>? = null
    private var categoryIdsByName: Map<String, String> = emptyMap()
    private var suppressCategorySelectionCallback = false

    /** Prevents programmatic `setText` calls during state rendering from being treated as user edits. */
    private var suppressFieldWatchers = false
    private var selectedImageUpload: ProductImageUpload? = null

    private val productImagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            handleSelectedProductImage(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminProductFormBinding.bind(view)
        binding.productFormTitleText.text = getString(
            if (mode == ProductFormUiState.Mode.EDIT) {
                R.string.admin_product_form_edit_title
            } else {
                R.string.admin_product_form_create_title
            }
        )
        setupBackHandling()
        setupFieldWatchers()
        binding.productFormRetryButton.setOnClickListener { viewModel.retry() }
        binding.productSelectImageButton.setOnClickListener {
            productImagePicker.launch("image/*")
        }
        binding.productRemoveImageButton.setOnClickListener {
            selectedImageUpload = null
            viewModel.onImageSelectionCleared()
            binding.productImagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        binding.productFormSaveButton.setOnClickListener { viewModel.save() }
        collectUi()
        collectEvents()
    }

    private fun setupBackHandling() {
        binding.productFormBackButton.setOnClickListener { attemptNavigateBack() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    attemptNavigateBack()
                }
            }
        )
    }

    private fun attemptNavigateBack() {
        if (viewModel.hasUnsavedChanges()) {
            UiFeedback.showConfirmationDialog(
                anchor = binding.root,
                title = getString(R.string.admin_product_form_unsaved_title),
                message = getString(R.string.admin_product_form_unsaved_message),
                positiveActionText = getString(R.string.admin_product_form_discard),
                negativeActionText = getString(R.string.admin_product_form_keep_editing),
                onConfirmed = { findNavController().navigateUp() }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    private fun setupFieldWatchers() {
        binding.productNameInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onNameChanged(it?.toString().orEmpty())
        }
        binding.productDescriptionInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onDescriptionChanged(it?.toString().orEmpty())
        }
        binding.productPriceInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onPriceChanged(it?.toString().orEmpty())
        }
        binding.productDiscountInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onDiscountChanged(it?.toString().orEmpty())
        }
        binding.productStockInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onStockChanged(it?.toString().orEmpty())
        }
        binding.productSkuInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onSkuChanged(it?.toString().orEmpty())
        }
        binding.productUnitInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onUnitChanged(it?.toString().orEmpty())
        }
        binding.productImageUrlInput.doAfterTextChanged {
            if (!suppressFieldWatchers) viewModel.onImageUrlChanged(it?.toString().orEmpty())
        }

        binding.productCategoryInput.setOnItemClickListener { parent, _, position, _ ->
            if (suppressCategorySelectionCallback) return@setOnItemClickListener
            val selectedName = parent.getItemAtPosition(position) as? String
            viewModel.onCategorySelected(categoryIdsByName[selectedName])
        }
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
                        is ProductEvent.Message -> UiFeedback.showSnackbar(binding.root, event.text)
                        is ProductEvent.ActionSucceeded -> Unit
                        is ProductEvent.SavedSuccessfully -> {
                            UiFeedback.showSnackbar(binding.root, getString(R.string.admin_product_form_save))
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: ProductFormUiState) {
        binding.productFormLoading.isVisible = state.isLoadingDetails &&
            state.content is ProductFormUiState.Content.Loading
        binding.productFormFieldsSection.isVisible = state.content == ProductFormUiState.Content.Editing
        binding.productFormStateSection.isVisible =
            state.content is ProductFormUiState.Content.Error || state.content is ProductFormUiState.Content.Unavailable

        when (val content = state.content) {
            is ProductFormUiState.Content.Error -> {
                binding.productFormStateTitleText.text = getString(R.string.admin_products_error_title)
                binding.productFormStateMessageText.text = content.message
                binding.productFormRetryButton.isVisible = true
            }

            is ProductFormUiState.Content.Unavailable -> {
                binding.productFormStateTitleText.text = getString(R.string.admin_products_unavailable_title)
                binding.productFormStateMessageText.text = content.message
                binding.productFormRetryButton.isVisible = false
            }

            else -> Unit
        }

        renderFields(state)
        binding.productFormSaveButton.isEnabled = !state.isSaving
    }

    private fun renderFields(state: ProductFormUiState) {
        suppressFieldWatchers = true
        if (binding.productNameInput.text?.toString() != state.name) {
            binding.productNameInput.setText(state.name)
        }
        if (binding.productDescriptionInput.text?.toString() != state.description) {
            binding.productDescriptionInput.setText(state.description)
        }
        if (binding.productPriceInput.text?.toString() != state.price) {
            binding.productPriceInput.setText(state.price)
        }
        if (binding.productDiscountInput.text?.toString() != state.discountPercent) {
            binding.productDiscountInput.setText(state.discountPercent)
        }
        if (binding.productStockInput.text?.toString() != state.stock) {
            binding.productStockInput.setText(state.stock)
        }
        if (binding.productSkuInput.text?.toString() != state.sku) {
            binding.productSkuInput.setText(state.sku)
        }
        if (binding.productUnitInput.text?.toString() != state.unit) {
            binding.productUnitInput.setText(state.unit)
        }
        if (binding.productImageUrlInput.text?.toString() != state.imageUrl) {
            binding.productImageUrlInput.setText(state.imageUrl)
        }
        if (selectedImageUpload == null && state.imageUrl.isNotBlank()) {
            val previewUrl = if (state.imageUrl.startsWith("/")) {
                AppConfig.baseUrl.removeSuffix("/api/v1") + state.imageUrl
            } else {
                state.imageUrl
            }
            binding.productImagePreview.load(previewUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        }
        binding.productImageFileNameText.text = state.imageFileName ?: getString(R.string.admin_product_form_no_photo)
        suppressFieldWatchers = false

        renderCategoryOptions(state)

        binding.productNameInputLayout.error = state.fieldErrors.name
        binding.productCategoryInputLayout.error = state.fieldErrors.category
        binding.productPriceInputLayout.error = state.fieldErrors.price
        binding.productDiscountInputLayout.error = state.fieldErrors.discount
        binding.productStockInputLayout.error = state.fieldErrors.stock

        binding.productCategoryUnavailableText.isVisible = state.categoryOptionsUnavailableMessage != null
        binding.productCategoryUnavailableText.text = state.categoryOptionsUnavailableMessage
            ?: getString(R.string.admin_product_form_categories_unavailable)
    }

    private fun renderCategoryOptions(state: ProductFormUiState) {
        val names = state.categoryOptions.map { it.name }
        categoryIdsByName = state.categoryOptions.associateBy({ it.name }, { it.categoryId })
        if (categoryAdapter == null) {
            categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names.toMutableList())
            binding.productCategoryInput.setAdapter(categoryAdapter)
        } else {
            categoryAdapter?.clear()
            categoryAdapter?.addAll(names)
        }

        val selectedName = state.categoryOptions.firstOrNull { it.categoryId == state.selectedCategoryId }?.name.orEmpty()
        if (binding.productCategoryInput.text?.toString() != selectedName) {
            suppressCategorySelectionCallback = true
            binding.productCategoryInput.setText(selectedName, false)
            suppressCategorySelectionCallback = false
        }
    }

    private fun handleSelectedProductImage(uri: Uri) {
        val resolver = requireContext().contentResolver
        val contentType = resolver.getType(uri)?.lowercase().orEmpty()
        if (contentType !in setOf("image/jpeg", "image/png", "image/webp")) {
            UiFeedback.showSnackbar(binding.root, "Please select a JPG, PNG, or WebP image.")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read the selected image.")
                if (bytes.isEmpty()) throw IllegalStateException("Selected image is empty.")
                if (bytes.size > 5 * 1024 * 1024) {
                    UiFeedback.showSnackbar(binding.root, "Product photo must be 5 MB or smaller.")
                    return@launch
                }
                val upload = ProductImageUpload(
                    bytes = bytes,
                    fileName = "product_${System.currentTimeMillis()}.${
                        when (contentType) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            else -> "jpg"
                        }
                    }",
                    contentType = contentType
                )
                selectedImageUpload = upload
                viewModel.onImageSelected(upload)
                binding.productImagePreview.setImageURI(uri)
                binding.productImageFileNameText.text = upload.fileName
            } catch (t: Throwable) {
                UiFeedback.showSnackbar(binding.root, t.message ?: "Unable to select product photo.")
            }
        }
    }

    override fun onDestroyView() {
        categoryAdapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_PRODUCT_ID = "productId"
    }
}

package com.nexamart.customer.presentation.address

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentAddressFormBinding
import com.nexamart.customer.model.Address
import com.nexamart.customer.presentation.common.Nav.ARG_ADDRESS_ID
import com.nexamart.customer.presentation.common.openExternal
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.presentation.common.visibleIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.StringTokenizer
import kotlin.coroutines.resume
import android.location.Address as GeoAddress

/** Port of AddressFormScreen, including "Use Current Location" (GPS + reverse geocoding). */
class AddressFormFragment : Fragment() {
    private var _binding: FragmentAddressFormBinding? = null
    private val binding get() = _binding!!

    private var existing: Address? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var locating = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (_binding == null) return@registerForActivityResult
        if (result.values.any { it } || hasLocationPermission()) {
            fetchLocation()
        } else {
            setLocating(false)
            val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (permanentlyDenied) {
                Snackbar.make(
                    binding.root,
                    "Location permission is permanently denied. Please enable it in Settings.",
                    Snackbar.LENGTH_LONG,
                ).setAction("Settings") { openAppSettings() }.show()
            } else {
                showMessage("Location permission was denied.")
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddressFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = requireContext().appContainer
        val addressId = arguments?.getString(ARG_ADDRESS_ID)
        existing = addressId?.let { id -> container.addresses.state.value.addresses.firstOrNull { it.id == id } }
        val a = existing

        binding.toolbar.title = if (a == null) "Add Address" else "Edit Address"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        if (savedInstanceState == null) {
            binding.nameInput.setText(a?.name.orEmpty())
            binding.mobileInput.setText(a?.mobile.orEmpty())
            binding.houseInput.setText(a?.house.orEmpty())
            binding.streetInput.setText(a?.street.orEmpty())
            binding.areaInput.setText(a?.area.orEmpty())
            binding.cityInput.setText(a?.city ?: "Hyderabad")
            binding.stateInput.setText(a?.state ?: "Telangana")
            binding.pincodeInput.setText(a?.pincode.orEmpty())
            binding.defaultSwitch.isChecked = a?.isDefault ?: false
            latitude = a?.latitude
            longitude = a?.longitude
            if (a == null) {
                val phone = container.session.customerPhone
                if (!phone.isNullOrEmpty() && binding.mobileInput.text.isNullOrEmpty()) {
                    binding.mobileInput.setText(phone)
                }
            }
        } else {
            latitude = savedInstanceState.getDoubleOrNull(KEY_LAT)
            longitude = savedInstanceState.getDoubleOrNull(KEY_LNG)
        }

        setLocating(false)
        binding.locationButton.setOnClickListener { useCurrentLocation() }
        binding.mapButton.setOnClickListener {
            val lat = latitude
            val lng = longitude
            if (lat != null && lng != null) requireContext().openExternal(mapsUrl(lat, lng))
        }
        binding.saveButton.setOnClickListener { save() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        latitude?.let { outState.putDouble(KEY_LAT, it) }
        longitude?.let { outState.putDouble(KEY_LNG, it) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setLocating(value: Boolean) {
        locating = value
        val b = _binding ?: return
        b.locationButton.isEnabled = !value
        b.locationButton.text = if (value) "Detecting location..." else "Use Current Location"
        b.locationButton.icon = if (value) null else ContextCompat.getDrawable(requireContext(), R.drawable.ic_my_location)
        b.locationProgress.visibleIf(value)
        b.mapButton.visibleIf(latitude != null && longitude != null)
    }

    private fun hasLocationPermission(): Boolean {
        val context = requireContext()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun useCurrentLocation() {
        if (locating) return
        setLocating(true)
        val manager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            setLocating(false)
            showMessage("Location services are turned off. Please enable GPS and try again.")
            return
        }
        if (hasLocationPermission()) {
            fetchLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    private fun fetchLocation() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val position = currentLocation(context)
                    ?: throw IllegalStateException("Unable to detect your current location. Please try again.")
                latitude = position.latitude
                longitude = position.longitude
                val place = reverseGeocode(context, position.latitude, position.longitude)
                if (place != null) applyPlacemark(place)
                showMessage(
                    if (place != null) "Current location detected and address fields updated."
                    else "Current location detected. Please fill in the address fields.",
                )
            } catch (e: SecurityException) {
                showMessage("Location permission was denied.")
            } catch (e: Exception) {
                showMessage(e.message ?: "Unable to detect your current location. Please try again.")
            } finally {
                setLocating(false)
            }
        }
    }

    /** Same field mapping as the Flutter geocoding Placemark logic. */
    private fun applyPlacemark(p: GeoAddress) {
        val street = p.getAddressLine(0)?.takeIf { it.isNotEmpty() }?.let { line ->
            StringTokenizer(line, ",", false).takeIf { it.hasMoreTokens() }?.nextToken()
        }
        val lines = mutableListOf<String>()
        for (value in listOf(p.featureName, street, p.subLocality)) {
            val v = value.orEmpty().trim()
            if (v.isNotEmpty() && v !in lines) lines.add(v)
        }
        val b = binding
        if (lines.isNotEmpty()) b.houseInput.setText(lines[0])
        if (lines.size > 1) b.streetInput.setText(lines[1])
        if (lines.size > 2) b.areaInput.setText(lines[2]) else p.locality?.let { b.areaInput.setText(it) }
        b.cityInput.setText((p.locality ?: p.subAdminArea ?: b.cityInput.text.toString()).trim())
        b.stateInput.setText((p.adminArea ?: b.stateInput.text.toString()).trim())
        b.pincodeInput.setText((p.postalCode ?: b.pincodeInput.text.toString()).trim())
    }

    private fun save() {
        val b = binding
        val fields = listOf(
            Triple(b.nameLayout, b.nameInput, "Full Name"),
            Triple(b.mobileLayout, b.mobileInput, "Mobile"),
            Triple(b.houseLayout, b.houseInput, "House / Flat"),
            Triple(b.streetLayout, b.streetInput, "Street"),
            Triple(b.areaLayout, b.areaInput, "Area"),
            Triple(b.cityLayout, b.cityInput, "City"),
            Triple(b.stateLayout, b.stateInput, "State"),
            Triple(b.pincodeLayout, b.pincodeInput, "Pincode"),
        )
        var valid = true
        for ((layout, input, label) in fields) {
            val error = validate(label, input.text?.toString().orEmpty())
            layout.error = error
            if (error != null) valid = false
        }
        if (!valid) return

        fun TextInputEditText.value() = text?.toString().orEmpty().trim()
        val item = Address(
            id = existing?.id,
            name = b.nameInput.value(),
            mobile = b.mobileInput.value(),
            house = b.houseInput.value(),
            street = b.streetInput.value(),
            area = b.areaInput.value(),
            city = b.cityInput.value(),
            state = b.stateInput.value(),
            pincode = b.pincodeInput.value(),
            country = "India",
            latitude = latitude,
            longitude = longitude,
            isDefault = b.defaultSwitch.isChecked,
        )
        requireContext().appContainer.addresses.save(item)
        findNavController().navigateUp()
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null)),
            )
        } catch (_: Exception) {
            showMessage("Unable to open Settings.")
        }
    }

    companion object {
        private const val KEY_LAT = "latitude"
        private const val KEY_LNG = "longitude"
        private const val LOCATION_TIMEOUT_MS = 20_000L

        /** Same validators as the Flutter form (Mobile/Pincode use regex, other fields are required). */
        fun validate(label: String, value: String): String? = when (label) {
            "Mobile" -> if (Regex("^[6-9]\\d{9}$").matches(value)) null else "Enter valid 10-digit mobile"
            "Pincode" -> if (Regex("^\\d{6}$").matches(value)) null else "Enter valid 6-digit pincode"
            else -> if (value.trim().isEmpty()) "$label is required" else null
        }

        private fun Bundle.getDoubleOrNull(key: String): Double? = if (containsKey(key)) getDouble(key) else null

        @SuppressLint("MissingPermission")
        private suspend fun currentLocation(context: Context): Location? {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val providers = buildList {
                if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            }
            for (provider in providers) {
                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context),
                        ) { location -> if (cont.isActive) cont.resume(location) }
                    }
                }
                if (location != null) return location
            }
            return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }

        private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): GeoAddress? {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            lat,
                            lng,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<GeoAddress>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }
}

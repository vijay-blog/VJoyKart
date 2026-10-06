package com.nexamart.customer.repository

import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.model.Address
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.asJsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AddressState(val addresses: List<Address> = emptyList(), val selected: Address? = null)

/** Port of lib/providers/address_provider.dart. Addresses are stored on the device only. */
class AddressRepository(
    private val prefs: CustomerPrefs,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AddressState> = _state.asStateFlow()

    private fun load(): AddressState {
        val addresses = prefs.getStringList(CustomerPrefs.ADDRESSES).orEmpty().mapNotNull { raw ->
            try {
                Json.decode(raw).asJsonMap()?.let(Address::fromJson)
            } catch (_: Exception) {
                null
            }
        }
        val selectedId = prefs.getString(CustomerPrefs.SELECTED_ADDRESS_ID)
        val selected = selectedId?.let { id -> addresses.firstOrNull { it.id == id } }
            ?: addresses.firstOrNull { it.isDefault }
            ?: addresses.firstOrNull()
        return AddressState(addresses, selected)
    }

    private fun publish(state: AddressState) {
        _state.value = state
        prefs.putStringList(CustomerPrefs.ADDRESSES, state.addresses.map { Json.encode(it.toJson()) })
        state.selected?.id?.let { prefs.putString(CustomerPrefs.SELECTED_ADDRESS_ID, it) }
    }

    fun save(address: Address): Address {
        val id = if (address.id.isNullOrEmpty()) "addr_${now()}" else address.id
        val item = address.copy(id = id)
        val list = _state.value.addresses
            .map { if (item.isDefault) it.copy(isDefault = false) else it }
            .toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        if (index >= 0) list[index] = item else list.add(0, item)
        publish(AddressState(list, item))
        return item
    }

    fun select(address: Address) = publish(_state.value.copy(selected = address))

    fun setDefault(address: Address) {
        val list = _state.value.addresses.map { it.copy(isDefault = it.id == address.id) }
        publish(AddressState(list, list.firstOrNull { it.id == address.id }))
    }

    fun delete(address: Address) {
        val list = _state.value.addresses.filterNot { it.id == address.id }
        val selected = _state.value.selected.takeUnless { it?.id == address.id } ?: list.firstOrNull()
        publish(AddressState(list, selected))
    }

    fun clear() {
        _state.value = AddressState()
        prefs.remove(CustomerPrefs.ADDRESSES, CustomerPrefs.SELECTED_ADDRESS_ID)
    }
}

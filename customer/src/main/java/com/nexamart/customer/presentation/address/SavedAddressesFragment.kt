package com.nexamart.customer.presentation.address

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentSavedAddressesBinding
import com.nexamart.customer.databinding.ItemAddressBinding
import com.nexamart.customer.model.Address
import com.nexamart.customer.presentation.common.Nav.openAddressForm
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.openExternal
import com.nexamart.customer.presentation.common.visibleIf

/** Port of SavedAddressesScreen (addresses are stored on the device, as in Flutter). */
class SavedAddressesFragment : Fragment() {
    private var _binding: FragmentSavedAddressesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSavedAddressesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = requireContext().appContainer.addresses
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.addButton.setOnClickListener { openAddressForm(null) }
        binding.useLocationButton.setOnClickListener { openAddressForm(null) }

        val adapter = AddressAdapter(
            onSelect = repo::select,
            onEdit = { openAddressForm(it.id) },
            onSetDefault = repo::setDefault,
            onDelete = repo::delete,
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        launchOnStarted {
            repo.state.collect { state ->
                binding.emptyState.visibleIf(state.addresses.isEmpty())
                binding.list.visibleIf(state.addresses.isNotEmpty())
                adapter.selectedId = state.selected?.id
                adapter.submitList(state.addresses)
                adapter.notifyItemRangeChanged(0, state.addresses.size)
            }
        }
    }

    override fun onDestroyView() {
        binding.list.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private class AddressAdapter(
    private val onSelect: (Address) -> Unit,
    private val onEdit: (Address) -> Unit,
    private val onSetDefault: (Address) -> Unit,
    private val onDelete: (Address) -> Unit,
) : ListAdapter<Address, AddressAdapter.Holder>(Diff) {
    var selectedId: String? = null

    class Holder(val binding: ItemAddressBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemAddressBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val a = getItem(position)
        val b = holder.binding
        val context = b.root.context
        val selected = selectedId != null && selectedId == a.id
        b.iconTile.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (selected) R.color.vk_primary_soft else R.color.vk_bg),
        )
        b.icon.setImageResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_home_outline)
        b.icon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (selected) R.color.vk_primary else R.color.vk_grey_700),
        )
        b.name.text = a.name
        b.defaultBadge.visibleIf(a.isDefault)
        b.address.text = a.oneLine
        b.mobile.text = a.mobile
        val lat = a.latitude
        val lng = a.longitude
        b.mapButton.visibleIf(lat != null && lng != null)
        b.mapButton.setOnClickListener {
            if (lat != null && lng != null) context.openExternal(mapsUrl(lat, lng))
        }
        b.card.setOnClickListener { onSelect(a) }
        b.menuButton.setOnClickListener { anchor ->
            PopupMenu(context, anchor).apply {
                menu.add(0, 1, 0, "Edit")
                menu.add(0, 2, 1, "Set as default")
                menu.add(0, 3, 2, "Delete")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onEdit(a)
                        2 -> onSetDefault(a)
                        else -> onDelete(a)
                    }
                    true
                }
            }.show()
        }
    }

    private object Diff : DiffUtil.ItemCallback<Address>() {
        override fun areItemsTheSame(oldItem: Address, newItem: Address) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Address, newItem: Address) = oldItem == newItem
    }
}

internal fun mapsUrl(lat: Double, lng: Double) = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"

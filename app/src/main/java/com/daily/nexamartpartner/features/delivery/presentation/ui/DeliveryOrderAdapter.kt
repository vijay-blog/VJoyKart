package com.daily.nexamartpartner.features.delivery.presentation.ui

import android.view.*
import androidx.recyclerview.widget.*
import com.daily.nexamartpartner.core.format.ValueFormatter
import com.daily.nexamartpartner.databinding.ItemDeliveryOrderBinding
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderSummary

class DeliveryOrderAdapter(private val click:(DeliveryOrderSummary)->Unit):ListAdapter<DeliveryOrderSummary,DeliveryOrderAdapter.VH>(Diff){
 override fun onCreateViewHolder(p:ViewGroup,v:Int)=VH(ItemDeliveryOrderBinding.inflate(LayoutInflater.from(p.context),p,false),click)
 override fun onBindViewHolder(h:VH,pos:Int){h.bind(getItem(pos))}
 class VH(private val b:ItemDeliveryOrderBinding,private val click:(DeliveryOrderSummary)->Unit):RecyclerView.ViewHolder(b.root){fun bind(x:DeliveryOrderSummary){b.orderIdText.text="Order #${x.orderId}";b.customerText.text=x.customerName.ifBlank{"Customer unavailable"};b.addressText.text=x.address ?: "Address unavailable";b.statusText.text="${x.statusLabel ?: x.status}  •  Pickup: ${x.pickupName}";if(x.totalAmount!=null)b.amountText.text=ValueFormatter.formatCurrency(x.totalAmount,x.currencyCode) else b.amountText.text="Amount unavailable";b.root.setOnClickListener{click(x)}}}
 object Diff:DiffUtil.ItemCallback<DeliveryOrderSummary>(){override fun areItemsTheSame(a:DeliveryOrderSummary,b:DeliveryOrderSummary)=a.orderId==b.orderId;override fun areContentsTheSame(a:DeliveryOrderSummary,b:DeliveryOrderSummary)=a==b}
}

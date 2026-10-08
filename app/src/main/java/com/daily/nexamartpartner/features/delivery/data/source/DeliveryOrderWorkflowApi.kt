package com.daily.nexamartpartner.features.delivery.data.source

import com.daily.nexamartpartner.features.delivery.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface DeliveryOrderWorkflowApi {
    @GET suspend fun getAssignedOrders(@Url path:String,@QueryMap query:Map<String,String>):Response<DeliveryOrdersPageDto>
    @GET suspend fun getOrderDetails(@Url path:String):Response<DeliveryOrderDetailsDto>
    @POST suspend fun performAction(@Url path:String,@Body body:Map<String,String>):Response<Unit>
}

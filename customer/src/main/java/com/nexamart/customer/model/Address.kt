package com.nexamart.customer.model

import com.nexamart.customer.util.JsonMap
import com.nexamart.customer.util.jsonDoubleOrNull
import com.nexamart.customer.util.jsonString

/** Port of lib/models/address.dart. Addresses are stored on the device only (as in Flutter). */
data class Address(
    val id: String? = null,
    val name: String,
    val mobile: String,
    val house: String,
    val street: String,
    val area: String,
    val city: String,
    val state: String,
    val pincode: String,
    val country: String = "India",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isDefault: Boolean = false,
) {
    val oneLine: String get() = "$house, $street, $area, $city - $pincode"
    val fullAddress: String get() = oneLine

    /** Same request shape the Flutter app sent to POST /customer/orders. */
    fun toJson(): JsonMap = linkedMapOf<String, Any?>().apply {
        if (id != null) put("id", id)
        put("label", "Home")
        put("name", name)
        put("recipientName", name)
        put("fullName", name)
        put("mobile", mobile)
        put("phone", mobile)
        put("house", house)
        put("line1", house)
        put("street", street)
        put("line2", street)
        put("area", area)
        put("landmark", area)
        put("addressLine", listOf(house, street, area).filter { it.trim().isNotEmpty() }.joinToString(", "))
        put("city", city)
        put("state", state)
        put("pincode", pincode)
        put("postalCode", pincode)
        put("country", country)
        put("latitude", latitude)
        put("longitude", longitude)
        put("isDefault", isDefault)
        put("defaultAddress", isDefault)
    }

    companion object {
        fun fromJson(json: JsonMap): Address = Address(
            id = json["id"].jsonString(),
            name = (json["name"] ?: json["fullName"]).jsonString().orEmpty(),
            mobile = (json["mobile"] ?: json["mobileNumber"]).jsonString().orEmpty(),
            house = (json["house"] ?: json["houseFlat"]).jsonString().orEmpty(),
            street = json["street"].jsonString().orEmpty(),
            area = json["area"].jsonString().orEmpty(),
            city = json["city"].jsonString() ?: "Hyderabad",
            state = json["state"].jsonString() ?: "Telangana",
            pincode = json["pincode"].jsonString().orEmpty(),
            country = json["country"].jsonString() ?: "India",
            latitude = json["latitude"].jsonDoubleOrNull(),
            longitude = json["longitude"].jsonDoubleOrNull(),
            isDefault = json["isDefault"] == true,
        )
    }
}

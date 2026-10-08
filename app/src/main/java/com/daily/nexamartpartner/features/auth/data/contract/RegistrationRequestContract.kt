package com.daily.nexamartpartner.features.auth.data.contract

import com.daily.nexamartpartner.features.auth.domain.model.RegistrationCredentials

class RegistrationRequestContract {
    fun buildBody(credentials: RegistrationCredentials): Map<String, String> = mapOf(
        "name" to credentials.name.trim(),
        "email" to credentials.email.trim().lowercase(),
        "phone" to credentials.phone.filter { it.isDigit() || it == '+' },
        "password" to credentials.password
    )
}

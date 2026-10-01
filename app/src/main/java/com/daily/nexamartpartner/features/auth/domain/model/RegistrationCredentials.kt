package com.daily.nexamartpartner.features.auth.domain.model

data class RegistrationCredentials(
    val name: String,
    val email: String,
    val password: String,
    val phone: String = ""
)

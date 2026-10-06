package com.nexamart.customer.repository

import com.nexamart.customer.data.network.ApiClient

class AccountRepository(private val api: ApiClient) {
    suspend fun deleteAccount() {
        api.execute { api.customerApi.deleteAccount() }
    }
}

package com.ksm.draftcsrapp

import retrofit2.Response
import retrofit2.http.GET

data class Customer(
    val customerId: Int,
    val customerName: String?,
    val address: String?,
    val contactPerson: String?,
    val displayText: String?
) {
    fun label(): String = displayText?.takeIf { it.isNotBlank() } ?: customerName.orEmpty()
}

data class CustomerListResponse(
    val message: String?,
    val data: List<Customer>?
)

interface CustomerApiService {
    @GET("api/ksm/GetCustomerIT")
    suspend fun getCustomers(): Response<CustomerListResponse>
}

package com.ksm.draftcsrapp

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class DraftListResponse(
    val message: String?,
    val data: DraftPage?
)

data class DraftPage(
    val pageNumber: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<DraftItem>
)

// Dipakai untuk GetDraftServiceReportIT (Beranda) maupun GetServiceReportIT (halaman
// Service Report): bentuk item kedua endpoint sama, hanya errorMessage yang khusus draft.
data class DraftItem(
    val serviceReportId: Int,
    val registrationNumber: String?,
    val username: String?,
    val createDate: String?,
    val reportDate: String?,
    val kategori: String?,
    val totalHours: String?,
    val customerName: String?,
    val serialNumber: String?,
    val modelName: String?,
    val codeDescription: String?,
    val isCustomerSigned: Boolean?,
    val modelInfo: String?,
    val serviceType: String?,
    val errorMessage: String?
)

interface DraftApiService {
    @GET("api/ksm/GetDraftServiceReportIT")
    suspend fun getDrafts(
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int,
        @Query("Search") search: String?,
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?
    ): Response<DraftListResponse>

    // Semua service report (Draft, External, Internal)
    @GET("api/ksm/GetServiceReportIT")
    suspend fun getServiceReports(
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int,
        @Query("search") search: String?
    ): Response<DraftListResponse>
}

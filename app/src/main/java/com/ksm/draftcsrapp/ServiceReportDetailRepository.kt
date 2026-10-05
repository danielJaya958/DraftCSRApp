package com.ksm.draftcsrapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class ReportNotFoundException : Exception("Report tidak ditemukan")

class ServiceReportDetailRepository @Inject constructor(
    private val apiService: ServiceReportDetailApiService
) {
    suspend fun getDetail(serviceReportId: Int): Result<ServiceReportDetailResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getDetail(serviceReportId)
                val body = response.body()
                when {
                    response.isSuccessful && body?.serviceReportIT != null -> Result.success(body)
                    response.isSuccessful || response.code() == 404 ->
                        Result.failure(ReportNotFoundException())
                    response.code() == 401 -> Result.failure(SessionExpiredException())
                    else -> Result.failure(Exception("Gagal memuat detail report (${response.code()})"))
                }
            } catch (e: IOException) {
                Result.failure(NoConnectionException())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

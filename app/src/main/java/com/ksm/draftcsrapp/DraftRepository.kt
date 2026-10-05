package com.ksm.draftcsrapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

// Dilempar saat access token & refresh token sama-sama tidak valid
class SessionExpiredException : Exception("Sesi berakhir, silakan login ulang")

// Dibedakan dari error lain supaya UI bisa menampilkan pesan "periksa koneksi internet"
class NoConnectionException : Exception("Tidak dapat terhubung ke server")

class DraftRepository @Inject constructor(
    private val apiService: DraftApiService
) {
    suspend fun getDrafts(
        page: Int,
        size: Int,
        search: String? = null
    ): Result<DraftPage> = fetchPage(page, size, "Gagal memuat draft") {
        apiService.getDrafts(page, size, search, null, null)
    }

    suspend fun getServiceReports(
        page: Int,
        size: Int,
        search: String? = null
    ): Result<DraftPage> = fetchPage(page, size, "Gagal memuat service report") {
        apiService.getServiceReports(page, size, search?.takeIf { it.isNotBlank() })
    }

    private suspend fun fetchPage(
        page: Int,
        size: Int,
        errorLabel: String,
        call: suspend () -> Response<DraftListResponse>
    ): Result<DraftPage> = withContext(Dispatchers.IO) {
        try {
            val response = call()
            when {
                response.isSuccessful ->
                    Result.success(response.body()?.data ?: emptyPage(page, size))
                response.code() == 401 -> Result.failure(SessionExpiredException())
                response.code() == 404 -> Result.success(emptyPage(page, size))
                else -> Result.failure(Exception("$errorLabel (${response.code()})"))
            }
        } catch (e: IOException) {
            Result.failure(NoConnectionException())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun emptyPage(page: Int, size: Int) = DraftPage(page, size, 0, 0, emptyList())
}

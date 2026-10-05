package com.ksm.draftcsrapp

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Retrofit terpisah (tanpa interceptor/authenticator) supaya tidak ada dependency melingkar
interface RefreshApi {
    @POST("api/auth/refresh-token")
    fun refresh(@Body request: RefreshRequest): Call<RefreshResponse>
}

// Dipanggil OkHttp otomatis saat backend balas 401
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    baseUrl: String
) : Authenticator {

    private val refreshApi: RefreshApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RefreshApi::class.java)

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Jangan refresh untuk endpoint auth (login / refresh-token itu sendiri)
        if (response.request.url.encodedPath.contains("/api/auth/")) return null
        // Sudah dicoba 2x -> menyerah
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        // Thread lain mungkin sudah refresh: pakai token terbaru
        val current = tokenManager.getToken()
        val sentWith = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (current != null && sentWith != current) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $current")
                .build()
        }

        return try {
            val result = refreshApi.refresh(RefreshRequest(refreshToken)).execute()
            val data = result.body()?.data
            if (result.isSuccessful && data != null) {
                tokenManager.saveToken(data.accessToken)
                tokenManager.saveRefreshToken(data.refreshToken) // refresh token ikut berganti
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${data.accessToken}")
                    .build()
            } else {
                tokenManager.clear() // refresh token ditolak -> harus login ulang
                null
            }
        } catch (e: Exception) {
            null // masalah jaringan: token tidak dihapus
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

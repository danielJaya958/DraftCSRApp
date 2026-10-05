package com.ksm.draftcsrapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: AuthApiService,
    private val tokenManager: TokenManager
) {
    // Menggunakan Flow untuk memancarkan hasil
    fun login(request: LoginRequest): Flow<Result<String>> = flow {
        try {
            val response = apiService.login(request)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                tokenManager.saveToken(data.accessToken) // Simpan token secara aman
                tokenManager.saveRefreshToken(data.refreshToken)
                emit(Result.success(response.body()?.message ?: "Login Berhasil"))
            } else {
                val detail = response.errorBody()?.string()
                emit(Result.failure(Exception("Login gagal: ${response.code()} $detail")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    // Tukar refresh token jadi access token baru. True = sesi masih hidup.
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = tokenManager.getRefreshToken() ?: return@withContext false
        try {
            val response = apiService.refreshToken(RefreshRequest(refreshToken))
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                tokenManager.saveToken(data.accessToken)
                tokenManager.saveRefreshToken(data.refreshToken)
                true
            } else {
                tokenManager.clear() // refresh token ditolak -> login ulang
                false
            }
        } catch (e: Exception) {
            false // masalah jaringan: token dibiarkan
        }
    }
}

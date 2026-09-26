package com.ksm.draftcsrapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: AuthApiService,
    private val tokenManager: TokenManager
) {
    // Menggunakan Flow untuk memancarkan hasil
    fun login(request: LoginRequest): Flow<Result<String>> = flow {
        try {
            val response = apiService.login(request)
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!.token
                tokenManager.saveToken(token) // Simpan token secara aman
                emit(Result.success("Login Berhasil"))
            } else {
                emit(Result.failure(Exception("Login gagal: ${response.code()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}
package com.ksm.draftcsrapp

import retrofit2.http.Body
import retrofit2.http.POST

// Data Classes
data class LoginRequest(
    val username: String, // backend terima username ATAU email di field ini
    val password: String
)

data class LoginResponse(
    val message: String,
    val data: LoginData?
)

data class LoginData(
    val username: String,
    val email: String,
    val codename: String,
    val role: List<Int>,
    val accessToken: String,
    val refreshToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class RefreshData(
    val accessToken: String,
    val refreshToken: String
)

data class RefreshResponse(
    val message: String,
    val data: RefreshData?
)

// Antarmuka Retrofit
interface AuthApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<LoginResponse>

    @POST("api/auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshRequest): retrofit2.Response<RefreshResponse>
}

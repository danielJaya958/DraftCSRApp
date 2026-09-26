package com.ksm.draftcsrapp

import retrofit2.http.Body
import retrofit2.http.POST

// Data Classes
data class LoginRequest(
    val usernameOrEmail: String,
    val password: String
)

data class LoginResponse(
    val token: String, // Sesuaikan dengan key JWT dari backend Anda
    val message: String
)

// Antarmuka Retrofit
interface AuthApiService {
    @POST("api/auth/login") // Ganti dengan rute login ASP.NET Anda
    suspend fun login(@Body request: LoginRequest): retrofit2.Response<LoginResponse>
}
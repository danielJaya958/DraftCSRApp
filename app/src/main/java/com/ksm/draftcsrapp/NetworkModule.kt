package com.ksm.draftcsrapp

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://192.168.1.7:5210/"

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager = TokenManager(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                tokenManager.getToken()?.let { token ->
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .authenticator(TokenAuthenticator(tokenManager, BASE_URL))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        // Gunakan 10.0.2.2 untuk mengakses localhost (port 5210) dari Android Emulator
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDraftApiService(retrofit: Retrofit): DraftApiService {
        return retrofit.create(DraftApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCustomerApiService(retrofit: Retrofit): CustomerApiService {
        return retrofit.create(CustomerApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDraftFormApiService(retrofit: Retrofit): DraftFormApiService {
        return retrofit.create(DraftFormApiService::class.java)
    }
}

package com.ksm.draftcsrapp

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import javax.inject.Singleton

data class ServiceReportDetailResponse(
    val serviceReportIT: ServiceReportDetailHeader?,
    val questions: List<DetailQuestion>?
)

// Field unit (serialNumber dst.) hanya dikirim untuk kategori tertentu, jadi semuanya nullable.
// Gson mengubah angka menjadi String, sehingga tahun/draftDuration aman bertipe String.
data class ServiceReportDetailHeader(
    val serviceReportId: Int,
    val registrationNumber: String?,
    val username: String?,
    val createDate: String?,
    val serviceReportSubmitDate: String?,
    val draftDuration: String?,
    val kategori: String?,
    val serialNumber: String?,
    val modelName: String?,
    val manufacture: String?,
    val modelCategory: String?,
    val modelStatus: String?,
    val customerName: String?,
    val tglInstall: String?,
    val tahun: String?
)

data class DetailQuestion(
    val questionId: Int,
    val questionName: String?,
    // "date", "text", "textarea", "checkbox", "table", "newcustomer", atau null (report Internal)
    val questionType: String?,
    val answer: DetailAnswer?
)

data class DetailAnswer(
    val answerId: Int?,
    val answerText: String?,
    val attachmentFileName: String?,
    val attachmentDownloadUrl: String?
)

interface ServiceReportDetailApiService {
    @GET("api/ksm/GetServiceReportITDetail/{id}")
    suspend fun getDetail(@Path("id") serviceReportId: Int): Response<ServiceReportDetailResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceReportDetailModule {
    @Provides
    @Singleton
    fun provideServiceReportDetailApiService(retrofit: Retrofit): ServiceReportDetailApiService =
        retrofit.create(ServiceReportDetailApiService::class.java)
}

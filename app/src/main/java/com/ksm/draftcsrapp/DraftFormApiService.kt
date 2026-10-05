package com.ksm.draftcsrapp

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

object QuestionTypes {
    const val TEXT = "text"
    const val TEXTAREA = "textarea"
    const val DROPDOWN = "dropdown"
    const val DATE = "date"
    const val DATETIME = "datetime"
    const val CUSTOMER_MODEL = "customer-modelsunit-cascading-dropdown"
    const val TABLE = "table"
    const val ATTACHFILE = "attachfile"
}

data class QuestionColumn(val columnName: String?, val columnType: String?, val options: List<String>?)
data class Question(
    val questionId: Int,
    val templateId: Int,
    val questionName: String?,
    val questionType: String?,
    val option: List<String>?,
    val order: Int,
    val isMandatory: Boolean,
    val width: String?,
    val direction: String?,
    val row: Int?,
    val column: Int?,
    val parentQuestionId: Int?,
    val triggerValue: String?,
    val columns: List<QuestionColumn>?
)
data class QuestionListResponse(val message: String?, val data: List<Question>?)

data class SubAnswer(val ColumnName: String, val ColumnAnswer: String)
data class AnswerPayload(val QuestionId: Int, val AnswerText: String? = null, val Answers: List<SubAnswer>? = null)
data class AddAnswerRequest(val TemplateId: Int, val CustomerId: Int?, val Answers: List<AnswerPayload>)
data class AddAnswerResponse(val message: String?, val serviceReportId: Int?)

// --- Detail draft: GET GetServiceReportITDetail/{id} ---
data class ServiceReportITSummary(
    val serviceReportId: Int,
    val registrationNumber: String?,
    val username: String?,
    val createDate: String?,
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
data class DetailAnswer(
    val answerId: Int?,
    val answerText: String?,
    val attachmentFileName: String?,
    val attachmentDownloadUrl: String?
)
data class DetailQuestion(
    val questionId: Int,
    val questionName: String?,
    val questionType: String?,
    val answer: DetailAnswer?
)
data class ServiceReportDetailResponse(
    val serviceReportIT: ServiceReportITSummary?,
    val questions: List<DetailQuestion>?
)

// --- Edit jawaban draft: PUT UpdateDraftAnswers (JSON biasa) ---
data class UpdateAnswerItem(val QuestionId: Int, val AnswerText: String)
data class UpdateDraftAnswersRequest(val ServiceReportITId: Int, val Answers: List<UpdateAnswerItem>)
data class UpdateDraftAnswersResponse(val message: String?, val data: Any? = null)

interface DraftFormApiService {
    @POST("api/ksm/GetSpecificQuestions/{templateId}")
    suspend fun getQuestions(@Path("templateId") templateId: Int, @Body questionIds: List<Int>): Response<QuestionListResponse>

    // AddAnswerDraftIT dibaca backend dengan [FromForm], jadi harus dikirim sebagai
    // application/x-www-form-urlencoded dengan key bertingkat (mis. "Answers[0].QuestionId"),
    // bukan JSON.
    @FormUrlEncoded
    @POST("api/ksm/AddAnswerDraftIT")
    suspend fun addAnswer(@FieldMap fields: Map<String, String>): Response<AddAnswerResponse>

    @GET("api/ksm/GetServiceReportITDetail/{id}")
    suspend fun getDraftDetail(@Path("id") id: Int): Response<ServiceReportDetailResponse>

    // Edit jawaban draft yang sudah ada: endpoint ini pakai JSON body biasa (bukan form-urlencoded)
    @PUT("api/ksm/UpdateDraftAnswers")
    suspend fun updateDraftAnswers(@Body request: UpdateDraftAnswersRequest): Response<UpdateDraftAnswersResponse>

    // "Buat CSR" (Save Draft To CSR IT): sama seperti AddAnswerDraftIT, form-urlencoded
    // dengan notasi bertingkat.
    @FormUrlEncoded
    @PATCH("api/ksm/UpdateDraftWithAttachment")
    suspend fun updateDraftWithAttachment(@FieldMap fields: Map<String, String>): Response<UpdateDraftAnswersResponse>

    // Daftar Model/Unit milik satu pelanggan (dropdown Model/Unit di layar Buat CSR).
    // Dibaca sebagai JsonElement supaya tetap jalan walau nama field-nya belum pasti
    // (lihat extractModelUnitLabels di DraftFormRepository).
    @GET("api/ksm/dropdown/modelsunit-LIS")
    suspend fun getModelUnits(@Query("customerId") customerId: Int): Response<JsonElement>
}

package com.ksm.draftcsrapp

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

// Template yang dipakai untuk Add/Edit Draft: hanya 2 pertanyaan
// (Error Message & Customer And Model), sesuai contoh "Get Draft Questions"
// dari dokumentasi.
const val DRAFT_TEMPLATE_ID = 29
val DRAFT_TEMPLATE_QUESTION_IDS = listOf(3506, 3515)

// Set pertanyaan lengkap yang dipakai untuk "Buat CSR" (Save Draft To CSR IT),
// sesuai daftar Answers[] pada contoh dokumentasi UpdateDraftWithAttachment,
// ditambah Time Start (4941) & Time End (4942).
val CSR_TEMPLATE_QUESTION_IDS = listOf(3500, 3506, 3507, 3508, 3509, 3515, 4939, 4941, 4942)

// Nama field yang dicoba (urut prioritas, tidak peka huruf besar/kecil) untuk teks yang
// ditampilkan & dikirim sebagai ModelUnit dari response modelsunit-LIS.
private val MODEL_UNIT_LABEL_KEYS = listOf(
    "displayText", "modelUnit", "modelsUnit", "modelUnitName", "text", "label", "name", "modelName"
)

// Response modelsunit-LIS bisa berupa array langsung, atau dibungkus {"data": [...]}
// / {"data": {"items": [...]}}. Ambil array pertama yang ketemu.
private fun findFirstArray(el: JsonElement?): JsonArray? {
    if (el == null || el.isJsonNull) return null
    if (el.isJsonArray) return el.asJsonArray
    if (!el.isJsonObject) return null
    val obj = el.asJsonObject
    return listOf("data", "items", "result").firstNotNullOfOrNull { key -> findFirstArray(obj.get(key)) }
}

private fun extractModelUnitLabels(root: JsonElement?): List<String> {
    val array = findFirstArray(root) ?: return emptyList()
    return array.mapNotNull { el ->
        val raw = when {
            el.isJsonPrimitive -> el.asString
            el.isJsonObject -> {
                val entries = el.asJsonObject.entrySet()
                MODEL_UNIT_LABEL_KEYS.firstNotNullOfOrNull { key ->
                    entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        ?.value
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.takeIf { it.isNotBlank() }
                }
            }
            else -> null
        }
        raw?.trim()?.takeIf { it.isNotEmpty() }
    }.distinct()
}

// Jawaban "Customer And Model" disimpan backend sebagai string JSON, contoh:
// {"CustomerName":"AMPANA","SerialNumber":"","ModelName":"","Manufacture":"","ModelUnit":""}
// Kalau bukan JSON (mis. draft lama yang sempat tersimpan sebagai teks polos sebelum
// flatText() di bawah diperbaiki), anggap seluruh teksnya adalah nama pelanggan.
fun parseCustomerModelAnswer(text: String): Pair<String?, String?> = try {
    val obj = JSONObject(text)
    val name = obj.optString("CustomerName").takeIf { it.isNotBlank() }
    val unit = obj.optString("ModelUnit").takeIf { it.isNotBlank() }
    name to unit
} catch (e: Exception) {
    (text.takeIf { it.isNotBlank() }) to null
}

class DraftFormRepository @Inject constructor(
    private val formApi: DraftFormApiService,
    private val customerApi: CustomerApiService
) {
    suspend fun getQuestions(): Result<List<Question>> = fetchQuestions(DRAFT_TEMPLATE_QUESTION_IDS)

    suspend fun getCsrQuestions(): Result<List<Question>> = fetchQuestions(CSR_TEMPLATE_QUESTION_IDS)

    private suspend fun fetchQuestions(ids: List<Int>): Result<List<Question>> = withContext(Dispatchers.IO) {
        try {
            val response = formApi.getQuestions(DRAFT_TEMPLATE_ID, ids)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                Result.success(data.sortedBy { it.order })
            } else {
                Result.failure(Exception("Gagal memuat pertanyaan (${response.code()})"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCustomers(): Result<List<Customer>> = withContext(Dispatchers.IO) {
        try {
            val response = customerApi.getCustomers()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                Result.success(data)
            } else {
                Result.failure(Exception("Gagal memuat data pelanggan (${response.code()})"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelUnits(customerId: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = formApi.getModelUnits(customerId)
            if (response.isSuccessful) {
                val labels = extractModelUnitLabels(response.body())
                if (labels.isEmpty()) {
                    // Bantu debug kalau nama field-nya ternyata beda dari MODEL_UNIT_LABEL_KEYS
                    Log.w("DraftFormRepository", "modelsunit-LIS kosong / format tak dikenal: ${response.body()}")
                }
                Result.success(labels)
            } else {
                val detail = response.errorBody()?.string()
                Log.e("DraftFormRepository", "modelsunit-LIS gagal (${response.code()}): $detail")
                Result.failure(Exception("Gagal memuat model/unit (${response.code()})"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDraftDetail(serviceReportId: Int): Result<ServiceReportDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val response = formApi.getDraftDetail(serviceReportId)
            val data = response.body()
            if (response.isSuccessful && data != null) {
                Result.success(data)
            } else {
                Result.failure(Exception("Gagal memuat detail draft (${response.code()})"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitDraft(
        customerId: Int?,
        answers: List<AnswerPayload>
    ): Result<AddAnswerResponse> = withContext(Dispatchers.IO) {
        val fields = buildFormFields("TemplateId", DRAFT_TEMPLATE_ID.toString(), customerId, answers)
        try {
            val response = formApi.addAnswer(fields)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val detail = response.errorBody()?.string()
                Log.e("DraftFormRepository", "AddAnswerDraftIT gagal (${response.code()}): $detail")
                Log.e("DraftFormRepository", "Form fields dikirim: $fields")
                Result.failure(Exception("Gagal menyimpan draft (${response.code()}) $detail"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Edit jawaban draft yang sudah ada. Endpoint ini JSON biasa (bukan form-urlencoded), dan
    // dari contoh dokumentasi semua jawaban dikirim sebagai AnswerText rata (termasuk
    // Customer And Model, cukup nama pelanggannya saja).
    suspend fun updateDraft(
        serviceReportId: Int,
        answers: List<AnswerPayload>
    ): Result<UpdateDraftAnswersResponse> = withContext(Dispatchers.IO) {
        val items = answers.map { UpdateAnswerItem(it.QuestionId, it.flatText()) }
        val request = UpdateDraftAnswersRequest(serviceReportId, items)
        try {
            val response = formApi.updateDraftAnswers(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val detail = response.errorBody()?.string()
                Log.e("DraftFormRepository", "UpdateDraftAnswers gagal (${response.code()}): $detail")
                Log.e("DraftFormRepository", "Request: $request")
                Result.failure(Exception("Gagal menyimpan perubahan (${response.code()}) $detail"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // "Buat CSR" (Save Draft To CSR IT): form-urlencoded dengan notasi bertingkat, tanpa
    // CustomerId (lihat contoh dokumentasi UpdateDraftWithAttachment).
    suspend fun submitCsr(
        serviceReportId: Int,
        answers: List<AnswerPayload>
    ): Result<UpdateDraftAnswersResponse> = withContext(Dispatchers.IO) {
        val fields = buildFormFields("ServiceReportITId", serviceReportId.toString(), null, answers)
        try {
            val response = formApi.updateDraftWithAttachment(fields)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val detail = response.errorBody()?.string()
                Log.e("DraftFormRepository", "UpdateDraftWithAttachment gagal (${response.code()}): $detail")
                Log.e("DraftFormRepository", "Form fields dikirim: $fields")
                Result.failure(Exception("Gagal membuat CSR (${response.code()}) $detail"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Tidak dapat terhubung ke server"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Backend membaca body AddAnswerDraftIT & UpdateDraftWithAttachment dengan [FromForm], yaitu
    // binding notasi titik/kurung ASP.NET Core untuk objek/array bersarang
    // (contoh: "Answers[0].QuestionId", "Answers[0].Answers[0].ColumnName"). Karena itu payloadnya
    // harus application/x-www-form-urlencoded dengan key-key rata seperti ini, bukan JSON.
    private fun buildFormFields(
        idKey: String,
        idValue: String,
        customerId: Int?,
        answers: List<AnswerPayload>
    ): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        fields[idKey] = idValue
        if (customerId != null) {
            fields["CustomerId"] = customerId.toString()
        }
        answers.forEachIndexed { i, answer ->
            fields["Answers[$i].QuestionId"] = answer.QuestionId.toString()
            if (!answer.AnswerText.isNullOrEmpty()) {
                fields["Answers[$i].AnswerText"] = answer.AnswerText
            }
            answer.Answers?.forEachIndexed { j, sub ->
                fields["Answers[$i].Answers[$j].ColumnName"] = sub.ColumnName
                fields["Answers[$i].Answers[$j].ColumnAnswer"] = sub.ColumnAnswer
            }
        }
        return fields
    }
}

// Jawaban rata (dipakai endpoint JSON UpdateDraftAnswers): AnswerText langsung, atau kalau
// berbentuk sub-jawaban (mis. Customer And Model), dibungkus jadi JSON lagi supaya formatnya
// tetap sama dengan yang dibaca GetServiceReportITDetail (parseCustomerModelAnswer) -- kalau
// cuma dikirim teks nama pelanggan polos, edit berikutnya gagal mengenali nama itu lagi.
private fun AnswerPayload.flatText(): String {
    AnswerText?.let { return it }
    val subs = Answers.orEmpty()
    if (subs.isEmpty()) return ""
    val obj = JSONObject()
    subs.forEach { obj.put(it.ColumnName, it.ColumnAnswer) }
    return obj.toString()
}

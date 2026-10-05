package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class FormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val isEditMode: Boolean = false,
    // Nomor registrasi draft yang sedang diedit (ditampilkan di kartu info)
    val headerInfo: String? = null,
    val questions: List<Question> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val textAnswers: Map<Int, String> = emptyMap(),
    val dateAnswers: Map<Int, Long> = emptyMap(),
    val selectedCustomer: Customer? = null,
    val fieldErrors: Map<Int, String> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submittedReportId: Int? = null
)

// Format tanggal sesuai contoh dari backend, misal "2026-09-22T16:00:00.000Z"
private val isoUtcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun formatIsoUtc(epochMillis: Long): String = isoUtcFormat.format(epochMillis)

// Kebalikan formatIsoUtc, dipakai untuk mengisi ulang field tanggal saat mode edit/Buat CSR
fun parseIsoUtc(text: String): Long? = try {
    isoUtcFormat.parse(text)?.time
} catch (e: Exception) {
    null
}

@HiltViewModel
class AddDraftViewModel @Inject constructor(
    private val repository: DraftFormRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FormUiState())
    val state: StateFlow<FormUiState> = _state.asStateFlow()

    private var started = false
    private var editServiceReportId: Int? = null

    // editServiceReportId null = mode tambah draft baru, non-null = mode edit draft yang sudah ada
    fun start(editServiceReportId: Int?) {
        if (started) return
        started = true
        this.editServiceReportId = editServiceReportId
        loadForm()
    }

    private fun loadForm() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val questionsResult = repository.getQuestions()
            val customersResult = repository.getCustomers()
            val editId = editServiceReportId

            var textAnswers = emptyMap<Int, String>()
            var dateAnswers = emptyMap<Int, Long>()
            var selectedCustomer: Customer? = null
            var detailError: String? = null
            var headerInfo: String? = null

            if (editId != null) {
                repository.getDraftDetail(editId).fold(
                    onSuccess = { detail ->
                        headerInfo = detail.serviceReportIT?.registrationNumber
                        val texts = mutableMapOf<Int, String>()
                        val dates = mutableMapOf<Int, Long>()
                        for (q in detail.questions.orEmpty()) {
                            val answerText = q.answer?.answerText ?: continue
                            when (q.questionType) {
                                QuestionTypes.CUSTOMER_MODEL -> {
                                    val (name, _) = parseCustomerModelAnswer(answerText)
                                    selectedCustomer = name?.let { n ->
                                        customersResult.getOrNull()
                                            ?.firstOrNull { it.customerName.equals(n, ignoreCase = true) }
                                            ?: Customer(
                                                customerId = -1,
                                                customerName = n,
                                                address = null,
                                                contactPerson = null,
                                                displayText = n
                                            )
                                    }
                                }
                                QuestionTypes.DATE, QuestionTypes.DATETIME -> {
                                    parseIsoUtc(answerText)?.let { dates[q.questionId] = it }
                                }
                                else -> texts[q.questionId] = answerText
                            }
                        }
                        textAnswers = texts
                        dateAnswers = dates
                    },
                    onFailure = { detailError = it.message }
                )
            }

            val error = questionsResult.exceptionOrNull()?.message
                ?: customersResult.exceptionOrNull()?.message
                ?: detailError

            _state.update {
                it.copy(
                    loading = false,
                    loadError = error,
                    isEditMode = editId != null,
                    headerInfo = headerInfo,
                    questions = questionsResult.getOrDefault(emptyList()),
                    customers = customersResult.getOrDefault(emptyList()),
                    textAnswers = textAnswers,
                    dateAnswers = dateAnswers,
                    selectedCustomer = selectedCustomer
                )
            }
        }
    }

    fun onTextChanged(questionId: Int, value: String) {
        _state.update {
            it.copy(
                textAnswers = it.textAnswers + (questionId to value),
                fieldErrors = it.fieldErrors - questionId
            )
        }
    }

    fun onDateChanged(questionId: Int, epochMillis: Long) {
        _state.update {
            it.copy(
                dateAnswers = it.dateAnswers + (questionId to epochMillis),
                fieldErrors = it.fieldErrors - questionId
            )
        }
    }

    fun onCustomerSelected(customer: Customer?) {
        _state.update {
            // questionId Customer And Model dikunci di CUSTOMER_QUESTION_ID
            it.copy(selectedCustomer = customer, fieldErrors = it.fieldErrors - CUSTOMER_QUESTION_ID)
        }
    }

    // Dipanggil setelah pesan error submit ditampilkan, supaya pesan tidak muncul berulang
    fun onSubmitErrorShown() {
        _state.update { it.copy(submitError = null) }
    }

    fun submit() {
        val s = _state.value
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }

        val answers = buildAnswers(s)
        val editId = editServiceReportId
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val result = if (editId != null) {
                repository.updateDraft(editId, answers).map { AddAnswerResponse(it.message, editId) }
            } else {
                repository.submitDraft(s.selectedCustomer?.customerId, answers)
            }
            result.fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(submitting = false, submittedReportId = res.serviceReportId ?: editId ?: -1)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(submitting = false, submitError = e.message) }
                }
            )
        }
    }

    private fun validate(s: FormUiState): Map<Int, String> {
        val errors = mutableMapOf<Int, String>()
        for (q in s.questions) {
            if (!q.isMandatory) continue
            when (q.questionType) {
                QuestionTypes.CUSTOMER_MODEL -> {
                    if (s.selectedCustomer == null) errors[q.questionId] = "Pilih pelanggan"
                }
                QuestionTypes.DATE, QuestionTypes.DATETIME -> {
                    if (s.dateAnswers[q.questionId] == null) errors[q.questionId] = "Wajib diisi"
                }
                QuestionTypes.TABLE, QuestionTypes.ATTACHFILE -> {
                    // Belum didukung, tidak divalidasi walau isMandatory true di server
                }
                else -> {
                    if (s.textAnswers[q.questionId].isNullOrBlank()) errors[q.questionId] = "Wajib diisi"
                }
            }
        }
        return errors
    }

    private fun buildAnswers(s: FormUiState): List<AnswerPayload> {
        val list = mutableListOf<AnswerPayload>()
        for (q in s.questions) {
            when (q.questionType) {
                QuestionTypes.CUSTOMER_MODEL -> {
                    val customer = s.selectedCustomer ?: continue
                    list += AnswerPayload(
                        QuestionId = q.questionId,
                        Answers = listOf(SubAnswer("CustomerName", customer.customerName.orEmpty()))
                    )
                }
                QuestionTypes.DATE, QuestionTypes.DATETIME -> {
                    val millis = s.dateAnswers[q.questionId] ?: continue
                    list += AnswerPayload(QuestionId = q.questionId, AnswerText = formatIsoUtc(millis))
                }
                QuestionTypes.TABLE, QuestionTypes.ATTACHFILE -> {
                    // Belum dikirim: format JSON untuk tipe ini belum dikonfirmasi backend
                }
                else -> {
                    val text = s.textAnswers[q.questionId]
                    if (!text.isNullOrBlank()) {
                        list += AnswerPayload(QuestionId = q.questionId, AnswerText = text)
                    }
                }
            }
        }
        return list
    }

    companion object {
        // QuestionId "Customer And Model" (lihat DraftFormRepository) — dipakai untuk
        // menandai error pada field customer di UI
        const val CUSTOMER_QUESTION_ID = 3515
    }
}

package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CsrUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    // Nomor registrasi draft (ditampilkan di kartu info)
    val headerInfo: String? = null,
    val questions: List<Question> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val textAnswers: Map<Int, String> = emptyMap(),
    val dateAnswers: Map<Int, Long> = emptyMap(),
    val selectedCustomer: Customer? = null,
    // Model/Unit bukan pertanyaan tersendiri dari backend, tapi wajib dikirim sebagai
    // sub-jawaban "ModelUnit" pada Customer And Model untuk UpdateDraftWithAttachment.
    // Pilihannya diambil dari GET dropdown/modelsunit-LIS?customerId=...
    val modelUnit: String = "",
    val modelUnitOptions: List<String> = emptyList(),
    val modelUnitsLoading: Boolean = false,
    val modelUnitsMessage: String? = null,
    val fieldErrors: Map<Int, String> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submitted: Boolean = false
)

@HiltViewModel
class CreateCsrViewModel @Inject constructor(
    private val repository: DraftFormRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CsrUiState())
    val state: StateFlow<CsrUiState> = _state.asStateFlow()

    private var started = false
    private var serviceReportId: Int? = null

    fun start(serviceReportId: Int) {
        if (started) return
        started = true
        this.serviceReportId = serviceReportId
        load()
    }

    private fun load() {
        val id = serviceReportId ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val questionsResult = repository.getCsrQuestions()
            val customersResult = repository.getCustomers()
            val detailResult = repository.getDraftDetail(id)

            var textAnswers = emptyMap<Int, String>()
            var dateAnswers = emptyMap<Int, Long>()
            var selectedCustomer: Customer? = null
            var modelUnit = ""
            val detail = detailResult.getOrNull()

            if (detail != null) {
                val texts = mutableMapOf<Int, String>()
                val dates = mutableMapOf<Int, Long>()
                for (q in detail.questions.orEmpty()) {
                    val answerText = q.answer?.answerText ?: continue
                    when (q.questionType) {
                        QuestionTypes.CUSTOMER_MODEL -> {
                            val (name, unit) = parseCustomerModelAnswer(answerText)
                            modelUnit = unit.orEmpty()
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
            }

            val error = questionsResult.exceptionOrNull()?.message
                ?: customersResult.exceptionOrNull()?.message

            val customer = selectedCustomer
            _state.update {
                it.copy(
                    loading = false,
                    loadError = error,
                    headerInfo = detail?.serviceReportIT?.registrationNumber,
                    questions = questionsResult.getOrDefault(emptyList()),
                    customers = customersResult.getOrDefault(emptyList()),
                    textAnswers = textAnswers,
                    dateAnswers = dateAnswers,
                    selectedCustomer = customer,
                    modelUnit = modelUnit,
                    // Nama pelanggan tersimpan tapi tidak ketemu di daftar -> tidak punya id,
                    // jadi daftar model/unit tidak bisa diambil sampai pelanggan dipilih ulang
                    modelUnitsMessage = if (customer != null && customer.customerId <= 0) {
                        "Pilih ulang pelanggan untuk memuat daftar model/unit"
                    } else null
                )
            }

            customer?.customerId?.takeIf { it > 0 }?.let { loadModelUnits(it) }
        }
    }

    private fun loadModelUnits(customerId: Int) {
        _state.update { it.copy(modelUnitsLoading = true, modelUnitsMessage = null) }
        viewModelScope.launch {
            val result = repository.getModelUnits(customerId)
            // Abaikan hasil kalau pelanggan sudah diganti selama request berjalan
            if (_state.value.selectedCustomer?.customerId != customerId) return@launch
            result.fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(
                            modelUnitsLoading = false,
                            modelUnitOptions = list,
                            modelUnitsMessage = if (list.isEmpty()) "Pelanggan ini belum punya data model/unit" else null
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(modelUnitsLoading = false, modelUnitsMessage = e.message) }
                }
            )
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
        val changed = customer?.customerId != _state.value.selectedCustomer?.customerId
        _state.update {
            it.copy(
                selectedCustomer = customer,
                // Model/unit milik pelanggan lama tidak berlaku lagi
                modelUnit = if (changed) "" else it.modelUnit,
                modelUnitOptions = if (changed) emptyList() else it.modelUnitOptions,
                fieldErrors = it.fieldErrors - CUSTOMER_QUESTION_ID
            )
        }
        val id = customer?.customerId
        if (changed && id != null && id > 0) loadModelUnits(id)
    }

    fun onModelUnitChanged(value: String) {
        _state.update { it.copy(modelUnit = value, fieldErrors = it.fieldErrors - MODEL_UNIT_ERROR_KEY) }
    }

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

        val id = serviceReportId ?: return
        val answers = buildAnswers(s)
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            repository.submitCsr(id, answers).fold(
                onSuccess = { _state.update { it.copy(submitting = false, submitted = true) } },
                onFailure = { e -> _state.update { it.copy(submitting = false, submitError = e.message) } }
            )
        }
    }

    private fun validate(s: CsrUiState): Map<Int, String> {
        val errors = mutableMapOf<Int, String>()
        for (q in s.questions) {
            when (q.questionType) {
                QuestionTypes.CUSTOMER_MODEL -> {
                    if (s.selectedCustomer == null) errors[q.questionId] = "Pilih pelanggan"
                    if (s.modelUnit.isBlank()) errors[MODEL_UNIT_ERROR_KEY] = "Pilih model/unit"
                }
                QuestionTypes.DATE, QuestionTypes.DATETIME -> {
                    if (q.isMandatory && s.dateAnswers[q.questionId] == null) {
                        errors[q.questionId] = "Wajib diisi"
                    }
                }
                QuestionTypes.TABLE, QuestionTypes.ATTACHFILE -> Unit
                else -> {
                    if (q.isMandatory && s.textAnswers[q.questionId].isNullOrBlank()) {
                        errors[q.questionId] = "Wajib diisi"
                    }
                }
            }
        }
        return errors
    }

    private fun buildAnswers(s: CsrUiState): List<AnswerPayload> {
        val list = mutableListOf<AnswerPayload>()
        for (q in s.questions) {
            when (q.questionType) {
                QuestionTypes.CUSTOMER_MODEL -> {
                    val customer = s.selectedCustomer ?: continue
                    list += AnswerPayload(
                        QuestionId = q.questionId,
                        Answers = listOf(
                            SubAnswer("CustomerName", customer.customerName.orEmpty()),
                            SubAnswer("ModelUnit", s.modelUnit)
                        )
                    )
                }
                QuestionTypes.DATE, QuestionTypes.DATETIME -> {
                    val millis = s.dateAnswers[q.questionId] ?: continue
                    list += AnswerPayload(QuestionId = q.questionId, AnswerText = formatIsoUtc(millis))
                }
                QuestionTypes.TABLE, QuestionTypes.ATTACHFILE -> Unit
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
        const val CUSTOMER_QUESTION_ID = 3515
    }
}

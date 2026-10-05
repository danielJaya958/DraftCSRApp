package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// Satu baris tabel jawaban: daftar (nama kolom, nilai) sesuai urutan dari backend
typealias DetailTableRow = List<Pair<String, String>>

data class DetailAttachment(val fileName: String, val url: String?)

sealed class DetailRow {
    data class Summary(
        val registrationNumber: String?,
        val kategori: String?,
        val meta: List<Pair<String, String>>
    ) : DetailRow()

    data class Section(val title: String, val caption: String?) : DetailRow()

    data class Field(
        val id: Int,
        val label: String,
        val value: String?,
        val table: List<DetailTableRow>?,
        val attachment: DetailAttachment?
    ) : DetailRow()

    // Item checklist. Pertanyaan "Noted <nama>" milik item ini digabung ke note/table.
    data class Check(
        val id: Int,
        val label: String,
        val done: Boolean,
        val note: String?,
        val table: List<DetailTableRow>?,
        val attachment: DetailAttachment?
    ) : DetailRow()
}

data class DetailUiState(
    val loading: Boolean = true,
    val error: Throwable? = null,
    val rows: List<DetailRow> = emptyList()
)

@HiltViewModel
class ServiceReportDetailViewModel @Inject constructor(
    private val repository: ServiceReportDetailRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var reportId: Int? = null

    fun start(serviceReportId: Int) {
        if (reportId != null) return
        reportId = serviceReportId
        load()
    }

    fun retry() = load()

    private fun load() {
        val id = reportId ?: return
        _state.value = DetailUiState(loading = true)
        viewModelScope.launch {
            repository.getDetail(id).fold(
                onSuccess = { _state.value = DetailUiState(loading = false, rows = buildRows(it)) },
                onFailure = { _state.value = DetailUiState(loading = false, error = it) }
            )
        }
    }

    private fun buildRows(response: ServiceReportDetailResponse): List<DetailRow> {
        val header = response.serviceReportIT ?: return emptyList()
        val questions = response.questions.orEmpty()
        val rows = ArrayList<DetailRow>(questions.size + 4)
        rows += summaryOf(header)

        // "Noted Check UPS" adalah catatan untuk checkbox "Check UPS"
        val checkNames = questions.filter { it.isCheckbox() }.map { it.key() }.toSet()
        val notes = HashMap<String, DetailQuestion>()
        for (q in questions) {
            val key = q.key()
            if (key.startsWith(NOTE_PREFIX) && key.removePrefix(NOTE_PREFIX) in checkNames) {
                notes.putIfAbsent(key.removePrefix(NOTE_PREFIX), q)
            }
        }
        val noteIds = notes.values.map { it.questionId }.toSet()

        val checks = questions.filter { it.isCheckbox() }
        val doneCount = checks.count { it.isDone() }
        val body = questions.filter { it.questionId !in noteIds }

        if (body.isEmpty()) return rows
        rows += DetailRow.Section(
            title = "Isi report",
            caption = if (checks.isEmpty()) null else "$doneCount dari ${checks.size} checklist selesai"
        )
        for (q in body) {
            val label = q.questionName?.trim()?.ifEmpty { null } ?: "Pertanyaan ${q.questionId}"
            if (q.isCheckbox()) {
                val note = notes[q.key()]
                val noteIsTable = note?.questionType.equals("table", ignoreCase = true)
                rows += DetailRow.Check(
                    id = q.questionId,
                    label = label,
                    done = q.isDone(),
                    note = if (noteIsTable) null else note?.answer?.answerText.meaningful(),
                    table = if (noteIsTable) parseTable(note?.answer?.answerText) else null,
                    attachment = attachmentOf(q) ?: note?.let(::attachmentOf)
                )
            } else {
                val isTable = q.questionType.equals("table", ignoreCase = true)
                rows += DetailRow.Field(
                    id = q.questionId,
                    label = label,
                    value = if (isTable) null else displayValue(q),
                    table = if (isTable) parseTable(q.answer?.answerText) else null,
                    attachment = attachmentOf(q)
                )
            }
        }
        return rows
    }

    private fun summaryOf(h: ServiceReportDetailHeader): DetailRow.Summary {
        val meta = ArrayList<Pair<String, String>>()
        fun add(label: String, value: String?) {
            value?.trim()?.ifEmpty { null }?.let { meta += label to it }
        }
        add("Teknisi", h.username)
        add("Dibuat", parseServerDate(h.createDate)?.let(::formatDayTime))
        meta += "Disubmit" to
            (parseServerDate(h.serviceReportSubmitDate)?.let(::formatDayTime) ?: "Belum disubmit")
        add("Durasi draft", h.draftDuration)
        add("Customer", h.customerName)
        add("Model", h.modelName)
        add("Serial number", h.serialNumber)
        add("Manufacture", h.manufacture)
        add("Kategori model", h.modelCategory)
        add("Status model", h.modelStatus)
        add("Tanggal instal", parseServerDate(h.tglInstall)?.let(::formatDay) ?: h.tglInstall)
        add("Tahun", h.tahun)
        return DetailRow.Summary(h.registrationNumber, h.kategori?.trim()?.ifEmpty { null }, meta)
    }

    // Tanggal hanya diformat untuk tipe date/datetime atau tipe null (report Internal).
    // Tipe "text" dibiarkan apa adanya walau isinya mirip tanggal.
    private fun displayValue(q: DetailQuestion): String? {
        val raw = q.answer?.answerText?.trim()?.ifEmpty { null } ?: return null
        val type = q.questionType?.lowercase()
        val mayBeDate = type == null || type.startsWith("date") || type == "time"
        if (!mayBeDate || !DATE_TIME.matches(raw)) return raw
        val date = parseServerDate(raw) ?: return raw
        val cal = Calendar.getInstance().apply { time = date }
        val midnight = cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
        return if (type == "date" || midnight) formatDay(date) else formatDayTime(date)
    }

    @Suppress("DEPRECATION") // JsonParser.parseString baru ada di Gson 2.8.6
    private fun parseTable(json: String?): List<DetailTableRow>? {
        if (json.isNullOrBlank()) return null
        return try {
            JsonParser().parse(json).asJsonArray.mapNotNull { element ->
                val columns = element.asJsonObject.getAsJsonObject("columns") ?: return@mapNotNull null
                columns.entrySet().map { (name, value) ->
                    name to (if (value.isJsonNull) "" else value.asString)
                }.ifEmpty { null }
            }.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun attachmentOf(q: DetailQuestion): DetailAttachment? {
        val name = q.answer?.attachmentFileName?.trim()?.ifEmpty { null } ?: return null
        return DetailAttachment(name, q.answer?.attachmentDownloadUrl?.trim()?.ifEmpty { null })
    }

    private fun DetailQuestion.isCheckbox() = questionType.equals("checkbox", ignoreCase = true)

    private fun DetailQuestion.isDone(): Boolean {
        val v = answer?.answerText?.trim().orEmpty().lowercase()
        return v.isNotEmpty() && v !in NOT_DONE
    }

    private fun DetailQuestion.key() = questionName?.trim().orEmpty().lowercase()

    // "-" dipakai teknisi sebagai "tidak ada catatan"
    private fun String?.meaningful(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "-" }

    private companion object {
        const val NOTE_PREFIX = "noted "
        val NOT_DONE = setOf("false", "0", "no", "not done")
        val DATE_TIME = Regex("""\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}.*""")
    }
}

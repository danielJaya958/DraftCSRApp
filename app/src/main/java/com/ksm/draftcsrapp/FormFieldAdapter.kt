package com.ksm.draftcsrapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.ksm.draftcsrapp.databinding.ItemFieldCustomerBinding
import com.ksm.draftcsrapp.databinding.ItemFieldDateBinding
import com.ksm.draftcsrapp.databinding.ItemFieldDropdownBinding
import com.ksm.draftcsrapp.databinding.ItemFieldTextBinding
import com.ksm.draftcsrapp.databinding.ItemFieldTextareaBinding
import com.ksm.draftcsrapp.databinding.ItemFieldUnsupportedBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TYPE_TEXT = 0
private const val TYPE_TEXTAREA = 1
private const val TYPE_DROPDOWN = 2
private const val TYPE_CUSTOMER = 3
private const val TYPE_DATE = 4
private const val TYPE_UNSUPPORTED = 5

private fun viewTypeOf(q: Question): Int = when (q.questionType) {
    QuestionTypes.TEXT -> TYPE_TEXT
    QuestionTypes.TEXTAREA -> TYPE_TEXTAREA
    QuestionTypes.DROPDOWN -> TYPE_DROPDOWN
    QuestionTypes.CUSTOMER_MODEL -> TYPE_CUSTOMER
    QuestionTypes.DATE, QuestionTypes.DATETIME -> TYPE_DATE
    else -> TYPE_UNSUPPORTED // table, attachfile, atau tipe baru yang belum dikenal
}

// Label field, dengan tanda * merah untuk field wajib
private fun requiredLabel(context: Context, text: String, required: Boolean): CharSequence {
    if (!required) return text
    val sb = SpannableStringBuilder(text).append(" *")
    sb.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, R.color.nd_error)),
        sb.length - 1,
        sb.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return sb
}

private fun Question.label(context: Context) =
    requiredLabel(context, questionName.orEmpty(), isMandatory)

// Id semu (bukan questionId asli dari backend) dipakai untuk menandai error pada field
// Model/Unit, yang hanya tampil ketika includeModelUnitField = true (layar Buat CSR).
const val MODEL_UNIT_ERROR_KEY = -1001

class FormFieldAdapter(
    private val questions: List<Question>,
    initialTextAnswers: Map<Int, String>,
    initialDateAnswers: Map<Int, Long>,
    initialCustomer: Customer?,
    private val customers: List<Customer>,
    private val onTextChanged: (Int, String) -> Unit,
    private val onDateChanged: (Int, Long) -> Unit,
    private val onCustomerSelected: (Customer?) -> Unit,
    private val includeModelUnitField: Boolean = false,
    initialModelUnit: String = "",
    private val onModelUnitChanged: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Sumber kebenaran nilai yang sedang tampil di layar
    private val textValues = HashMap(initialTextAnswers)
    private val dateValues = HashMap(initialDateAnswers)
    private var selectedCustomer: Customer? = initialCustomer
    private var modelUnitValue: String = initialModelUnit
    private var modelUnitOptions: List<String> = emptyList()
    private var modelUnitsLoading = false
    private var modelUnitsMessage: String? = null
    private var errors: Map<Int, String> = emptyMap()

    private val customerPosition: Int
        get() = questions.indexOfFirst { it.questionType == QuestionTypes.CUSTOMER_MODEL }

    fun updateErrors(newErrors: Map<Int, String>) {
        if (newErrors == errors) return
        errors = newErrors
        notifyDataSetChanged()
    }

    // Dipanggil layar Buat CSR tiap state berubah; hanya rebind baris pelanggan kalau ada yang beda
    fun updateModelUnitState(options: List<String>, loading: Boolean, message: String?, value: String) {
        if (options == modelUnitOptions && loading == modelUnitsLoading &&
            message == modelUnitsMessage && value == modelUnitValue
        ) return
        modelUnitOptions = options
        modelUnitsLoading = loading
        modelUnitsMessage = message
        modelUnitValue = value
        customerPosition.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun getItemCount() = questions.size
    override fun getItemViewType(position: Int) = viewTypeOf(questions[position])

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT -> TextVH(ItemFieldTextBinding.inflate(inflater, parent, false))
            TYPE_TEXTAREA -> TextAreaVH(ItemFieldTextareaBinding.inflate(inflater, parent, false))
            TYPE_DROPDOWN -> DropdownVH(ItemFieldDropdownBinding.inflate(inflater, parent, false))
            TYPE_CUSTOMER -> CustomerVH(ItemFieldCustomerBinding.inflate(inflater, parent, false))
            TYPE_DATE -> DateVH(ItemFieldDateBinding.inflate(inflater, parent, false))
            else -> UnsupportedVH(ItemFieldUnsupportedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val q = questions[position]
        val error = errors[q.questionId]
        when (holder) {
            is TextVH -> holder.bind(q, textValues[q.questionId], error)
            is TextAreaVH -> holder.bind(q, textValues[q.questionId], error)
            is DropdownVH -> holder.bind(q, textValues[q.questionId], error)
            is CustomerVH -> holder.bind(
                q,
                selectedCustomer,
                error,
                if (includeModelUnitField) errors[MODEL_UNIT_ERROR_KEY] else null
            )
            is DateVH -> holder.bind(q, dateValues[q.questionId], error)
            is UnsupportedVH -> holder.bind(q)
        }
    }

    // --- Teks satu baris ---
    inner class TextVH(private val b: ItemFieldTextBinding) : RecyclerView.ViewHolder(b.root) {
        private var binding = false
        private var questionId = -1

        init {
            b.etValue.doAfterTextChangedSafe {
                if (!binding) {
                    textValues[questionId] = it
                    onTextChanged(questionId, it)
                }
            }
        }

        fun bind(q: Question, value: String?, error: String?) {
            questionId = q.questionId
            binding = true
            b.tvLabel.text = q.label(b.root.context)
            b.til.error = error
            if (b.etValue.text?.toString() != value) b.etValue.setText(value ?: "")
            binding = false
        }
    }

    // --- Teks panjang ---
    inner class TextAreaVH(private val b: ItemFieldTextareaBinding) : RecyclerView.ViewHolder(b.root) {
        private var binding = false
        private var questionId = -1

        init {
            b.etValue.doAfterTextChangedSafe {
                if (!binding) {
                    textValues[questionId] = it
                    onTextChanged(questionId, it)
                }
            }
        }

        fun bind(q: Question, value: String?, error: String?) {
            questionId = q.questionId
            binding = true
            b.tvLabel.text = q.label(b.root.context)
            b.til.error = error
            if (b.etValue.text?.toString() != value) b.etValue.setText(value ?: "")
            binding = false
        }
    }

    // --- Dropdown pilihan tetap (question.option) ---
    inner class DropdownVH(private val b: ItemFieldDropdownBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(q: Question, value: String?, error: String?) {
            val ctx = b.root.context
            b.tvLabel.text = q.label(ctx)
            b.til.error = error
            val options = q.option.orEmpty()
            b.actvValue.setAdapter(ArrayAdapter(ctx, R.layout.item_dropdown_option, options))
            b.actvValue.setText(value ?: "", false)
            b.actvValue.setOnItemClickListener { parent, _, pos, _ ->
                val picked = parent.getItemAtPosition(pos) as String
                textValues[q.questionId] = picked
                onTextChanged(q.questionId, picked)
            }
        }
    }

    // --- Pelanggan (+ Model/Unit di layar Buat CSR) ---
    inner class CustomerVH(private val b: ItemFieldCustomerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(q: Question, current: Customer?, error: String?, modelUnitError: String?) {
            val ctx = b.root.context
            b.tvLabel.text = q.label(ctx)
            b.tilCustomer.error = error
            b.tilCustomer.helperText = null
            val actv = b.actvCustomer
            val customerAdapter = SearchableDropdownAdapter(ctx, customers.map { it.label() }) { query, count ->
                b.tilCustomer.helperText =
                    if (count == 0 && query.isNotEmpty()) "Pelanggan \"$query\" tidak ditemukan" else null
            }
            actv.setAdapter(customerAdapter)
            actv.threshold = 1
            actv.setText(current?.label() ?: "", false)

            // Tampilkan semua pelanggan saat field disentuh; teks lama terblok supaya langsung
            // tertimpa ketikan baru
            val showAll = {
                customerAdapter.filter.filter(null) { if (actv.hasFocus()) actv.showDropDown() }
            }
            actv.setOnClickListener { if (!actv.isPopupShowing) showAll() }
            actv.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    actv.selectAll()
                    showAll()
                } else {
                    // Pelanggan wajib dipilih dari daftar: ketikan yang tidak dipilih dibuang,
                    // kembali ke pelanggan yang terakhir dipilih
                    actv.setText(selectedCustomer?.label() ?: "", false)
                    b.tilCustomer.helperText = null
                }
            }
            actv.setOnItemClickListener { parent, _, pos, _ ->
                // Pakai item dari adapter (bukan index ke list asli) supaya aman walau list terfilter
                val label = parent.getItemAtPosition(pos) as String
                val picked = customers.firstOrNull { it.label() == label } ?: return@setOnItemClickListener
                if (picked.customerId != selectedCustomer?.customerId) modelUnitValue = ""
                selectedCustomer = picked
                b.tilCustomer.helperText = null
                ctx.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(actv.windowToken, 0)
                onCustomerSelected(picked)
            }

            b.groupModelUnit.isVisible = includeModelUnitField
            if (!includeModelUnitField) return

            b.tvModelUnitLabel.text = requiredLabel(ctx, "Model / Unit", true)
            b.actvModelUnit.setAdapter(ArrayAdapter(ctx, R.layout.item_dropdown_option, modelUnitOptions))
            b.actvModelUnit.setText(modelUnitValue, false)
            val canPick = current != null && current.customerId > 0 && !modelUnitsLoading
            b.tilModelUnit.isEnabled = canPick
            b.tilModelUnit.error = modelUnitError
            b.tilModelUnit.helperText = when {
                modelUnitError != null -> null
                current == null -> "Pilih pelanggan terlebih dahulu"
                modelUnitsLoading -> "Memuat daftar model/unit…"
                else -> modelUnitsMessage
            }
            b.progressModelUnit.isVisible = modelUnitsLoading
            b.actvModelUnit.setOnItemClickListener { parent, _, pos, _ ->
                val picked = parent.getItemAtPosition(pos) as String
                modelUnitValue = picked
                onModelUnitChanged(picked)
            }
        }
    }

    // --- Tanggal / tanggal+jam ---
    inner class DateVH(private val b: ItemFieldDateBinding) : RecyclerView.ViewHolder(b.root) {
        private var questionId = -1
        private var withTime = false

        fun bind(q: Question, epochMillis: Long?, error: String?) {
            questionId = q.questionId
            withTime = q.questionType == QuestionTypes.DATETIME
            b.tvLabel.text = q.label(b.root.context)
            b.til.error = error
            b.etValue.hint = if (withTime) "Pilih tanggal & jam" else "Pilih tanggal"
            b.etValue.setText(epochMillis?.let { format(it) } ?: "")

            val openPicker = {
                val cal = Calendar.getInstance().apply { epochMillis?.let { timeInMillis = it } }
                DatePickerDialog(
                    b.root.context,
                    { _, y, m, d ->
                        cal.set(y, m, d)
                        if (withTime) {
                            TimePickerDialog(
                                b.root.context,
                                { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    cal.set(Calendar.SECOND, 0)
                                    cal.set(Calendar.MILLISECOND, 0)
                                    commit(cal.timeInMillis)
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                true
                            ).show()
                        } else {
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            commit(cal.timeInMillis)
                        }
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            b.etValue.setOnClickListener { openPicker() }
            b.til.setEndIconOnClickListener { openPicker() }
        }

        private fun commit(millis: Long) {
            dateValues[questionId] = millis
            b.etValue.setText(format(millis))
            onDateChanged(questionId, millis)
        }

        private fun format(epochMillis: Long): String {
            val pattern = if (withTime) "EEE, dd MMM yyyy • HH:mm" else "EEE, dd MMM yyyy"
            return SimpleDateFormat(pattern, Locale("id", "ID")).format(epochMillis)
        }
    }

    // --- Tipe belum didukung (table, attachfile, dll) ---
    inner class UnsupportedVH(private val b: ItemFieldUnsupportedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(q: Question) {
            b.tvLabel.text = q.label(b.root.context)
        }
    }
}

// Helper TextWatcher ringkas, menghindari boilerplate 3-method di tiap ViewHolder
private fun com.google.android.material.textfield.TextInputEditText.doAfterTextChangedSafe(
    action: (String) -> Unit
) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = action(s?.toString().orEmpty())
    })
}

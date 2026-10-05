package com.ksm.draftcsrapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ksm.draftcsrapp.databinding.ActivityAddDraftBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddDraftActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddDraftBinding
    private val viewModel: AddDraftViewModel by viewModels()
    private var adapter: FormFieldAdapter? = null
    private var hadFieldErrors = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddDraftBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        val editId = intent.getIntExtra(EXTRA_EDIT_SERVICE_REPORT_ID, -1).takeIf { it != -1 }
        viewModel.start(editId)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { viewModel.submit() }
        binding.rvFields.layoutManager = LinearLayoutManager(this)

        observeState()
    }

    // Status bar & navigation bar + keyboard: konten tidak tertutup, form ikut naik saat mengetik
    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.tvTitle.text = if (s.isEditMode) "Edit Draft" else "Draft Baru"
                    binding.tvSubtitle.text =
                        if (s.isEditMode) "Perbarui jawaban draft laporan servis" else "Isi data awal laporan servis"
                    binding.cardInfo.isVisible = s.isEditMode && !s.headerInfo.isNullOrBlank()
                    binding.tvInfoValue.text = s.headerInfo

                    binding.progress.isVisible = s.loading
                    binding.scrollContent.isVisible = !s.loading && s.loadError == null
                    binding.tvLoadError.isVisible = !s.loading && s.loadError != null
                    binding.tvLoadError.text = s.loadError

                    if (!s.loading && s.loadError == null && adapter == null) {
                        adapter = FormFieldAdapter(
                            questions = s.questions,
                            initialTextAnswers = s.textAnswers,
                            initialDateAnswers = s.dateAnswers,
                            initialCustomer = s.selectedCustomer,
                            customers = s.customers,
                            onTextChanged = viewModel::onTextChanged,
                            onDateChanged = viewModel::onDateChanged,
                            onCustomerSelected = viewModel::onCustomerSelected
                        )
                        binding.rvFields.adapter = adapter
                    }

                    adapter?.updateErrors(s.fieldErrors)
                    if (s.fieldErrors.isNotEmpty() && !hadFieldErrors) {
                        showMessage("Lengkapi field yang wajib diisi", MessageType.WARNING)
                    }
                    hadFieldErrors = s.fieldErrors.isNotEmpty()

                    val submitLabel = if (s.isEditMode) "Simpan Perubahan" else "Simpan Draft"
                    binding.btnSubmit.text = if (s.submitting) "" else submitLabel
                    // Tetap biru saat loading (isEnabled=false bikin tombol jadi abu-abu), cukup blok klik
                    binding.btnSubmit.isClickable = !s.submitting
                    binding.progressSubmit.isVisible = s.submitting

                    s.submitError?.let {
                        showMessage(it, MessageType.ERROR)
                        viewModel.onSubmitErrorShown()
                    }

                    if (s.submittedReportId != null) {
                        showMessage(if (s.isEditMode) "Perubahan draft disimpan" else "Draft berhasil disimpan", MessageType.SUCCESS)
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_SERVICE_REPORT_ID, s.submittedReportId))
                        finish()
                    }
                }
            }
        }
    }


    companion object {
        const val EXTRA_SERVICE_REPORT_ID = "extra_service_report_id"
        const val EXTRA_EDIT_SERVICE_REPORT_ID = "extra_edit_service_report_id"
    }
}

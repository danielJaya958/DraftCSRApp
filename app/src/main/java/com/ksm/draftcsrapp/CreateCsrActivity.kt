package com.ksm.draftcsrapp

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
import com.ksm.draftcsrapp.databinding.ActivityCreateCsrBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateCsrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCsrBinding
    private val viewModel: CreateCsrViewModel by viewModels()
    private var adapter: FormFieldAdapter? = null
    private var hadFieldErrors = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateCsrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        val serviceReportId = intent.getIntExtra(EXTRA_SERVICE_REPORT_ID, -1)
        if (serviceReportId == -1) {
            showMessage("Draft tidak ditemukan", MessageType.ERROR)
            finish()
            return
        }
        viewModel.start(serviceReportId)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { viewModel.submit() }
        binding.rvFields.layoutManager = LinearLayoutManager(this)

        observeState()
    }

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
                    binding.cardInfo.isVisible = !s.headerInfo.isNullOrBlank()
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
                            onCustomerSelected = viewModel::onCustomerSelected,
                            includeModelUnitField = true,
                            initialModelUnit = s.modelUnit,
                            onModelUnitChanged = viewModel::onModelUnitChanged
                        )
                        binding.rvFields.adapter = adapter
                    }

                    adapter?.updateModelUnitState(
                        options = s.modelUnitOptions,
                        loading = s.modelUnitsLoading,
                        message = s.modelUnitsMessage,
                        value = s.modelUnit
                    )
                    adapter?.updateErrors(s.fieldErrors)
                    if (s.fieldErrors.isNotEmpty() && !hadFieldErrors) {
                        showMessage("Lengkapi field yang wajib diisi", MessageType.WARNING)
                    }
                    hadFieldErrors = s.fieldErrors.isNotEmpty()

                    binding.btnSubmit.text = if (s.submitting) "" else "Kirim ke CSR"
                    // Tetap hijau saat loading (isEnabled=false bikin tombol jadi abu-abu), cukup blok klik
                    binding.btnSubmit.isClickable = !s.submitting
                    binding.progressSubmit.isVisible = s.submitting

                    s.submitError?.let {
                        showMessage(it, MessageType.ERROR)
                        viewModel.onSubmitErrorShown()
                    }

                    if (s.submitted) {
                        showMessage("Draft berhasil dijadikan CSR", MessageType.SUCCESS)
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }


    companion object {
        const val EXTRA_SERVICE_REPORT_ID = "extra_service_report_id"
    }
}

package com.ksm.draftcsrapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.ksm.draftcsrapp.databinding.ActivityServiceReportDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Detail service report (baca saja): ringkasan report + jawaban setiap pertanyaan
@AndroidEntryPoint
class ServiceReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceReportDetailBinding
    private val viewModel: ServiceReportDetailViewModel by viewModels()
    private val adapter = ServiceReportDetailAdapter(::openAttachment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityServiceReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val id = intent.getIntExtra(EXTRA_SERVICE_REPORT_ID, -1)
        if (id == -1) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.rvDetail.layoutManager = LinearLayoutManager(this)
        binding.rvDetail.adapter = adapter

        viewModel.start(id)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(s: DetailUiState) {
        if (s.error is SessionExpiredException) {
            // Daftar Service Report yang menangani logout saat dimuat ulang
            showMessage("Sesi berakhir, silakan login ulang", MessageType.ERROR)
            finish()
            return
        }
        binding.loadingView.isVisible = s.loading
        binding.errorView.isVisible = !s.loading && s.error != null
        binding.rvDetail.isVisible = !s.loading && s.error == null
        adapter.submit(s.rows)

        when (val e = s.error) {
            null -> Unit
            is NoConnectionException -> {
                binding.tvErrorTitle.text = "Tidak ada koneksi"
                binding.tvErrorDesc.text =
                    "Aplikasi tidak bisa terhubung ke server. Periksa koneksi internet Anda, lalu coba lagi."
            }
            is ReportNotFoundException -> {
                binding.tvErrorTitle.text = "Report tidak ditemukan"
                binding.tvErrorDesc.text =
                    "Report ini mungkin sudah dihapus. Kembali ke daftar dan muat ulang."
            }
            else -> {
                binding.tvErrorTitle.text = "Detail report gagal dimuat"
                binding.tvErrorDesc.text = e.message ?: "Terjadi kesalahan. Coba lagi beberapa saat lagi."
            }
        }
        binding.btnRetry.isVisible = s.error !is ReportNotFoundException
    }

    private fun openAttachment(attachment: DetailAttachment) {
        val url = attachment.url ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            showMessage("Tidak ada aplikasi untuk membuka lampiran ini", MessageType.WARNING)
        }
    }

    companion object {
        private const val EXTRA_SERVICE_REPORT_ID = "extra_detail_service_report_id"

        fun intent(context: Context, serviceReportId: Int): Intent =
            Intent(context, ServiceReportDetailActivity::class.java)
                .putExtra(EXTRA_SERVICE_REPORT_ID, serviceReportId)
    }
}

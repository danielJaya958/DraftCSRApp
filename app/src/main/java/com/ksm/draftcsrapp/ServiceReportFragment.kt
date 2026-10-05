package com.ksm.draftcsrapp

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.ksm.draftcsrapp.databinding.DialogSrFilterBinding
import com.ksm.draftcsrapp.databinding.FragmentServiceReportBinding
import com.ksm.draftcsrapp.databinding.ItemSrSkeletonBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Tab Service Report: semua report (Draft, External, Internal) dengan pencarian & filter
@AndroidEntryPoint
class ServiceReportFragment : Fragment(R.layout.fragment_service_report) {

    private val viewModel: ServiceReportViewModel by activityViewModels()
    private var _binding: FragmentServiceReportBinding? = null
    private val binding get() = _binding!!
    private val host get() = requireActivity() as MainActivity

    private var skeletonPulse: ObjectAnimator? = null
    private var chips: Map<SrCategory, TextView> = emptyMap()
    private var emptyAction: (() -> Unit)? = null
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"))

    private val adapter = ServiceReportAdapter(
        onClick = { item ->
            if (item.isDraft()) host.openEditDraft(item.serviceReportId)
            else startActivity(
                ServiceReportDetailActivity.intent(requireContext(), item.serviceReportId)
            )
        },
        onLongClick = { item, view ->
            if (item.isDraft()) host.showQuickActions(item.serviceReportId, view)
            else host.showMessage("Edit, Buat CSR & Hapus hanya tersedia untuk report berstatus Draft")
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentServiceReportBinding.bind(view)
        binding.header.applyStatusBarPadding()

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvReports.layoutManager = layoutManager
        binding.rvReports.adapter = adapter
        binding.rvReports.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 4) {
                    viewModel.loadMore()
                }
            }
        })

        repeat(7) { ItemSrSkeletonBinding.inflate(layoutInflater, binding.skeletonRows, true) }

        chips = mapOf(
            SrCategory.ALL to binding.chipAll,
            SrCategory.DRAFT to binding.chipDraft,
            SrCategory.EXTERNAL to binding.chipExternal,
            SrCategory.INTERNAL to binding.chipInternal
        )
        chips.forEach { (category, chip) ->
            chip.text = category.label
            chip.setOnClickListener { viewModel.setCategory(category) }
        }

        if (binding.etSearch.text.isNullOrEmpty() && viewModel.state.value.search.isNotEmpty()) {
            binding.etSearch.setText(viewModel.state.value.search)
        }
        binding.etSearch.doAfterTextChanged { viewModel.setSearch(it?.toString().orEmpty()) }
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                requireContext().getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.btnEmptyAction.setOnClickListener { emptyAction?.invoke() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(s: SrUiState) {
        if (s.sessionExpired) {
            host.logout()
            return
        }

        val showSkeleton = s.initialLoading
        val showError = !showSkeleton && s.error != null && s.loaded.isEmpty()
        val showEmpty = !showSkeleton && !showError && s.visible.isEmpty()

        setSkeletonVisible(showSkeleton)
        binding.errorView.isVisible = showError
        binding.emptyView.isVisible = showEmpty
        binding.rvReports.isVisible = !showSkeleton && !showError && !showEmpty
        binding.progressMore.isVisible = s.loadingMore && !showEmpty

        if (showError) {
            binding.tvErrorDesc.text = if (s.error is NoConnectionException) {
                "Aplikasi tidak bisa terhubung ke server. Periksa koneksi internet Anda, lalu coba lagi."
            } else {
                s.error?.message ?: "Terjadi kesalahan. Coba lagi beberapa saat lagi."
            }
        }
        if (showEmpty) renderEmpty(s)

        adapter.submitList(buildRows(s.visible))

        binding.tvSubtitle.text = when {
            s.initialLoading -> "Memuat…"
            s.isFiltering -> "${numberFormat.format(s.visible.size)} cocok dari " +
                "${numberFormat.format(s.loaded.size)} report yang dimuat"
            s.totalItems == 0 -> "0 report"
            else -> "${numberFormat.format(s.totalItems)} report, halaman ${s.currentPage} dari ${s.totalPages}"
        }

        chips.forEach { (category, chip) -> chip.isSelected = category == s.category }
        val count = s.filter.activeCount
        binding.tvFilter.text = if (count > 0) "Filter ($count)" else "Filter"
    }

    private fun renderEmpty(s: SrUiState) {
        val hasQuery = s.search.isNotEmpty()
        when {
            s.isFiltering && s.loadingMore -> {
                binding.ivEmpty.setImageResource(R.drawable.ic_search)
                binding.tvEmptyTitle.text = "Mencari report yang cocok…"
                binding.tvEmptyDesc.text = "Memeriksa ${numberFormat.format(s.loaded.size)} report yang sudah dimuat."
                setEmptyAction(null, null)
            }
            s.isFiltering && !s.endReached -> {
                binding.ivEmpty.setImageResource(R.drawable.ic_search)
                binding.tvEmptyTitle.text = "Belum ada yang cocok"
                binding.tvEmptyDesc.text = "Tidak ada report yang cocok di ${numberFormat.format(s.loaded.size)} " +
                    "report yang sudah dimuat. Muat halaman berikutnya atau ubah filter."
                setEmptyAction("Muat lebih banyak") { viewModel.loadMore() }
            }
            s.isFiltering || hasQuery -> {
                binding.ivEmpty.setImageResource(R.drawable.ic_search)
                binding.tvEmptyTitle.text = "Tidak ada report yang cocok"
                binding.tvEmptyDesc.text = "Coba kata kunci lain atau ubah filter."
                setEmptyAction("Reset filter") {
                    binding.etSearch.setText("")
                    viewModel.resetFilters()
                }
            }
            else -> {
                binding.ivEmpty.setImageResource(R.drawable.ic_document)
                binding.tvEmptyTitle.text = "Belum ada service report"
                binding.tvEmptyDesc.text = "Report yang Anda buat akan tampil di sini, lengkap dengan nomor " +
                    "registrasi dan status tanda tangan customer."
                setEmptyAction("Buat draft pertama") { host.openAddDraft() }
            }
        }
    }

    private fun setEmptyAction(label: String?, action: (() -> Unit)?) {
        binding.btnEmptyAction.isVisible = label != null
        binding.btnEmptyAction.text = label
        emptyAction = action
    }

    private fun buildRows(items: List<DraftItem>): List<SrRow> {
        val rows = ArrayList<SrRow>(items.size + 8)
        var lastLabel: String? = null
        for (item in items) {
            val label = parseServerDate(item.createDate)?.let { "Dibuat ${formatDay(it)}" }
                ?: "Tanggal dibuat tidak diketahui"
            if (label != lastLabel) {
                rows += SrRow.Header(label)
                lastLabel = label
            }
            rows += SrRow.Report(item)
        }
        return rows
    }

    private fun setSkeletonVisible(visible: Boolean) {
        binding.skeletonView.isVisible = visible
        if (visible && skeletonPulse == null) {
            skeletonPulse = ObjectAnimator.ofFloat(binding.skeletonRows, View.ALPHA, 1f, 0.45f).apply {
                duration = 750
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!visible) {
            skeletonPulse?.cancel()
            skeletonPulse = null
            binding.skeletonRows.alpha = 1f
        }
    }

    // Dialog layar penuh "Filter report": tanggal report, teknisi & status tanda tangan
    private fun showFilterDialog() {
        val ctx = requireContext()
        val s = viewModel.state.value
        var from = s.filter.fromMillis
        var to = s.filter.toMillis

        val dialog = Dialog(ctx, R.style.NdFullScreenDialog)
        val d = DialogSrFilterBinding.inflate(layoutInflater)
        dialog.setContentView(d.root)
        dialog.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = false
        }
        d.topBar.applyStatusBarPadding()
        d.bottomBar.applyNavigationBarPadding()

        fun renderDates() {
            d.tvFrom.text = from?.let { formatDay(Date(it)) }
            d.tvTo.text = to?.let { formatDay(Date(it)) }
        }
        fun pickDate(initial: Long?, onPicked: (Long) -> Unit) {
            val cal = Calendar.getInstance().apply { initial?.let { timeInMillis = it } }
            DatePickerDialog(ctx, { _, y, m, day ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(y, m, day)
                }
                onPicked(picked.timeInMillis)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        d.tvFrom.setOnClickListener {
            pickDate(from) {
                from = it
                if (to != null && to!! < it) to = null
                renderDates()
            }
        }
        d.tvTo.setOnClickListener {
            pickDate(to ?: from) {
                to = it
                if (from != null && from!! > it) from = null
                renderDates()
            }
        }
        renderDates()

        val navy = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.nd_primary))
        val technicians = (s.technicians + s.filter.technicians).distinct()
        val checkBoxes = technicians.map { name ->
            MaterialCheckBox(ctx).apply {
                text = name
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.nd_on_surface))
                buttonTintList = navy
                minHeight = ctx.dp(52)
                isChecked = name in s.filter.technicians
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                d.techContainer.addView(this)
            }
        }
        d.tvNoTechnician.isVisible = technicians.isEmpty()

        d.rgSign.check(
            when (s.filter.sign) {
                SignFilter.ALL -> R.id.rbSignAll
                SignFilter.SIGNED -> R.id.rbSigned
                SignFilter.UNSIGNED -> R.id.rbUnsigned
            }
        )

        d.btnClose.setOnClickListener { dialog.dismiss() }
        d.btnReset.setOnClickListener {
            viewModel.setFilter(SrFilter())
            dialog.dismiss()
        }
        d.btnApply.setOnClickListener {
            viewModel.setFilter(
                SrFilter(
                    fromMillis = from,
                    toMillis = to,
                    technicians = checkBoxes.filter { it.isChecked }.map { it.text.toString() }.toSet(),
                    sign = when (d.rgSign.checkedRadioButtonId) {
                        R.id.rbSigned -> SignFilter.SIGNED
                        R.id.rbUnsigned -> SignFilter.UNSIGNED
                        else -> SignFilter.ALL
                    }
                )
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        skeletonPulse?.cancel()
        skeletonPulse = null
        binding.rvReports.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

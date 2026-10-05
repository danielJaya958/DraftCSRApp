package com.ksm.draftcsrapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ksm.draftcsrapp.databinding.ActivityMainBinding
import com.ksm.draftcsrapp.databinding.LayoutQuickActionCardBinding
import com.ksm.draftcsrapp.databinding.LayoutQuickActionRowBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Host bottom navigation: tab Beranda & Service Report adalah fragment, Plan & Attendance
// belum tersedia. Menu aksi cepat (tekan & tahan draft) juga ditampilkan di sini supaya
// overlay-nya menutupi seluruh layar termasuk bottom bar.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val homeViewModel: HomeViewModel by viewModels()
    private val serviceReportViewModel: ServiceReportViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private enum class Tab { HOME, SERVICE_REPORT }

    private var currentTab = Tab.HOME
    private var loggingOut = false

    // Tambah/edit draft & Buat CSR: muat ulang daftar setelah berhasil
    private val draftLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            homeViewModel.refresh()
            serviceReportViewModel.refreshIfStarted()
        }
    }

    private var quickActionsCard: LayoutQuickActionCardBinding? = null
    private var quickActionsRow: LayoutQuickActionRowBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Header navy di belakang status bar -> ikon status bar putih
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            binding.bottomBar.updatePadding(bottom = systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.overlayQuickActions.isVisible -> dismissQuickActions()
                currentTab != Tab.HOME -> selectTab(Tab.HOME)
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.fragmentContainer, HomeFragment(), TAG_HOME)
                add(R.id.fragmentContainer, ServiceReportFragment(), TAG_SERVICE_REPORT)
            }
        }
        val savedTab = savedInstanceState?.getString(KEY_TAB)
        selectTab(Tab.entries.firstOrNull { it.name == savedTab } ?: Tab.HOME)

        binding.navHome.setOnClickListener { selectTab(Tab.HOME) }
        binding.navServiceReport.setOnClickListener { selectTab(Tab.SERVICE_REPORT) }
        binding.navPlan.setOnClickListener { showMessage("Plan segera hadir") }
        binding.navAttendance.setOnClickListener { showMessage("Attendance segera hadir") }
        binding.fabAdd.setOnClickListener { openAddDraft() }
        binding.quickActionsScrim.setOnClickListener { dismissQuickActions() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, currentTab.name)
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        val home = supportFragmentManager.findFragmentByTag(TAG_HOME)
        val serviceReport = supportFragmentManager.findFragmentByTag(TAG_SERVICE_REPORT)
        if (home != null && serviceReport != null) {
            supportFragmentManager.commit {
                if (tab == Tab.HOME) {
                    show(home)
                    hide(serviceReport)
                } else {
                    show(serviceReport)
                    hide(home)
                }
            }
        }
        // Service Report baru dimuat saat tab-nya pertama kali dibuka
        if (tab == Tab.SERVICE_REPORT) serviceReportViewModel.start()

        styleNav(binding.iconHome, binding.labelHome, binding.indicatorHome, tab == Tab.HOME)
        styleNav(
            binding.iconServiceReport,
            binding.labelServiceReport,
            binding.indicatorServiceReport,
            tab == Tab.SERVICE_REPORT
        )
    }

    private fun styleNav(icon: ImageView, label: TextView, indicator: View, active: Boolean) {
        val color = getColor(if (active) R.color.nd_primary else R.color.nd_on_surface_variant)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color))
        label.setTextColor(color)
        label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        indicator.isVisible = active
    }

    fun openAddDraft() {
        draftLauncher.launch(Intent(this, AddDraftActivity::class.java))
    }

    fun openEditDraft(serviceReportId: Int) {
        draftLauncher.launch(
            Intent(this, AddDraftActivity::class.java)
                .putExtra(AddDraftActivity.EXTRA_EDIT_SERVICE_REPORT_ID, serviceReportId)
        )
    }

    private fun openCreateCsr(serviceReportId: Int) {
        draftLauncher.launch(
            Intent(this, CreateCsrActivity::class.java)
                .putExtra(CreateCsrActivity.EXTRA_SERVICE_REPORT_ID, serviceReportId)
        )
    }

    // Menu aksi cepat (tekan & tahan sebuah draft): baris yang ditekan "diangkat" di atas
    // overlay gelap (salinan gambar baris + border + centang), lalu tampil 3 tombol aksi
    // di atasnya, atau di bawahnya kalau ruang di atas tidak cukup.
    fun showQuickActions(serviceReportId: Int, itemView: View) {
        dismissQuickActions()
        if (itemView.width == 0 || itemView.height == 0) return

        val overlay = binding.overlayQuickActions
        overlay.isVisible = true

        val itemLoc = IntArray(2)
        itemView.getLocationOnScreen(itemLoc)
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)
        val relativeTop = itemLoc[1] - overlayLoc[1]
        val relativeLeft = itemLoc[0] - overlayLoc[0]

        val card = LayoutQuickActionCardBinding.inflate(layoutInflater, overlay, false)
        card.ivSnapshot.setImageBitmap(itemView.drawToBitmap())
        overlay.addView(
            card.root,
            FrameLayout.LayoutParams(itemView.width, itemView.height).apply {
                leftMargin = relativeLeft
                topMargin = relativeTop
            }
        )

        val row = LayoutQuickActionRowBinding.inflate(layoutInflater, overlay, false)
        overlay.addView(
            row.root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        )
        row.root.visibility = View.INVISIBLE
        row.root.post {
            val params = row.root.layoutParams as FrameLayout.LayoutParams
            val above = relativeTop - row.root.height - dp(16)
            params.topMargin = if (above >= dp(8)) above else relativeTop + itemView.height + dp(16)
            row.root.layoutParams = params
            row.root.visibility = View.VISIBLE
        }

        row.actionEdit.setOnClickListener {
            dismissQuickActions()
            openEditDraft(serviceReportId)
        }
        row.actionCsr.setOnClickListener {
            dismissQuickActions()
            openCreateCsr(serviceReportId)
        }
        row.actionDelete.setOnClickListener {
            dismissQuickActions()
            confirmDeleteDraft()
        }

        quickActionsCard = card
        quickActionsRow = row
    }

    private fun dismissQuickActions() {
        quickActionsCard?.let { binding.overlayQuickActions.removeView(it.root) }
        quickActionsRow?.let { binding.overlayQuickActions.removeView(it.root) }
        quickActionsCard = null
        quickActionsRow = null
        binding.overlayQuickActions.isVisible = false
    }

    private fun confirmDeleteDraft() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Draft")
            .setMessage("Fitur hapus draft menunggu endpoint di backend, jadi belum bisa dilakukan dari sini.")
            .setPositiveButton("Oke", null)
            .show()
    }

    fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Keluar dari akun ini?")
            .setPositiveButton("Keluar") { _, _ -> logout() }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Bisa dipanggil dari kedua tab sekaligus saat sesi berakhir, jadi dijaga agar sekali saja
    fun logout() {
        if (loggingOut) return
        loggingOut = true
        tokenManager.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private companion object {
        const val TAG_HOME = "home"
        const val TAG_SERVICE_REPORT = "service_report"
        const val KEY_TAB = "tab"
    }
}

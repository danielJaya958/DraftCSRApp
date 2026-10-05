package com.ksm.draftcsrapp

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ksm.draftcsrapp.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// Tab Beranda: daftar draft yang belum selesai
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val host get() = requireActivity() as MainActivity

    private val adapter = DraftAdapter(
        onClick = { draft -> host.openEditDraft(draft.serviceReportId) },
        onLongClick = { draft, view -> host.showQuickActions(draft.serviceReportId, view) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        binding.topBar.applyStatusBarPadding()

        binding.tvAvatar.text = host.tokenManager.getUsername()?.firstOrNull()?.uppercase() ?: "?"
        binding.tvAvatar.setOnClickListener { host.confirmLogout() }
        binding.btnNotif.setOnClickListener { requireActivity().showMessage("Notifikasi segera hadir") }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvDrafts.layoutManager = layoutManager
        binding.rvDrafts.adapter = adapter
        binding.rvDrafts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
                    viewModel.loadMore()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(s: HomeUiState) {
        if (s.sessionExpired) {
            host.logout()
            return
        }
        adapter.submitList(s.items)
        binding.progress.isVisible = s.isLoading
        binding.rvDrafts.isVisible = s.items.isNotEmpty()

        binding.tvSubtitle.text = when {
            s.isLoading && s.items.isEmpty() -> "Memuat draft…"
            s.totalItems > 0 -> "${s.totalItems} draft menunggu dilengkapi sebelum menjadi service report. " +
                "Tekan lama sebuah draft untuk opsi lainnya."
            else -> "Tidak ada draft yang menunggu dilengkapi."
        }

        val showState = !s.isLoading && s.items.isEmpty()
        binding.stateView.isVisible = showState
        binding.tvState.text = s.error ?: "Belum ada draft. Tekan tombol + untuk membuat draft baru."
        binding.btnRetry.isVisible = s.error != null
    }

    override fun onDestroyView() {
        binding.rvDrafts.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

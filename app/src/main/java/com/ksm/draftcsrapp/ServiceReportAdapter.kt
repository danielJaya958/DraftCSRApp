package com.ksm.draftcsrapp

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ksm.draftcsrapp.databinding.ItemServiceReportBinding
import com.ksm.draftcsrapp.databinding.ItemSrHeaderBinding

sealed class SrRow {
    // Pemisah per tanggal dibuat, mis. "Dibuat 3 Okt 2026"
    data class Header(val label: String) : SrRow()
    data class Report(val item: DraftItem) : SrRow()
}

private const val TYPE_HEADER = 0
private const val TYPE_REPORT = 1

class ServiceReportAdapter(
    private val onClick: (DraftItem) -> Unit,
    private val onLongClick: (DraftItem, View) -> Unit
) : ListAdapter<SrRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is SrRow.Header -> TYPE_HEADER
        is SrRow.Report -> TYPE_REPORT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemSrHeaderBinding.inflate(inflater, parent, false))
        } else {
            ReportVH(ItemServiceReportBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SrRow.Header -> (holder as HeaderVH).bind(row)
            is SrRow.Report -> (holder as ReportVH).bind(row.item)
        }
    }

    class HeaderVH(private val b: ItemSrHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: SrRow.Header) {
            b.tvHeader.text = row.label
        }
    }

    inner class ReportVH(private val b: ItemServiceReportBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DraftItem) {
            val ctx = b.root.context
            fun color(res: Int) = ContextCompat.getColor(ctx, res)

            b.tvRegNumber.text = item.registrationNumber ?: "Tanpa nomor registrasi"

            val kategori = item.kategori?.trim()?.ifEmpty { null }
            b.tvBadge.isVisible = kategori != null
            b.tvBadge.text = kategori
            when {
                item.isDraft() -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_draft)
                    b.tvBadge.setTextColor(color(R.color.nd_on_surface_variant))
                }
                kategori.equals("External", ignoreCase = true) -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_external)
                    b.tvBadge.setTextColor(color(R.color.white))
                }
                else -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_internal)
                    b.tvBadge.setTextColor(color(R.color.nd_primary))
                }
            }

            when {
                item.hasCustomer() -> {
                    b.tvCustomer.text = item.customerName
                    b.tvCustomer.setTextColor(color(R.color.nd_on_surface))
                    b.tvCustomer.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    // Report internal memang tidak punya customer
                    b.tvCustomer.text = if (kategori.equals("Internal", ignoreCase = true)) {
                        "Pekerjaan internal"
                    } else {
                        "Customer belum diisi"
                    }
                    b.tvCustomer.setTextColor(color(R.color.nd_on_surface_variant))
                    b.tvCustomer.setTypeface(null, Typeface.NORMAL)
                }
            }

            b.tvWork.text = item.workType() ?: "Jenis pekerjaan belum diisi"
            b.tvTechnician.text = item.username?.trim()?.ifEmpty { null } ?: "-"
            b.tvDate.text = parseServerDate(item.reportDate)?.let(::formatDay) ?: "Tanggal belum diisi"

            if (item.isCustomerSigned == true) {
                b.tvSign.text = "Sudah TTD"
                b.tvSign.setTextColor(color(R.color.nd_green_600))
                b.tvSign.setTypeface(null, Typeface.BOLD)
            } else {
                b.tvSign.text = "Belum TTD"
                b.tvSign.setTextColor(color(R.color.nd_on_surface_variant))
                b.tvSign.setTypeface(null, Typeface.NORMAL)
            }

            b.root.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener {
                onLongClick(item, b.root)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SrRow>() {
        override fun areItemsTheSame(a: SrRow, b: SrRow) = when {
            a is SrRow.Header && b is SrRow.Header -> a.label == b.label
            a is SrRow.Report && b is SrRow.Report -> a.item.serviceReportId == b.item.serviceReportId
            else -> false
        }

        override fun areContentsTheSame(a: SrRow, b: SrRow) = a == b
    }
}

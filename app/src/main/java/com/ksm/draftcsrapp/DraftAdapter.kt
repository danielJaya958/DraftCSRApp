package com.ksm.draftcsrapp

import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ksm.draftcsrapp.databinding.ItemDraftBinding

// Daftar "Draft belum selesai" di Beranda
class DraftAdapter(
    private val onClick: (DraftItem) -> Unit,
    private val onLongClick: (DraftItem, View) -> Unit
) : ListAdapter<DraftItem, DraftAdapter.VH>(Diff) {

    inner class VH(private val b: ItemDraftBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.tvContinue.paintFlags = b.tvContinue.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }

        fun bind(item: DraftItem) {
            val ctx = b.root.context
            b.tvRegNumber.text = item.registrationNumber ?: "Tanpa nomor registrasi"

            if (item.hasCustomer()) {
                b.tvCustomer.text = item.customerName
                b.tvCustomer.setTextColor(ContextCompat.getColor(ctx, R.color.nd_on_surface))
                b.tvCustomer.setTypeface(null, Typeface.BOLD)
            } else {
                b.tvCustomer.text = "Pelanggan belum dipilih"
                b.tvCustomer.setTextColor(ContextCompat.getColor(ctx, R.color.nd_on_surface_variant))
                b.tvCustomer.setTypeface(null, Typeface.NORMAL)
            }

            val complaint = item.warningText()
            b.tvComplaint.isVisible = complaint != null
            if (complaint != null) b.tvComplaint.text = labeledText(ctx, "Keluhan: ", complaint)

            b.tvCreated.text = parseServerDate(item.createDate)?.let { "Dibuat ${formatDayTime(it)}" }.orEmpty()

            b.root.setOnClickListener { onClick(item) }
            b.tvContinue.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener {
                onLongClick(item, b.root)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDraftBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<DraftItem>() {
        override fun areItemsTheSame(a: DraftItem, b: DraftItem) = a.serviceReportId == b.serviceReportId
        override fun areContentsTheSame(a: DraftItem, b: DraftItem) = a == b
    }
}

package com.ksm.draftcsrapp

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.ksm.draftcsrapp.databinding.ItemDetailCheckBinding
import com.ksm.draftcsrapp.databinding.ItemDetailFieldBinding
import com.ksm.draftcsrapp.databinding.ItemDetailMetaBinding
import com.ksm.draftcsrapp.databinding.ItemDetailSectionBinding
import com.ksm.draftcsrapp.databinding.ItemDetailSummaryBinding

private const val TYPE_SUMMARY = 0
private const val TYPE_SECTION = 1
private const val TYPE_FIELD = 2
private const val TYPE_CHECK = 3

class ServiceReportDetailAdapter(
    private val onAttachmentClick: (DetailAttachment) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<DetailRow> = emptyList()

    fun submit(newRows: List<DetailRow>) {
        if (newRows == rows) return
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is DetailRow.Summary -> TYPE_SUMMARY
        is DetailRow.Section -> TYPE_SECTION
        is DetailRow.Field -> TYPE_FIELD
        is DetailRow.Check -> TYPE_CHECK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryVH(ItemDetailSummaryBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionVH(ItemDetailSectionBinding.inflate(inflater, parent, false))
            TYPE_FIELD -> FieldVH(ItemDetailFieldBinding.inflate(inflater, parent, false))
            else -> CheckVH(ItemDetailCheckBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is DetailRow.Summary -> (holder as SummaryVH).bind(row)
            is DetailRow.Section -> (holder as SectionVH).bind(row)
            is DetailRow.Field -> (holder as FieldVH).bind(row)
            is DetailRow.Check -> (holder as CheckVH).bind(row)
        }
    }

    class SummaryVH(private val b: ItemDetailSummaryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: DetailRow.Summary) {
            val ctx = b.root.context
            b.tvRegNumber.text = row.registrationNumber ?: "Tanpa nomor registrasi"

            // Warna badge sama dengan daftar Service Report
            b.tvBadge.isVisible = row.kategori != null
            b.tvBadge.text = row.kategori
            when {
                row.kategori.equals("Draft", ignoreCase = true) -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_draft)
                    b.tvBadge.setTextColor(ContextCompat.getColor(ctx, R.color.nd_on_surface_variant))
                }
                row.kategori.equals("External", ignoreCase = true) -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_external)
                    b.tvBadge.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                else -> {
                    b.tvBadge.setBackgroundResource(R.drawable.bg_badge_internal)
                    b.tvBadge.setTextColor(ContextCompat.getColor(ctx, R.color.nd_primary))
                }
            }

            b.metaContainer.removeAllViews()
            val inflater = LayoutInflater.from(ctx)
            for ((label, value) in row.meta) {
                val m = ItemDetailMetaBinding.inflate(inflater, b.metaContainer, true)
                m.tvMetaLabel.text = label
                m.tvMetaValue.text = value
            }
        }
    }

    class SectionVH(private val b: ItemDetailSectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: DetailRow.Section) {
            b.tvSectionTitle.text = row.title
            b.tvSectionCaption.isVisible = row.caption != null
            b.tvSectionCaption.text = row.caption
        }
    }

    inner class FieldVH(private val b: ItemDetailFieldBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: DetailRow.Field) {
            val ctx = b.root.context
            b.tvLabel.text = row.label

            val empty = row.value == null && row.table == null && row.attachment == null
            b.tvValue.isVisible = row.value != null || empty
            b.tvValue.text = row.value ?: "Tidak diisi"
            b.tvValue.setTextColor(
                ContextCompat.getColor(ctx, if (empty) R.color.nd_outline else R.color.nd_on_surface)
            )

            bindTable(b.tableContainer, row.table)
            bindAttachment(b.tvAttachment, row.attachment)
        }
    }

    inner class CheckVH(private val b: ItemDetailCheckBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: DetailRow.Check) {
            val ctx = b.root.context
            b.tvLabel.text = row.label

            // Status disampaikan lewat ikon + teks, bukan warna saja
            val color = ContextCompat.getColor(
                ctx,
                if (row.done) R.color.nd_green_600 else R.color.nd_outline
            )
            b.ivState.setImageResource(
                if (row.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
            )
            ImageViewCompat.setImageTintList(b.ivState, ColorStateList.valueOf(color))
            b.tvState.text = if (row.done) "Selesai" else "Tidak dicentang"
            b.tvState.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (row.done) R.color.nd_green_600 else R.color.nd_on_surface_variant
                )
            )
            b.tvState.setTypeface(null, if (row.done) Typeface.BOLD else Typeface.NORMAL)

            b.tvNote.isVisible = row.note != null
            b.tvNote.text = row.note
            bindTable(b.tableContainer, row.table)
            bindAttachment(b.tvAttachment, row.attachment)
        }
    }

    private fun bindAttachment(view: TextView, attachment: DetailAttachment?) {
        view.isVisible = attachment != null
        view.text = attachment?.fileName
        if (attachment?.url != null) {
            view.setOnClickListener { onAttachmentClick(attachment) }
        } else {
            view.setOnClickListener(null)
            view.isClickable = false
        }
    }

    // Tabel ditampilkan sebagai grid 2 kolom (nama kolom di atas nilainya) supaya muat di
    // layar HP tanpa scroll horizontal, berapa pun jumlah kolomnya.
    private fun bindTable(container: LinearLayout, table: List<DetailTableRow>?) {
        container.removeAllViews()
        container.isVisible = table != null
        if (table == null) return
        val ctx = container.context
        val labelColor = ContextCompat.getColor(ctx, R.color.nd_on_surface_variant)
        val valueColor = ContextCompat.getColor(ctx, R.color.nd_on_surface)

        table.forEachIndexed { rowIndex, cells ->
            if (table.size > 1) {
                container.addView(TextView(ctx).apply {
                    text = "Baris ${rowIndex + 1}"
                    textSize = 12f
                    setTextColor(labelColor)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, if (rowIndex == 0) 0 else ctx.dp(12), 0, ctx.dp(4))
                })
            }
            cells.chunked(2).forEachIndexed { lineIndex, pair ->
                val line = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = if (lineIndex == 0) 0 else ctx.dp(10) }
                }
                for ((name, value) in pair) {
                    val cell = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                        ).apply { marginEnd = ctx.dp(8) }
                    }
                    cell.addView(TextView(ctx).apply {
                        text = name
                        textSize = 12f
                        setTextColor(labelColor)
                    })
                    cell.addView(TextView(ctx).apply {
                        text = value.trim().ifEmpty { "-" }
                        textSize = 15f
                        setTextColor(valueColor)
                        setTextIsSelectable(true)
                    })
                    line.addView(cell)
                }
                // Baris ganjil: sel kosong menjaga lebar kolom tetap sama
                if (pair.size == 1) {
                    line.addView(android.view.View(ctx), LinearLayout.LayoutParams(0, 0, 1f))
                }
                container.addView(line)
            }
        }
    }
}

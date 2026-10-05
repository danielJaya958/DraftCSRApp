package com.ksm.draftcsrapp

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val idLocale: Locale = Locale.forLanguageTag("id-ID")

// Nilai customerName dari backend untuk draft yang belum memilih pelanggan
private const val NO_CUSTOMER = "No Customer Data"

// Backend mengirim tanggal dalam beberapa format:
//   "2026-10-03T11:56:33.9799779"  (createDate, waktu lokal server)
//   "2026-10-02T16:00:00.000Z"     (reportDate, UTC)
//   "2026-10-01 00:00:00"          (reportDate lama)
//   "" / null                       (belum diisi)
fun parseServerDate(raw: String?): Date? {
    val s = raw?.trim().orEmpty()
    if (s.length < 10) return null
    return try {
        when {
            s.endsWith("Z") && s.length >= 19 ->
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(s.take(19))
            s.length >= 19 -> {
                val pattern = if (s[10] == 'T') "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd HH:mm:ss"
                SimpleDateFormat(pattern, Locale.US).parse(s.take(19))
            }
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.take(10))
        }
    } catch (e: Exception) {
        null
    }
}

// "3 Okt 2026"
fun formatDay(date: Date): String = SimpleDateFormat("d MMM yyyy", idLocale).format(date)

// "3 Okt 2026, 11.56 WIB"
fun formatDayTime(date: Date): String =
    SimpleDateFormat("d MMM yyyy, HH.mm", idLocale).format(date) + " WIB"

fun DraftItem.hasCustomer(): Boolean =
    !customerName.isNullOrBlank() && !customerName.equals(NO_CUSTOMER, ignoreCase = true)

fun DraftItem.isDraft(): Boolean = kategori.equals("Draft", ignoreCase = true)

fun DraftItem.warningText(): String? = errorMessage?.trim()?.ifEmpty { null }

fun DraftItem.workType(): String? = codeDescription?.trim()?.ifEmpty { null }

// "Keluhan: Error tidak muncul hasil" dengan label abu-abu dan isi berwarna teks utama
fun labeledText(context: Context, label: String, value: String): CharSequence {
    val sb = SpannableStringBuilder(label).append(value)
    sb.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, R.color.nd_on_surface_variant)),
        0,
        label.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return sb
}

package com.ksm.draftcsrapp

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

// Adapter dropdown yang bisa dicari: menyaring item yang MENGANDUNG teks ketikan
// (tidak peka huruf besar/kecil), bukan hanya yang diawali teks itu seperti filter
// bawaan ArrayAdapter. onFiltered dipanggil setiap kali hasil saring berubah.
class SearchableDropdownAdapter(
    context: Context,
    private val allItems: List<String>,
    private val onFiltered: (query: String, count: Int) -> Unit = { _, _ -> }
) : ArrayAdapter<String>(context, R.layout.item_dropdown_option, ArrayList(allItems)) {

    private val containsFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim().orEmpty()
            val matches = if (query.isEmpty()) allItems
            else allItems.filter { it.contains(query, ignoreCase = true) }
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            setNotifyOnChange(false)
            clear()
            addAll(results.values as List<String>)
            notifyDataSetChanged()
            onFiltered(constraint?.toString()?.trim().orEmpty(), results.count)
        }

        override fun convertResultToString(resultValue: Any?): CharSequence = resultValue as? String ?: ""
    }

    override fun getFilter(): Filter = containsFilter
}

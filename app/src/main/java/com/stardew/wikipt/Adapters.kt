package com.stardew.wikipt

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Row(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null
)

class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
    var items: List<Row> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(v) {
            field = v; notifyDataSetChanged()
        }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.title.text = r.title
        if (r.subtitle.isNullOrBlank()) {
            h.sub.visibility = View.GONE
        } else {
            h.sub.visibility = View.VISIBLE
            h.sub.text = r.subtitle
        }
        h.itemView.setOnClickListener { r.onClick() }
        h.itemView.setOnLongClickListener {
            val l = r.onLongClick
            if (l != null) { l(); true } else false
        }
    }
}

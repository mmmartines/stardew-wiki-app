package com.stardew.wikipt

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

data class Row(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    /** Deslizar para o lado remove (Histórico e Favoritos). */
    val onSwipe: (() -> Unit)? = null,
    /** Linha de cabeçalho ("Hoje", "Ontem"…) ou de dica, sem toque. */
    val header: Boolean = false,
)

class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items: List<Row> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(v) {
            field = v; notifyDataSetChanged()
        }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.subtitle)
    }

    class HeaderVH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun getItemViewType(position: Int) = if (items[position].header) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 1) {
            val c = parent.context
            val d = c.resources.displayMetrics.density
            val t = TextView(c).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
                typeface = ResourcesCompat.getFont(c, R.font.pixel)
                textSize = 15f
                setTextColor(ContextCompat.getColor(c, R.color.wood_text))
                gravity = Gravity.START
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
            }
            return HeaderVH(t)
        }
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val r = items[position]
        if (holder is HeaderVH) {
            holder.text.text = r.title
            return
        }
        val h = holder as VH
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

    /** Liga o "deslizar para remover" nas linhas que têm [Row.onSwipe]. */
    fun attachSwipe(list: RecyclerView) {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val p = vh.bindingAdapterPosition
                return if (p in items.indices && items[p].onSwipe != null) super.getSwipeDirs(rv, vh) else 0
            }

            override fun onMove(rv: RecyclerView, a: RecyclerView.ViewHolder, b: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val p = vh.bindingAdapterPosition
                if (p in items.indices) items[p].onSwipe?.invoke()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                vh.itemView.alpha = 1f - minOf(1f, kotlin.math.abs(dX) / vh.itemView.width.toFloat())
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                vh.itemView.alpha = 1f
                super.clearView(rv, vh)
            }
        }).attachToRecyclerView(list)
    }
}

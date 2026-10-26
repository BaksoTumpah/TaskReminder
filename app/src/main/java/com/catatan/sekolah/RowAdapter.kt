package com.catatan.sekolah

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class RowAdapter<T>(
    var items: List<T>,
    private val bind: (View, T) -> Unit,
    private val onClick: (T) -> Unit,
    private val onLong: (T) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        bind(holder.itemView, item)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLong(item); true }
    }
}

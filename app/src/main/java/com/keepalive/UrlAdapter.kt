package com.keepalive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UrlAdapter(
    private val urls: MutableList<String>,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<UrlAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_url, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = urls[position]
        holder.tvUrl.text = url
        holder.btnDelete.setOnClickListener { onDelete(url) }
    }

    override fun getItemCount(): Int = urls.size

    fun updateList(newUrls: List<String>) {
        urls.clear()
        urls.addAll(newUrls)
        notifyDataSetChanged()
    }
}

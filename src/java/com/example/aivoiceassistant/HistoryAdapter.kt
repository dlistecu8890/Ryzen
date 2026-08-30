package com.example.aivoiceassistant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aivoiceassistant.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val items: MutableList<HistoryItem> = mutableListOf()
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))

    fun addItem(item: HistoryItem) {
        items.add(0, item) // terbaru di atas
        notifyItemInserted(0)
    }

    fun isEmpty(): Boolean = items.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.tvHistoryCommand.text = item.commandText
            binding.tvHistoryResponse.text = item.responseText
            binding.tvHistoryTime.text = timeFormat.format(item.timestamp)
        }
    }
}

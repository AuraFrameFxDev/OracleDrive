package com.genesis.ai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.genesis.ai.app.R
import java.io.File

class FileAdapter(private val onClick: (File) -> Unit) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view, onClick)
    }
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    class FileViewHolder(itemView: View, val onClick: (File) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.fileName)
        private var file: File? = null
        init {
            itemView.setOnClickListener {
                file?.let(onClick)
            }
        }
        fun bind(file: File) {
            this.file = file
            textView.text = if (file.isDirectory) "[DIR] ${file.name}" else file.name
        }
    }
    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
        override fun areContentsTheSame(oldItem: File, newItem: File) = oldItem.length() == newItem.length() && oldItem.lastModified() == newItem.lastModified()
    }
}

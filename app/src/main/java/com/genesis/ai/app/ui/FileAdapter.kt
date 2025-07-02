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
    /**
     * Creates a new FileViewHolder by inflating the file item layout.
     *
     * @return A FileViewHolder instance for displaying a file item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view, onClick)
    }
    /**
     * Binds the `File` item at the specified position to the provided `FileViewHolder`.
     *
     * @param holder The view holder to bind data to.
     * @param position The position of the item in the list.
     */
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
        /**
         * Binds a File object to the view holder, updating the displayed file name.
         *
         * If the file is a directory, the name is prefixed with "[DIR] ".
         *
         * @param file The File to display in this view holder.
         */
        fun bind(file: File) {
            this.file = file
            textView.text = if (file.isDirectory) "[DIR] ${file.name}" else file.name
        }
    }
    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        /**
 * Determines whether two File objects represent the same file based on their absolute paths.
 *
 * @return true if both files have the same absolute path; false otherwise.
 */
override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
        /**
 * Checks whether the contents of two files are the same by comparing their length and last modified timestamp.
 *
 * @return `true` if both files have the same size and last modification time, `false` otherwise.
 */
override fun areContentsTheSame(oldItem: File, newItem: File) = oldItem.length() == newItem.length() && oldItem.lastModified() == newItem.lastModified()
    }
}

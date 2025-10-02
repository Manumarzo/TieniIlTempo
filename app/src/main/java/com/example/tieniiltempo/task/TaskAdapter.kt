package com.example.tieniiltempo.task

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R

class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val onEditClick: (Task) -> Unit,
    private val onDeleteClick: (Task) -> Unit,
    private val onVoteClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.taskTitle)
        val completionTimeText: TextView = view.findViewById(R.id.taskCompletionTime)
        val statusText: TextView = view.findViewById(R.id.taskStatus)
        val editButton: ImageButton = view.findViewById(R.id.editTaskButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteTaskButton)
        val voteButton: ImageButton = view.findViewById(R.id.voteTaskButton)
        val voteText: TextView = view.findViewById(R.id.taskVote)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.titleText.text = task.title

        val statusNormalized = task.status.lowercase()
        val (display, colorRes) = when (statusNormalized) {
            "in corso" -> Pair("In corso", android.R.color.holo_orange_light)
            "completata" -> Pair("Completata", android.R.color.holo_green_dark)
            else -> Pair("Da iniziare", android.R.color.holo_blue_dark)
        }
        //prendo dalla tupla (display, colorRes) il testo e il colore restituiti da Pair()
        holder.statusText.text = display
        holder.statusText.setTextColor(holder.itemView.context.getColor(colorRes))
        holder.voteText.visibility = View.GONE

        if (statusNormalized == "completata") {

            if (task.vote != null) {
                holder.voteButton.visibility = View.GONE
                holder.voteText.visibility = View.VISIBLE
                holder.voteText.text = "Voto: ${task.vote} stelle"
            } else {
                holder.voteButton.visibility = View.VISIBLE
                holder.voteButton.setImageResource(R.drawable.ic_star)
                holder.voteButton.setColorFilter(holder.itemView.context.getColor(R.color.yellow))
                holder.voteButton.setOnClickListener {
                    onVoteClick(task)
                }
            }

            if (task.startedAt != null && task.finishedAt != null) {
                val durationSeconds = task.finishedAt!!.seconds - task.startedAt!!.seconds
                val totalMinutes = (durationSeconds / 60).toInt()
                val estimated = task.estimatedDurationMinutes ?: 0

                holder.completionTimeText.visibility = View.VISIBLE
                holder.completionTimeText.text =
                    "Tempo impiegato:\n${totalMinutes} min su $estimated min stimati"
            } else {
                holder.completionTimeText.visibility = View.GONE
            }
        } else {
            holder.voteButton.visibility = View.GONE
            holder.completionTimeText.visibility = View.GONE
        }



        val editable = statusNormalized == "da iniziare"
        holder.editButton.isEnabled = editable
        holder.editButton.alpha = if (editable) 1f else 0.3f
        if (editable) holder.editButton.setOnClickListener {
            onEditClick(task)
        }
        else holder.editButton.setOnClickListener(null)

        holder.deleteButton.setOnClickListener {
            onDeleteClick(task)
        }
    }


    override fun getItemCount(): Int = tasks.size
}


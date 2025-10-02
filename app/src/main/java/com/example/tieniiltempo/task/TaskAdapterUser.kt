package com.example.tieniiltempo.task

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R

class TaskAdapterUser(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit = {}
) : RecyclerView.Adapter<TaskAdapterUser.UserTaskViewHolder>() {

    inner class UserTaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.taskTitle)
        val statusText: TextView = view.findViewById(R.id.taskStatus)
        val completionTimeText: TextView = view.findViewById(R.id.taskCompletionTime)
        val voteText: TextView = view.findViewById(R.id.taskVote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserTaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_user, parent, false)
        return UserTaskViewHolder(view)
    }

    @SuppressLint("SetTextI18n") // Per permettere stringhe dinamiche come "Tempo impiegato: X min"
    override fun onBindViewHolder(holder: UserTaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.titleText.text = task.title

        val statusNormalized = task.status.lowercase()
        val (display, colorRes) = when (statusNormalized) {
            "in corso" -> "In corso" to android.R.color.holo_orange_light
            "completata" -> "Completata" to android.R.color.holo_green_dark
            else -> "Da iniziare" to android.R.color.holo_blue_dark
        }

        holder.statusText.text = display
        holder.statusText.setTextColor(holder.itemView.context.getColor(colorRes))

        // Logica per mostrare durata e voto
        if (statusNormalized == "completata") {
            // Mostro la durata
            if (task.startedAt != null && task.finishedAt != null) {
                val durationSeconds = task.finishedAt!!.seconds - task.startedAt!!.seconds
                val totalMinutes = (durationSeconds / 60).toInt()
                val estimated = task.estimatedDurationMinutes ?: 0

                holder.completionTimeText.visibility = View.VISIBLE
                holder.completionTimeText.text =
                    "Tempo impiegato: ${totalMinutes} min su $estimated min stimati"
            } else {
                holder.completionTimeText.visibility = View.GONE
            }

            // Mostro il voto
            if (task.vote != null) {
                holder.voteText.visibility = View.VISIBLE
                holder.voteText.text = "Voto: ${task.vote} stelle"
            } else {
                holder.voteText.visibility = View.GONE
            }
        } else {
            // Se la task non è completata, nascondo entrambi gli elementi
            holder.completionTimeText.visibility = View.GONE
            holder.voteText.visibility = View.GONE
        }

        // onClickListener per aprire la nuova activity
        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}
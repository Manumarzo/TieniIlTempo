package com.example.tieniiltempo.task.subtask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tieniiltempo.R
import com.example.tieniiltempo.task.Subtask
import java.util.concurrent.TimeUnit

class SubtaskAdapter(
    private val onStartClick: (Subtask, Int) -> Unit, // Callback per il click su Inizia
    private val onEndClick: (Subtask, Int) -> Unit,   // Callback per il click su Fine
) : RecyclerView.Adapter<SubtaskAdapter.SubtaskViewHolder>() {

    private var subtaskList = listOf<Subtask>()

    inner class SubtaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val description: TextView = view.findViewById(R.id.subtaskDescription)
        val comment: TextView = view.findViewById(R.id.subtaskComment)
        val imageView: ImageView = view.findViewById(R.id.subtaskImage)
        val startButton: Button = view.findViewById(R.id.startButton)
        val endButton: Button = view.findViewById(R.id.endButton)
        val actualTime: TextView = view.findViewById(R.id.subtaskActualTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subtask, parent, false)
        return SubtaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        val subtask = subtaskList[position]
        holder.description.text = subtask.description

        holder.comment.text = if (!subtask.comment.isNullOrBlank()) {
            "\"${subtask.comment}\""
        } else {
            "Nessun commento"
        }

        if (!subtask.imageUri.isNullOrBlank()) {
            holder.imageView.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(subtask.imageUri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.imageView)
        } else {
            holder.imageView.visibility = View.GONE
        }

        val isCompleted = subtask.status == "Completata"
        val isInProgress = subtask.status == "In corso"


        if (isCompleted && subtask.startedAt != null && subtask.finishedAt != null) {
            val durationMillis =
                subtask.finishedAt!!.toDate().time - subtask.startedAt!!.toDate().time
            val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
            val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60

            holder.actualTime.text = "Tempo esecuzione: ${durationMinutes}m ${durationSeconds}s"
            holder.actualTime.visibility = View.VISIBLE
        } else {
            holder.actualTime.visibility = View.GONE
        }

        // Il pulsante "Fine" è attivo solo se l'attività è in corso.
        holder.endButton.isEnabled = isInProgress

        // Il pulsante "Inizia" è attivo solo se "Da iniziare" e canStart dice sì.
        if (isCompleted || isInProgress) {
            holder.startButton.isEnabled = false
        } else { // Stato "Da iniziare"
            holder.startButton.isEnabled = canStart(position)
        }

        holder.startButton.setOnClickListener {
            onStartClick(subtask, position)
        }

        holder.endButton.setOnClickListener {
            onEndClick(subtask, position)
        }
    }

    override fun getItemCount(): Int = subtaskList.size


    private fun canStart(position: Int): Boolean {
        val subtask = subtaskList[position]
        if (subtask.status != "Da iniziare") return false

        if (subtask.parallel) {
            // PARALLELA: può essere avviata se:
            // 1. Tutte le sequenziali precedenti sono completate
            // 2. Non c'è nessuna sequenziale in corso (in qualsiasi posizione)

            val allPrevSequentialsCompleted = subtaskList
                .take(position)
                .filter { !it.parallel }
                .all { it.status == "Completata" }

            val noSequentialInProgress = subtaskList
                .none { !it.parallel && it.status == "In corso" }

            return allPrevSequentialsCompleted && noSequentialInProgress

        } else {
            // SEQUENZIALE: può essere avviata se:
            // 1. Nessuna altra subtask è in corso
            // 2. Tutte le subtask precedenti sono completate (parallele E sequenziali)

            val anyInProgress = subtaskList.any { it.status == "In corso" }
            if (anyInProgress) return false

            val allPrevCompleted = subtaskList
                .take(position)
                .all { it.status == "Completata" }

            return allPrevCompleted
        }
    }



    fun updateList(newList: List<Subtask>) {
        subtaskList = newList
        notifyDataSetChanged()
    }
}
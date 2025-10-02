package com.example.tieniiltempo.user

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.chat.ChatActivity
import com.example.tieniiltempo.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.tieniiltempo.user.UserMainActivity
import java.util.concurrent.TimeUnit

class UserStatisticsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var nomeUtenteTitleText: TextView
    private lateinit var taskTotaliText: TextView
    private lateinit var taskCompletateText: TextView
    private lateinit var taskInCorsoText: TextView
    private lateinit var taskDaIniziareText: TextView
    private lateinit var percentualeText: TextView
    private lateinit var avgTimeText: TextView
    private lateinit var prizeText: TextView
    private lateinit var prizeToRedeemText: TextView

    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_user)

        nomeUtenteTitleText = findViewById(R.id.textUserTitle)
        taskTotaliText = findViewById(R.id.textTasksTotal)
        taskCompletateText = findViewById(R.id.textTasksCompleted)
        taskInCorsoText = findViewById(R.id.textTasksInProgress)
        taskDaIniziareText = findViewById(R.id.textTasksPending)
        percentualeText = findViewById(R.id.textCompletionRate)
        avgTimeText = findViewById(R.id.textAvgTime)
        prizeText = findViewById(R.id.textPrize)
        prizeToRedeemText = findViewById(R.id.textPrizeToReedeem)

        bottomNavigation = findViewById(R.id.bottom_navigation)

        setupBottomNavigation()
        caricaStatisticheUtente()
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_stats

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, UserMainActivity::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.navigation_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    true
                }
                R.id.navigation_stats -> true
                else -> false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun caricaStatisticheUtente() {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Utente non autenticato", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val nome = document.getString("name") ?: "Utente"
                nomeUtenteTitleText.text = "Statistiche di: $nome"
            }
            .addOnFailureListener {
                nomeUtenteTitleText.text = "Le mie Statistiche"
            }

        db.collection("tasks")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { taskSnapshot ->
                val tasks = taskSnapshot.documents

                val totali = tasks.size
                val completate = tasks.count { it.getString("status") == "Completata" }
                val inCorso = tasks.count { it.getString("status") == "In corso" }
                val daIniziare = tasks.count { it.getString("status") == "Da iniziare" }

                var totalDurationMinutesCompleted = 0L
                var punctualityCount = 0
                var totalVoteSum = 0 // Variabile per la somma dei voti

                for (taskDoc in tasks) {
                    val status = taskDoc.getString("status")
                    if (status == "Completata") {
                        val startedAt = taskDoc.getTimestamp("startedAt")
                        val finishedAt = taskDoc.getTimestamp("finishedAt")
                        val estimatedDurationMinutes = taskDoc.getLong("estimatedDurationMinutes") ?: 0
                        val vote = taskDoc.getLong("vote")?.toInt() // Recupera il voto

                        // Calcolo della durata effettiva e puntulità
                        if (startedAt != null && finishedAt != null) {
                            val durationMillis = finishedAt.toDate().time - startedAt.toDate().time
                            val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)

                            totalDurationMinutesCompleted += durationMinutes

                            if (estimatedDurationMinutes > 0 && durationMinutes <= estimatedDurationMinutes) {
                                punctualityCount++
                            }
                        }

                        // Aggiungo il voto alla somma totale, se presente
                        if (vote != null) {
                            totalVoteSum += vote
                        }
                    }
                }

                val avgTimeMinutes = if (completate > 0) {
                    totalDurationMinutesCompleted / completate
                } else {
                    0L
                }

                // Calcola il numero di premi basato sulla somma dei voti
                val numberOfPrizes = totalVoteSum / 5 // Ogni multiplo di 5 è un premio

                taskTotaliText.text = "Task totali: $totali"
                taskCompletateText.text = "Task completate: $completate"
                taskInCorsoText.text = "Task in corso: $inCorso"
                taskDaIniziareText.text = "Task da iniziare: $daIniziare"

                val percentualePuntuali = if (completate > 0) {
                    (punctualityCount * 100 / completate)
                } else {
                    0
                }
                percentualeText.text = "Percentuale completate in tempo: ${percentualePuntuali}%"

                avgTimeText.text = "Tempo medio per task completate: ${avgTimeMinutes} min"
                prizeText.text = "Premi correnti ottenuti: $numberOfPrizes"

                db.collection("prizes").document(userId).get()
                    .addOnSuccessListener { prizeDoc ->
                        val redeemableCount = prizeDoc.getLong("count")?.toInt() ?: 0
                        prizeToRedeemText.text = "Premi riscattabili: $redeemableCount"
                    }
                    .addOnFailureListener {
                        prizeToRedeemText.text = "Premi riscattabili: 0"
                    }

            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore nel caricamento delle statistiche: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        caricaStatisticheUtente()
    }
}
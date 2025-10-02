package com.example.tieniiltempo.caregiver

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

class CaregiverStatisticsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var nomeText: TextView
    private lateinit var utentiAssociatiText: TextView
    private lateinit var taskTotaliText: TextView
    private lateinit var taskCompletateText: TextView
    private lateinit var taskInCorsoText: TextView
    private lateinit var taskDaIniziareText: TextView
    private lateinit var percentualeText: TextView
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_caregiver)

        nomeText = findViewById(R.id.textCaregiverTitle)
        utentiAssociatiText = findViewById(R.id.textNumUsers)
        taskTotaliText = findViewById(R.id.textTasksTotal)
        taskCompletateText = findViewById(R.id.textTasksCompleted)
        taskInCorsoText = findViewById(R.id.textTasksInProgress)
        taskDaIniziareText = findViewById(R.id.textTasksPending)
        percentualeText = findViewById(R.id.textCompletionRate)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        setupBottomNavigation()
        caricaStatistiche()
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.navigation_stats

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, CaregiverMainActivity::class.java))
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
    private fun caricaStatistiche() {
        val currentUser = auth.currentUser ?: return
        val caregiverId = currentUser.uid

        // Carica nome e cognome
        db.collection("users").document(caregiverId).get()
            .addOnSuccessListener { document ->
                val nome = document.getString("name") ?: "N/D"
                val cognome = document.getString("surname") ?: "N/D"
                nomeText.text = "Statistiche di: $nome $cognome"
            }

        // Carica utenti associati
        db.collection("associations")
            .whereEqualTo("caregiverId", caregiverId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val utentiAssociati = querySnapshot.size()
                utentiAssociatiText.text = "Utenti associati: $utentiAssociati"

                val utentiIds = querySnapshot.documents.mapNotNull { it.getString("userId") }

                if (utentiIds.isEmpty()) {
                    taskTotaliText.text = "Task totali create: 0"
                    taskCompletateText.text = "Task completate dagli utenti: 0"
                    taskInCorsoText.text = "Task in corso: 0"
                    taskDaIniziareText.text = "Task da iniziare: 0"
                    percentualeText.text = "Percentuale completate in tempo: 0%"
                    return@addOnSuccessListener
                }

                db.collection("tasks")
                    .whereIn("userId", utentiIds)
                    .get()
                    .addOnSuccessListener { taskSnapshot ->
                        val tasks = taskSnapshot.documents

                        val totali = tasks.size
                        val completate = tasks.count { it.getString("status") == "Completata" }
                        val inCorso = tasks.count { it.getString("status") == "In corso" }
                        val daIniziare = tasks.count { it.getString("status") == "Da iniziare" }

                        val puntuali = tasks.count {
                            val start = it.getTimestamp("startedAt")?.toDate()?.time ?: 0
                            val end = it.getTimestamp("finishedAt")?.toDate()?.time ?: 0
                            val stimata = it.getLong("estimatedDurationMinutes") ?: 0
                            val durataEffettiva = (end - start) / 60000
                            it.getString("status") == "Completata" && durataEffettiva <= stimata
                        }

                        taskTotaliText.text = "Task totali create: $totali"
                        taskCompletateText.text = "Task completate dagli Utenti: $completate"
                        taskInCorsoText.text = "Task in corso degli Utenti: $inCorso"
                        taskDaIniziareText.text = "Task da iniziare degli Utenti: $daIniziare"
                        percentualeText.text = "Percentuale completate in tempo dagli Utenti: ${
                            if (completate > 0) "${(puntuali * 100 / completate)}%" else "0%"
                        }"
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Errore nel caricamento statistiche", Toast.LENGTH_SHORT).show()
            }
    }
}

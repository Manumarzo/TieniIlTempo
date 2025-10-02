package com.example.tieniiltempo.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R
import com.example.tieniiltempo.auth.SplashActivity
import com.example.tieniiltempo.chat.ChatActivity
import com.example.tieniiltempo.profile.ProfileActivity
import com.example.tieniiltempo.task.Task
import com.example.tieniiltempo.task.TaskAdapterUser
import com.example.tieniiltempo.task.subtask.SubtaskListActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserMainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var welcomeTextView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var logoutButton: FloatingActionButton
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var userType: String

    private val taskList = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapterUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_user)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        welcomeTextView = findViewById(R.id.welcomeTextView)
        recyclerView = findViewById(R.id.taskRecyclerView)
        emptyTextView = findViewById(R.id.emptyTextView)
        logoutButton = findViewById(R.id.logoutButton)
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        adapter = TaskAdapterUser(taskList) { selectedTask ->
            val intent = Intent(this, SubtaskListActivity::class.java)
            intent.putExtra("TASK_ID", selectedTask.id)
            intent.putExtra("TASK_TITLE", selectedTask.title)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchUserName()

        logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        fetchTasks()
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.selectedItemId = R.id.navigation_home

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stats -> {
                    startActivity(Intent(this, UserStatisticsActivity::class.java))
                    true
                }
                R.id.navigation_home -> {
                    fetchTasks()
                    true
                }
                R.id.navigation_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchUserName() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: "Utente"
                val type = document.getString("userType") ?: "Utente"

                userType = type.lowercase()
                welcomeTextView.text = "Ciao, $name!"
            }
            .addOnFailureListener {
                welcomeTextView.text = "Ciao, Utente"
            }
    }

    private fun fetchTasks() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("tasks")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                taskList.clear()
                for (document in result) {
                    val sortOrder = document.getLong("sortOrder")?.toInt()
                    // Ignora le task senza un sortOrder valido
                    if (sortOrder == null) {
                        Log.w("UserMainActivity", "Task ${document.id} without valid 'sortOrder' field. Skipping.")
                        continue
                    }

                    val task = Task(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        estimatedDurationMinutes = document.getLong("estimatedDurationMinutes")?.toInt() ?: 0,
                        status = document.getString("status") ?: "Da iniziare",
                        sortOrder = sortOrder,
                        startedAt = document.getTimestamp("startedAt"),     // Estrai Timestamp
                        finishedAt = document.getTimestamp("finishedAt"),   // Estrai Timestamp
                        vote = document.getDouble("vote")
                    )
                    taskList.add(task)
                }

                // Ordina le attività in base al campo sortOrder al contrario
                taskList.sortByDescending { it.sortOrder }

                adapter.updateTasks(taskList)

                if (taskList.isEmpty()) {
                    emptyTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore nel caricamento delle attività: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("UserMainActivity", "Errore nel caricamento tasks: ", e) // Logga l'errore per debugging
            }
    }
}
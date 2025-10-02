package com.example.tieniiltempo.caregiver

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R
import com.example.tieniiltempo.task.TaskListActivity
import com.example.tieniiltempo.auth.SplashActivity
import com.example.tieniiltempo.chat.ChatActivity
import com.example.tieniiltempo.profile.ProfileActivity
import com.example.tieniiltempo.user.User
import com.example.tieniiltempo.user.UserAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

class CaregiverMainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var welcomeText: TextView
    private lateinit var userRecyclerView: RecyclerView
    private lateinit var addUserButton: FloatingActionButton
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var emptyTextView: TextView
    private lateinit var logoutButton: FloatingActionButton


    private val userList = mutableListOf<User>()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_caregiver)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        welcomeText = findViewById(R.id.welcomeText)
        userRecyclerView = findViewById(R.id.usersRecyclerView)
        addUserButton = findViewById(R.id.addUserButton)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        emptyTextView = findViewById(R.id.emptyTextView)
        logoutButton = findViewById(R.id.logoutButton)


        adapter = UserAdapter(
            userList,
            onUserClick = { user ->
                openTaskActivity(user)
            },
            onDisassociateClick = { user ->
                showConfirmDisassociateDialog(user)
            }
        )


        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userRecyclerView.adapter = adapter

        loadCaregiverInfo()
        loadAssociatedUsers()

        addUserButton.setOnClickListener {
            showAddUserDialog()
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Logout effettuato con successo", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }


        setupBottomNavigation()
    }

    private fun openTaskActivity(user: User) {
        val intent = Intent(this, TaskListActivity::class.java)
        intent.putExtra("userId", user.uid)
        intent.putExtra("userName", "${user.name} ${user.surname}")
        startActivity(intent)
    }


    private fun setupBottomNavigation() {
        bottomNavigationView.selectedItemId = R.id.navigation_home

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stats -> {
                    startActivity(Intent(this, CaregiverStatisticsActivity::class.java))
                    true
                }
                R.id.navigation_home -> {
                    loadAssociatedUsers()
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



    private fun showAddUserDialog() {
        val input = EditText(this).apply {
            hint = "Inserisci ID utente"
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val container = FrameLayout(this).apply {
            setPadding(48, 0, 48, 0) // padding sinistra/destra in pixel
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Associa un utente")
            .setMessage("Inserisci l'ID dell'utente da associare")
            .setView(container)
            .setPositiveButton("Associa") { dialog, _ ->
                val userId = input.text.toString().trim()
                if (userId.isNotEmpty()) {
                    checkAndAssociateUser(userId)
                } else {
                    Toast.makeText(this, "Inserisci un ID valido", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Annulla") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    private fun showConfirmDisassociateDialog(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disassocia utente")
            .setMessage("Sei sicuro di voler disassociare ${user.name} ${user.surname}?")
            .setPositiveButton("Sì") { dialog, _ ->
                disassociateUser(user)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun disassociateUser(user: User) {
        val caregiverId = auth.currentUser?.uid ?: return

        db.collection("associations")
            .whereEqualTo("caregiverId", caregiverId)
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    db.collection("associations").document(doc.id)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Utente disassociato", Toast.LENGTH_SHORT).show()
                            loadAssociatedUsers() // aggiorna la lista
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Errore durante la disassociazione", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Errore nel recupero delle associazioni", Toast.LENGTH_SHORT).show()
            }
    }


    private fun checkAndAssociateUser(userId: String) {
        val caregiverId = auth.currentUser?.uid ?: return

        // Controlla se esiste un documento user con quell'ID e se è un utente (non caregiver)
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    Toast.makeText(this, "Utente non trovato.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener //listener per eseguire le operazioni in maniera asincrona
                }

                val role = userDoc.getString("userType") ?: ""
                if (role != "Utente") {
                    Toast.makeText(this, "L'ID inserito non appartiene a un utente.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("associations")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            Toast.makeText(this, "Utente già associato ad un caregiver.", Toast.LENGTH_SHORT).show()
                        } else {
                            // Crea nuova associazione
                            val association = hashMapOf(
                                "caregiverId" to caregiverId,
                                "userId" to userId
                            )
                            db.collection("associations").add(association)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Utente associato con successo!", Toast.LENGTH_SHORT).show()
                                    loadAssociatedUsers() // aggiorna lista
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Errore durante l'associazione.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Errore durante il controllo.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Errore nel recupero dati utente.", Toast.LENGTH_SHORT).show()
            }
    }



    private fun loadCaregiverInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Caregiver"
                welcomeText.text = "Ciao, $name!"
            }
            .addOnFailureListener {
                welcomeText.text = "Ciao, Caregiver!"
            }
    }

    private fun loadAssociatedUsers() {
        val caregiverUid = auth.currentUser?.uid ?: return

        db.collection("associations")
            .whereEqualTo("caregiverId", caregiverUid)
            .get()
            .addOnSuccessListener { associationDocs ->
                val userIds = associationDocs.mapNotNull { it.getString("userId") }

                if (userIds.isEmpty()) {
                    userList.clear()
                    adapter.notifyDataSetChanged()
                    userRecyclerView.visibility = View.GONE
                    emptyTextView.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .whereIn(FieldPath.documentId(), userIds)
                    .get()
                    .addOnSuccessListener { result ->
                        userList.clear()
                        for (doc in result) {
                            val user = User(
                                uid = doc.id,
                                name = doc.getString("name") ?: "Sconosciuto",
                                surname = doc.getString("surname") ?: ""
                            )
                            userList.add(user)
                        }
                        adapter.notifyDataSetChanged()
                        userRecyclerView.visibility = View.VISIBLE
                        emptyTextView.visibility = if (userList.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
    }


}

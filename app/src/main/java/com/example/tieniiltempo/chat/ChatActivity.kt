package com.example.tieniiltempo.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.widget.Toast
import com.example.tieniiltempo.R
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.caregiver.CaregiverStatisticsActivity
import com.example.tieniiltempo.profile.ProfileActivity
import com.example.tieniiltempo.user.UserMainActivity
import com.example.tieniiltempo.user.UserStatisticsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatTitle: TextView
    private lateinit var userType: String
    private lateinit var emptyChatTextView: TextView


    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        chatTitle = findViewById(R.id.chatTitle)
        emptyChatTextView = findViewById(R.id.emptyChatTextView)


        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        loadChatTitle()
        loadAssociatedChats()
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.selectedItemId = R.id.navigation_chat

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stats -> {
                    when (userType.lowercase()) {
                        "caregiver" -> startActivity(Intent(this, CaregiverStatisticsActivity::class.java))
                        "utente" -> startActivity(Intent(this, UserStatisticsActivity::class.java))
                        else -> Toast.makeText(this, "Tipo utente non riconosciuto", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.navigation_home -> {
                    when (userType.lowercase()) {
                        "caregiver" -> startActivity(Intent(this, CaregiverMainActivity::class.java))
                        "utente" -> startActivity(Intent(this, UserMainActivity::class.java))
                        else -> Toast.makeText(this, "Tipo utente non riconosciuto", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.navigation_chat -> {
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


    private fun loadChatTitle() {
        val currentUser = auth.currentUser ?: return
        val currentUserId = currentUser.uid

        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { userDoc ->
                val name = userDoc.getString("name") ?: "Utente"
                val type = userDoc.getString("userType")?.lowercase()

                userType = type ?: "utente"
                chatTitle.text = "Chat di $name"
            }
            .addOnFailureListener {
                chatTitle.text = "Chat"
            }
    }

    private fun loadAssociatedChats() {
        val currentUser = auth.currentUser ?: return
        val currentUserId = currentUser.uid

        val associatedUserIds = mutableSetOf<String>()

        // se siamo un caregiver, prendo gli utenti associati
        db.collection("associations")
            .whereEqualTo("caregiverId", currentUserId)
            .get()
            .addOnSuccessListener { caregiverAssocSnapshot ->
                caregiverAssocSnapshot.documents.forEach {
                    it.getString("userId")?.let { uid -> associatedUserIds.add(uid) }
                }

                // se siamo un utente prendo il caregiver associato
                db.collection("associations")
                    .whereEqualTo("userId", currentUserId)
                    .get()
                    .addOnSuccessListener { userAssocSnapshot ->
                        userAssocSnapshot.documents.forEach {
                            it.getString("caregiverId")?.let { cid -> associatedUserIds.add(cid) }
                        }

                        // carica i dati degli utenti/caregiver associati
                        loadChatsForUsers(associatedUserIds.toList())
                    }
                    .addOnFailureListener {
                        // Gestione errore seconda query
                        loadChatsForUsers(associatedUserIds.toList())
                    }
            }
            .addOnFailureListener {
                // Gestione errore prima query, provo a caricare comunque
                loadChatsForUsers(associatedUserIds.toList())
            }
    }


    private fun loadChatsForUsers(userIds: List<String>) {
        val currentUserId = auth.currentUser?.uid ?: return

        if (userIds.isEmpty()) {
            chatRecyclerView.adapter = ChatAdapter(emptyList())
            chatRecyclerView.visibility = View.GONE
            emptyChatTextView.visibility = View.VISIBLE
            return
        }

        val chats = mutableListOf<Chat>()
        val loadedCount = 0

        // listener per aggiornare la UI in tempo reale
        val chatsCollection = db.collection("chats")

        for (userId in userIds) {
            // trovo i dati dell'utente (nome, cognome) per visualizzarlo nella chat
            db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
                val firstName = userDoc.getString("name") ?: "Utente"
                val lastName = userDoc.getString("surname") ?: ""
                val fullName = if (lastName.isNotBlank()) "$firstName $lastName" else firstName

                // Calcola l'ID della chat room
                val chatRoomId = getChatRoomId(currentUserId, userId)

                // prendo l'ultimo messaggio in quella chat room
                chatsCollection.document(chatRoomId).collection("messages")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            // In caso di errore, viene mostrato un messaggio
                            updateChatList(chats, Chat(userId, fullName, "Errore caricamento"), userIds.size)
                            return@addSnapshotListener
                        }

                        val lastMessage = if (snapshot != null && !snapshot.isEmpty) {
                            snapshot.documents[0].getString("text") ?: "..."
                        } else {
                            "Nessun messaggio" // Testo per chat vuote
                        }

                        updateChatList(chats, Chat(userId, fullName, lastMessage), userIds.size)
                    }
            }.addOnFailureListener {
                updateChatList(chats, null, userIds.size) // in caso di errore non aggiungiamo nessuna chat
            }
        }
    }

    private fun updateChatList(chats: MutableList<Chat>, newOrUpdatedChat: Chat?, totalSize: Int) {
        // Rimuovo la vecchia versione della chat se esiste, per evitare duplicati
        if (newOrUpdatedChat != null) {
            chats.removeAll { it.userId == newOrUpdatedChat.userId }
            chats.add(newOrUpdatedChat)
        }

        // Aggiorna l'adapter solo quando abbiamo informazioni per tutte le chat
        if (chats.size >= totalSize) {
            // Ordina le chat per nome
            chats.sortBy { it.username }

            chatAdapter = ChatAdapter(chats)
            chatRecyclerView.adapter = chatAdapter

            if (chats.isEmpty()) {
                chatRecyclerView.visibility = View.GONE
                emptyChatTextView.visibility = View.VISIBLE
            } else {
                chatRecyclerView.visibility = View.VISIBLE
                emptyChatTextView.visibility = View.GONE
            }
        }
    }

    private fun getChatRoomId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) {
            "${userId1}_${userId2}"
        } else {
            "${userId2}_${userId1}"
        }
    }


}

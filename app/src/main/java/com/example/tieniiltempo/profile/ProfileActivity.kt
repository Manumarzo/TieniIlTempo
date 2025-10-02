package com.example.tieniiltempo.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.caregiver.CaregiverStatisticsActivity
import com.example.tieniiltempo.chat.ChatActivity
import com.example.tieniiltempo.user.UserMainActivity
import com.example.tieniiltempo.user.UserStatisticsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var nomeTextView: TextView
    private lateinit var cognomeTextView: TextView
    private lateinit var tipoUtenteTextView: TextView
    private lateinit var userIdTextView: TextView
    private lateinit var copyButton: ImageButton
    private lateinit var emailTextView: TextView


    private var tipoUtente: String? = null // per sapere se è caregiver o utente

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // View binding manuale
        nomeTextView = findViewById(R.id.textNome)
        cognomeTextView = findViewById(R.id.textCognome)
        tipoUtenteTextView = findViewById(R.id.textTipoUtente)
        userIdTextView = findViewById(R.id.textUserId)
        copyButton = findViewById(R.id.btnCopyId)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        emailTextView = findViewById(R.id.textEmail)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(userId)

            userRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val nome = document.getString("name") ?: ""
                    val cognome = document.getString("surname") ?: ""
                    val tipo = document.getString("userType") ?: "Utente"
                    val email = document.getString("email") ?: ""

                    tipoUtente = tipo
                    emailTextView.text = email
                    nomeTextView.text = nome
                    cognomeTextView.text = cognome
                    tipoUtenteTextView.text = tipo
                    userIdTextView.text = userId
                } else {
                    Toast.makeText(this, "Utente non trovato", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Errore nel caricamento profilo", Toast.LENGTH_SHORT).show()
            }
        }

        // Copia ID utente negli appunti
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("User ID", userIdTextView.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "ID copiato negli appunti", Toast.LENGTH_SHORT).show()
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.selectedItemId = R.id.navigation_profile

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_stats -> {
                    when (tipoUtente?.lowercase()) {
                        "caregiver" -> startActivity(Intent(this, CaregiverStatisticsActivity::class.java))
                        "utente" -> startActivity(Intent(this, UserStatisticsActivity::class.java))
                        else -> Toast.makeText(this, "Tipo utente non riconosciuto", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.navigation_home -> {
                    when (tipoUtente?.lowercase()) {
                        "caregiver" -> startActivity(Intent(this, CaregiverMainActivity::class.java))
                        "utente" -> startActivity(Intent(this, UserMainActivity::class.java))
                        else -> Toast.makeText(this, "Tipo utente non riconosciuto", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.navigation_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    true
                }
                R.id.navigation_profile -> true
                else -> false
            }
        }
    }
}

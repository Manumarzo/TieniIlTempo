package com.example.tieniiltempo.auth

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.user.UserMainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var forgotPasswordText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)

        loginButton.setOnClickListener {
            loginUser()
        }

        forgotPasswordText.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Inserisci un'email per reimpostare la password.", Toast.LENGTH_SHORT).show()
            } else {
                // Controlla se l'email esiste nella collezione "users"
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            // L'email esiste, invia email di reset
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Email per reimpostare la password inviata!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Errore durante l'invio dell'email.", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Email non presente tra quelle degli utenti registrati.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Errore durante la verifica dell'email.", Toast.LENGTH_SHORT).show()
                    }
            }
        }

    }

    private fun loginUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login effettuato!", Toast.LENGTH_SHORT).show()

                    val currentUser = auth.currentUser
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                    currentUser?.let {
                        db.collection("users").document(it.uid).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val userType = document.getString("userType")
                                    val nextActivity = when (userType) {
                                        "Caregiver" -> CaregiverMainActivity::class.java
                                        "Utente" -> UserMainActivity::class.java
                                        else -> UserMainActivity::class.java
                                    }
                                    val intent = Intent(this, nextActivity)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this, "Dati utente non trovati.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Errore nel recupero del tipo utente.", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Login fallito. Controlla i dati.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

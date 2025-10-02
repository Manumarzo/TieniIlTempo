package com.example.tieniiltempo.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.user.UserMainActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private val db = FirebaseFirestore.getInstance()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Le notifiche sono disabilitate. Attivale nelle impostazioni.", Toast.LENGTH_LONG).show()
            }
        }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Recupera il documento utente da Firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userType = document.getString("userType")

                        when (userType) {
                            "Caregiver" -> {
                                startActivity(Intent(this, CaregiverMainActivity::class.java))
                            }
                            "Utente" -> {
                                startActivity(Intent(this, UserMainActivity::class.java))
                            }
                            else -> {
                                Toast.makeText(this, "Tipo utente sconosciuto", Toast.LENGTH_SHORT).show()
                            }
                        }
                        finish()
                    } else {
                        Toast.makeText(this, "Documento utente non trovato", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Errore nel recupero utente: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

            return
        }

        // Altrimenti, mostra i pulsanti per login e registrazione
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        askNotificationPermission()
    }
}


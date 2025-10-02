package com.example.tieniiltempo.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.user.UserMainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var nameEditText: TextInputEditText
    private lateinit var surnameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var ConfirmPasswordEditText: TextInputEditText
    private lateinit var userTypeSwitch: SwitchMaterial
    private lateinit var registerButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        nameEditText = findViewById(R.id.editTextName)
        surnameEditText = findViewById(R.id.editTextSurname)
        emailEditText = findViewById(R.id.editTextEmail)
        passwordEditText = findViewById(R.id.editTextPassword)
        ConfirmPasswordEditText = findViewById(R.id.editTextConfirmPassword)
        userTypeSwitch = findViewById(R.id.userTypeSwitch)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val name = nameEditText.text.toString().trim()
        val surname = surnameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = ConfirmPasswordEditText.text.toString().trim()


        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!name.matches(Regex("^[a-zA-ZÀ-ÿ\\s]+\$"))) {
            Toast.makeText(this, "Il nome può contenere solo lettere.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!surname.matches(Regex("^[a-zA-ZÀ-ÿ\\s]+\$"))) {
            Toast.makeText(this, "Il cognome può contenere solo lettere.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Inserisci un indirizzo email valido.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "La password deve contenere almeno 6 caratteri.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Le password non corrispondono.", Toast.LENGTH_SHORT).show()
            return
        }


        // Registrazione Firebase
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userType = if (userTypeSwitch.isChecked) "Caregiver" else "Utente"
                    saveUserToFirestore(user, name, surname, email, userType)
                } else {
                    Toast.makeText(this, "Registrazione fallita: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(user: FirebaseUser?, name: String, surname: String, email: String, userType: String) {
        user?.let {
            val userData = hashMapOf(
                "uid" to user.uid,
                "name" to name,
                "surname" to surname,
                "email" to email,
                "userType" to userType
            )

            firestore.collection("users").document(it.uid)
                .set(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Registrazione completata!", Toast.LENGTH_SHORT).show()
                    val nextActivity = when (userType) {
                        "Caregiver" -> CaregiverMainActivity::class.java
                        "Utente" -> UserMainActivity::class.java
                        else -> UserMainActivity::class.java
                    }
                    val intent = Intent(this, nextActivity)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)

                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Errore nel salvataggio dei dati: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

}

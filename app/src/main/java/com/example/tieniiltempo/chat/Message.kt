package com.example.tieniiltempo.chat

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Companion object per definire i tipi di messaggio in modo pulito
object MessageType {
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
    const val LOCATION = "LOCATION"
}

data class Message(
    val senderId: String = "",
    val text: String = "", // Usato per testo normale o come didascalia per immagini
    @ServerTimestamp
    val timestamp: Date? = null,

    // Campi per i vari tipi di allegato
    val imageUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Campo per distinguere il tipo di messaggio
    val type: String = MessageType.TEXT
)
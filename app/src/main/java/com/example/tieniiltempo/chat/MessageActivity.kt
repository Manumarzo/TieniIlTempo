package com.example.tieniiltempo.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class MessageActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var recipientNameTextView: TextView
    private lateinit var sendAttachmentButton: ImageButton

    // Firebase
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""

    // Client per la geolocalizzazione
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // Launcher per il selettore di immagini
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            uploadImageAndSendMessage(selectedUri)
        }
    }


    // Launcher per la richiesta dei permessi di localizzazione
    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            sendCurrentUserLocation()
        } else {
            Toast.makeText(this, "Permesso di localizzazione negato.", Toast.LENGTH_SHORT).show()
        }
    }

    // Chat Info
    private lateinit var chatWithUserId: String
    private lateinit var chatRoomId: String
    private lateinit var messageAdapter: MessageAdapter
    private val messageList = mutableListOf<Message>()

    // Notifiche
    private val CHANNEL_ID = "chat_notifications"
    private var isChatOpen = false
    private var messageListener: ListenerRegistration? = null

    companion object {
        var currentOpenChatId: String? = null
    }

    override fun onStart() {
        super.onStart()
        currentOpenChatId = chatRoomId
    }

    override fun onStop() {
        super.onStop()
        currentOpenChatId = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        // Recupero i dati e verifico la validità
        chatWithUserId = intent.getStringExtra("USER_ID") ?: ""
        val chatWithUserName = intent.getStringExtra("USER_NAME") ?: "Utente"

        if (chatWithUserId.isEmpty() || currentUserId.isEmpty()) {
            Toast.makeText(this, "Errore: utente non valido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Determino l'ID della chat room e la inizializzo
        chatRoomId = getChatRoomId(currentUserId, chatWithUserId)
        setupUI(chatWithUserName)

        // Imposto il listener per inviare i messaggi
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                val message = Message(senderId = currentUserId, text = messageText, type = MessageType.TEXT)
                sendMessage(message)
            }
        }

        sendAttachmentButton = findViewById(R.id.sendAttachmentButton)

        sendAttachmentButton.setOnClickListener {
            showAttachmentDialog()
        }

        createNotificationChannel()
        listenForMessages()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Chat Notifications"
            val descriptionText = "Notifiche per i messaggi ricevuti"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupUI(userName: String) {
        recipientNameTextView = findViewById(R.id.recipientNameTextView)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        recipientNameTextView.text = userName

        // Setup RecyclerView
        messageAdapter = MessageAdapter(messageList)
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // I messaggi partono dal basso
        }
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun showAttachmentDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_attachment_picker, null)
        builder.setView(dialogView)

        val dialog = builder.create()

        dialogView.findViewById<LinearLayout>(R.id.option_image).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch("image/*")
            dialog.dismiss()
        }

        dialogView.findViewById<LinearLayout>(R.id.option_location).setOnClickListener {
            checkLocationPermissionAndSend()
            dialog.dismiss()
        }

        dialog.show()
    }

    // logica per l'invio d'immagini
    private fun uploadImageAndSendMessage(uri: Uri) {
        Toast.makeText(this, "Caricamento immagine...", Toast.LENGTH_SHORT).show()
        val storageRef = FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("chat_images/${UUID.randomUUID()}.jpg")

        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val message = Message(
                        senderId = currentUserId,
                        imageUri = downloadUrl.toString(),
                        type = MessageType.IMAGE
                    )
                    sendMessage(message)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Errore caricamento immagine.", Toast.LENGTH_SHORT).show()
            }
    }

    // logica per controllare i permessi
    private fun checkLocationPermissionAndSend() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "Acquisizione posizione...", Toast.LENGTH_SHORT).show()
                sendCurrentUserLocation()
            }
            else -> {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // logica per inviare la posizione
    private fun sendCurrentUserLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token // Token per gestire l'eventuale annullamento
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val message = Message(
                        senderId = currentUserId,
                        text = "Posizione",
                        latitude = location.latitude,
                        longitude = location.longitude,
                        type = MessageType.LOCATION
                    )
                    sendMessage(message)
                } else {
                    Toast.makeText(this, "Impossibile ottenere la posizione. Attiva il GPS.", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Errore nell'ottenere la posizione: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Errore di sicurezza sulla posizione.", Toast.LENGTH_SHORT).show()
        }
    }

    // logica d'invio messaggi
    private fun sendMessage(message: Message) {
        db.collection("chats").document(chatRoomId).collection("messages")
            .add(message)
            .addOnSuccessListener {
                if (message.type == MessageType.TEXT) {
                    messageInput.text.clear()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore durante l'invio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Crea un ID univoco e consistente per la chat
    private fun getChatRoomId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) {
            "${userId1}_${userId2}"
        } else {
            "${userId2}_${userId1}"
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission", "ObsoleteSdkInt")
    private fun showNotification(senderName: String, messageText: String) {
        val notificationId = chatRoomId.hashCode()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notification)
        }
    }


    private var isInitialLoad = true

    private fun listenForMessages() {
        messageListener = db.collection("chats").document(chatRoomId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                if (snapshots == null) return@addSnapshotListener

                val newMessages = snapshots.documentChanges.filter {
                    it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED
                }.map {
                    it.document.toObject(Message::class.java)
                }

                if (isInitialLoad) {
                    // Primo caricamento: aggiungo tutti i messaggi senza notificare
                    messageList.clear()
                    messageList.addAll(snapshots.toObjects(Message::class.java))
                    isInitialLoad = false
                } else {
                    // Caricamenti successivi: aggiungo solo i nuovi messaggi
                    messageList.addAll(newMessages)

                    if (chatRoomId != currentOpenChatId) {
                        val incomingMessages = newMessages.filter { it.senderId != currentUserId }
                        for (msg in incomingMessages) {
                            db.collection("users").document(msg.senderId).get()
                                .addOnSuccessListener { doc ->
                                    val senderName = doc.getString("name") ?: "Utente"
                                    val senderSurname = doc.getString("surname") ?: "Chat"
                                    val userName = "$senderName $senderSurname"
                                    showNotification(userName, msg.text)
                                }
                                .addOnFailureListener {
                                    showNotification("Nuovo messaggio", msg.text)
                                }
                        }
                    }
                }

                messageAdapter.notifyDataSetChanged() // l'adapter legge la lista dei messaggi aggiornata
                messagesRecyclerView.scrollToPosition(messageList.size - 1)
            }
    }
}
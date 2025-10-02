package com.example.tieniiltempo.task.subtask

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R
import com.example.tieniiltempo.chat.Message
import com.example.tieniiltempo.chat.MessageType
import com.example.tieniiltempo.task.Subtask
import com.example.tieniiltempo.task.Task
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class SubtaskListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var terminateActivityButton: Button
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: SubtaskAdapter
    private var taskId: String = ""
    private var taskTitle: String = ""
    private var subtasks: MutableList<Subtask> = mutableListOf()
    private var taskStatusListener: ListenerRegistration? = null
    private var subtaskListener: ListenerRegistration? = null
    private var taskIsCompleted: Boolean = false
    private lateinit var commentImageButton: FloatingActionButton
    private lateinit var positionButton: FloatingActionButton

    private val auth = FirebaseAuth.getInstance()


    // Variabili per gestire l'immagine nel dialogo
    private var uploadedImageUrl: String? = null // Salva l'URL dell'immagine dopo l'upload
    private lateinit var dialogTextView: TextView

    // Callback per gestire il risultato dell'upload
    private var imagePickerCallback: ((Uri) -> Unit)? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { originalUri ->
            Toast.makeText(this, "Caricamento immagine...", Toast.LENGTH_SHORT).show()
            uploadImageToFirebaseStorage(originalUri) { downloadUri ->
                if (downloadUri != null) {
                    imagePickerCallback?.invoke(downloadUri) // Passa l'URL di download
                } else {
                    Toast.makeText(this, "Errore nel caricamento dell'immagine", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // Client per la geolocalizzazione
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // Launcher per la richiesta dei permessi di localizzazione
    private val requestLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            sendLocationNow()
        } else {
            Toast.makeText(this, "Permesso di localizzazione negato.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subtask_list)

        taskId = intent.getStringExtra("TASK_ID") ?: run {
            Toast.makeText(this, "ID attività non fornito.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Sottotask"

        title = "Sottotask di: $taskTitle"

        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance() // Inizializza Firebase Storage
        recyclerView = findViewById(R.id.subtaskRecyclerView)
        emptyText = findViewById(R.id.subtaskEmptyText)
        terminateActivityButton = findViewById(R.id.terminateActivityButton)
        commentImageButton = findViewById(R.id.commentImageButton)
        positionButton = findViewById(R.id.positionButton)

        adapter = SubtaskAdapter(
            onStartClick = { subtask, position ->
                handleStartSubtask(subtask, position)
            },
            onEndClick = { subtask, position ->
                handleEndSubtask(subtask, position)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        terminateActivityButton.setOnClickListener {
            terminateTask()
        }

        commentImageButton.setOnClickListener {
            showSubtaskSelectionDialog()
        }

        positionButton.setOnClickListener {
            checkLocationPermissionAndSend()
        }

        listenForTaskChanges(taskId)
        listenForSubtaskChanges(taskId)

        commentImageButton.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        taskStatusListener?.remove()
        subtaskListener?.remove()
    }

    private fun listenForTaskChanges(taskId: String) {
        taskStatusListener = db.collection("tasks").document(taskId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("SubtaskListActivity", "Listen failed (Task).", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val task = snapshot.toObject(Task::class.java)
                    if (task != null) {
                        taskIsCompleted = (task.status == "Completata")
                        updateTerminateButtonVisibility()
                        updateCommentButtonVisibility()
                    }
                } else {
                    finish()
                }
            }
    }

    private fun listenForSubtaskChanges(taskId: String) {
        subtaskListener = db.collection("subtasks")
            .whereEqualTo("taskId", taskId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("SubtaskListActivity", "Listen failed (Subtask).", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    subtasks.clear()
                    for (doc in snapshots.documents) {
                        val subtask = doc.toObject(Subtask::class.java)
                        if (subtask != null) {
                            subtask.id = doc.id
                            subtasks.add(subtask)
                        }
                    }

                    subtasks.sortBy { it.order }

                    adapter.updateList(subtasks)
                    emptyText.visibility = if (subtasks.isEmpty()) View.VISIBLE else View.GONE
                    updateTerminateButtonVisibility()
                    updatePositionButtonVisibility()
                    updateCommentButtonVisibility()
                }
            }
    }

    private fun canStartSubtask(subtask: Subtask, position: Int): Boolean {
        if (subtask.status != "Da iniziare") return false

        if (subtask.parallel) {
            // PARALLELA: può essere avviata se:
            // 1. Tutte le sequenziali precedenti sono completate
            // 2. Non c'è nessuna sequenziale in corso (in qualsiasi posizione)

            val allPrevSequentialsCompleted = subtasks
                .take(position)
                .filter { !it.parallel }
                .all { it.status == "Completata" }

            val noSequentialInProgress = subtasks
                .none { !it.parallel && it.status == "In corso" }

            return allPrevSequentialsCompleted && noSequentialInProgress

        } else {
            // SEQUENZIALE: può essere avviata se:
            // 1. Nessuna altra subtask è in corso
            // 2. Tutte le subtask precedenti sono completate (parallele E sequenziali)

            val anyInProgress = subtasks.any { it.status == "In corso" }
            if (anyInProgress) return false

            val allPrevCompleted = subtasks
                .take(position)
                .all { it.status == "Completata" }

            return allPrevCompleted
        }
    }


    private fun handleStartSubtask(subtask: Subtask, position: Int) {
        if (!canStartSubtask(subtask, position)) {
            Toast.makeText(this, "Non puoi avviare questa sottotask ora.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedSubtaskData = mapOf(
            "status" to "In corso",
            "startedAt" to Timestamp.now()
        )

        db.collection("subtasks").document(subtask.id).update(updatedSubtaskData)
            .addOnSuccessListener { updateTaskStatusOnSubtaskStart(taskId) }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore nell'avvio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateTaskStatusOnSubtaskStart(taskId: String) {
        db.collection("tasks").document(taskId).get().addOnSuccessListener { documentSnapshot ->
            val currentTaskStatus = documentSnapshot.getString("status")
            if (currentTaskStatus == "Da iniziare") {
                val updateData = mapOf(
                    "status" to "In corso",
                    "startedAt" to Timestamp.now()
                )
                db.collection("tasks").document(taskId).update(updateData)
            }
        }
    }

    private fun handleEndSubtask(subtask: Subtask, position: Int) {
        val updatedSubtaskData = mapOf(
            "status" to "Completata",
            "finishedAt" to Timestamp.now()
        )

        db.collection("subtasks").document(subtask.id).update(updatedSubtaskData)
    }

    private fun updateTerminateButtonVisibility() {
        if (taskIsCompleted || subtasks.isEmpty()) {
            terminateActivityButton.visibility = View.GONE
            terminateActivityButton.isEnabled = false
            return
        }
        val allSubtasksCompleted = subtasks.all { it.status == "Completata" } // ritorna true se tutte le sottotask hanno come stato completata
        terminateActivityButton.visibility = if (allSubtasksCompleted) View.VISIBLE else View.GONE
        terminateActivityButton.isEnabled = allSubtasksCompleted
    }

    private fun updatePositionButtonVisibility() {
        val hasPositionSubtaskInProgress = subtasks.any { it.position && it.status == "In corso" }
        positionButton.visibility = if (hasPositionSubtaskInProgress) View.VISIBLE else View.GONE
    }

    private fun updateCommentButtonVisibility() {
        commentImageButton.visibility = if (taskIsCompleted) View.GONE else View.VISIBLE
    }

    private fun terminateTask() {
        db.collection("tasks").document(taskId).update(
            "status", "Completata",
            "finishedAt", Timestamp.now()
        ).addOnSuccessListener {
            Toast.makeText(this, "Attività '${taskTitle}' completata!", Toast.LENGTH_LONG).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Errore nel completamento dell'attività: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSubtaskSelectionDialog() {
        if (subtasks.isEmpty()) {
            Toast.makeText(this, "Nessuna sottotask disponibile per il commento.", Toast.LENGTH_SHORT).show()
            return
        }

        val subtaskDescriptions = subtasks.map { it.description }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Seleziona sottotask da commentare")
            .setItems(subtaskDescriptions) { dialog, which ->
                val selectedSubtask = subtasks[which]
                showCommentImageDialog(selectedSubtask, taskTitle)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }


    private fun showCommentImageDialog(subtask: Subtask, taskTitle: String) {
        uploadedImageUrl = null // Resetta l'URL precedente

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Commenta: ${subtask.description}")

        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_comment_image, null)
        val commentInput = dialogView.findViewById<EditText>(R.id.commentEditText)
        val selectImageButton = dialogView.findViewById<Button>(R.id.selectImageButton)
        dialogTextView = dialogView.findViewById(R.id.imageSelectedTextView)

        builder.setView(dialogView)

        selectImageButton.setOnClickListener {
            // Definisce cosa fare una volta ottenuto l'URL di download
            imagePickerCallback = { downloadUri ->
                uploadedImageUrl = downloadUri.toString()
                dialogTextView.text = "Immagine caricata!"
                dialogTextView.visibility = View.VISIBLE
            }
            // Avvia il selettore
            imagePickerLauncher.launch("image/*")
        }

        builder.setPositiveButton("Invia") { dialog, _ ->
            val commentText = commentInput.text.toString().trim()

            if (commentText.isBlank()) {
                Toast.makeText(this, "Devi inserire almeno un commento", Toast.LENGTH_SHORT).show()
            } else {
                // L'immagine è già stata caricata, ora invio solo il messaggio
                val header = "Commento a \"$taskTitle\" per \"${subtask.description}\":"
                val messageText = "$header\n$commentText"
                findCaregiverAndSendMessage(messageText, uploadedImageUrl)
            }
        }
        builder.setNegativeButton("Annulla", null)
        builder.show()
    }


    private fun uploadImageToFirebaseStorage(fileUri: Uri, onSuccess: (Uri?) -> Unit) {
        val fileName = "subtask_images/${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child(fileName)

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri)
                }.addOnFailureListener {
                    onSuccess(null)
                }
            }
            .addOnFailureListener {
                onSuccess(null)
            }
    }


    private fun findCaregiverAndSendMessage(comment: String, imageUrl: String?) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("associations")
            .whereEqualTo("userId", currentUserId)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    return@addOnSuccessListener
                }
                val caregiverId = documents.documents[0].getString("caregiverId")
                if (caregiverId != null) {
                    sendMessageToChat(currentUserId, caregiverId, comment, imageUrl)
                }
            }
    }


    private fun sendMessageToChat(
        senderId: String,
        recipientId: String,
        text: String,
        imageUrl: String?,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        val chatRoomId = getChatRoomId(senderId, recipientId)

        val messageType = when {
            imageUrl != null -> MessageType.IMAGE
            latitude != null && longitude != null -> MessageType.LOCATION
            else -> MessageType.TEXT
        }

        val message = Message(
            senderId = senderId,
            text = text,
            imageUri = imageUrl,
            latitude = latitude,
            longitude = longitude,
            type = messageType
        )

        db.collection("chats").document(chatRoomId).collection("messages")
            .add(message)
            .addOnSuccessListener {
                val successMessage = when (messageType) {
                    MessageType.IMAGE, MessageType.TEXT -> "Commento inviato in chat!"
                    MessageType.LOCATION -> "Posizione inviata in chat!"
                    else -> "Messaggio inviato!"
                }
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore invio messaggio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getChatRoomId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun checkLocationPermissionAndSend() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "Acquisizione posizione...", Toast.LENGTH_SHORT).show()
                sendLocationNow()
            }
            else -> {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun sendLocationNow() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        //restituisce null o la prima occorrezza nella lista che rispetta le condizioni tra {}
                        val subtaskInProgress = subtasks.firstOrNull { it.position && it.status == "In corso" }
                        val description = if (subtaskInProgress != null) {
                            "Posizione attuale per la sottotask: \"${subtaskInProgress.description}\""
                        } else {
                            "La mia posizione attuale"
                        }
                        findCaregiverAndSendLocation(description, location.latitude, location.longitude)
                    } else {
                        Toast.makeText(this, "Impossibile ottenere la posizione. Attiva il GPS.", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Errore di sicurezza. Attiva il GPS.", Toast.LENGTH_LONG).show()
        }
    }

    private fun findCaregiverAndSendLocation(text: String, latitude: Double, longitude: Double) {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("associations").whereEqualTo("userId", currentUserId).limit(1).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener
                val caregiverId = documents.documents[0].getString("caregiverId")
                if (caregiverId != null) {
                    sendMessageToChat(
                        senderId = currentUserId,
                        recipientId = caregiverId,
                        text = text,
                        imageUrl = null,
                        latitude = latitude,
                        longitude = longitude
                    )
                }
            }
    }
}
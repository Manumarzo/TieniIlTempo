package com.example.tieniiltempo.task

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tieniiltempo.R
import com.example.tieniiltempo.caregiver.CaregiverMainActivity
import com.example.tieniiltempo.caregiver.CaregiverStatisticsActivity
import com.example.tieniiltempo.chat.ChatActivity
import com.example.tieniiltempo.chat.Message
import com.example.tieniiltempo.chat.MessageType
import com.example.tieniiltempo.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID


class TaskListActivity : AppCompatActivity() {

    // Componenti UI
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var bottomNavigationView: BottomNavigationView

    // Database e dati
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage // Dichiarazione di Firebase Storage
    private lateinit var userId: String
    private val taskList = mutableListOf<Task>()

    // Gestione immagini
    private var imagePickerCallback: ((Uri) -> Unit)? = null


    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { originalUri ->
            // Carica l'immagine su Firebase Storage
            uploadImageToFirebaseStorage(originalUri) { downloadUri ->
                if (downloadUri != null) {
                    imagePickerCallback?.invoke(downloadUri) // Passa l'URI di download alla callback
                } else {
                    Toast.makeText(this, "Errore nel caricamento dell'immagine su Storage", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        // Inizializza database e Storage
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance() // Inizializza Firebase Storage

        // Inizializza componenti UI
        taskRecyclerView = findViewById(R.id.taskRecyclerView)
        emptyText = findViewById(R.id.emptyTaskText)

        // Ottieni dati utente dall'intent
        userId = intent.getStringExtra("userId").toString()
        val userName = intent.getStringExtra("userName") ?: "Utente"
        findViewById<TextView>(R.id.taskListTitle).text = "Attività di $userName"

        // Configura adapter per RecyclerView
        taskAdapter = TaskAdapter(
            taskList,
            onEditClick = { task -> userId.let { showCreateTaskDialog(it, task) } },
            onDeleteClick = { task -> deleteTask(task) },
            onVoteClick = { task -> showVoteDialog(task) }
        )


        taskRecyclerView.adapter = taskAdapter
        taskRecyclerView.layoutManager = LinearLayoutManager(this)

        // Carica le attività dell'utente
        loadTasksForUser(userId)

        // Configura navigazione e pulsante aggiunta
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()

        findViewById<FloatingActionButton>(R.id.addTaskButton).setOnClickListener {
            userId.let { showCreateTaskDialog(it) }
        }

        val redeemButton: FloatingActionButton = findViewById(R.id.redeemPrizeButton)
        redeemButton.setOnClickListener {
            showRedeemDialog()
        }

        createNotificationChannel()

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Task Notifications"
            val descriptionText = "Notifiche per task che superano il tempo stimato"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("TASK_OVERDUE_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showTaskOverdueNotification(taskTitle: String) {
        val builder = NotificationCompat.Builder(this, "TASK_OVERDUE_CHANNEL")
            .setSmallIcon(R.drawable.ic_stats)
            .setContentTitle("Task in ritardo")
            .setContentText("La task \"$taskTitle\" ha superato la durata stimata!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), builder.build()) // id unico
        }
    }

    private fun checkTaskOverdue(task: Task) {
        val startedAt = task.startedAt ?: return
        val estimatedMillis = task.estimatedDurationMinutes * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        if (currentTime > startedAt.toDate().time + estimatedMillis && task.status != "Completata") {
            showTaskOverdueNotification(task.title)
        }
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
                    startActivity(Intent(this, CaregiverMainActivity::class.java))
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


    private fun showCreateTaskDialog(userId: String, taskToEdit: Task? = null) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (taskToEdit == null) "Crea nuova attività" else "Modifica attività")

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_create_task, null)
        val titleEditText = dialogView.findViewById<EditText>(R.id.taskTitleEditText)
        val subtasksContainer = dialogView.findViewById<LinearLayout>(R.id.subtasksContainer)
        val addSubtaskButton = dialogView.findViewById<Button>(R.id.addSubtaskButton)
        val durationEditText = dialogView.findViewById<EditText>(R.id.taskEstimatedDurationEditText)

        // Funzione helper per popolare le view delle sottotask, per evitare codice duplicato
        val populateSubtasks = { documents: List<com.google.firebase.firestore.DocumentSnapshot> ->
            for (doc in documents) {
                addSubtaskView(
                    subtasksContainer,
                    doc.getString("description") ?: "",
                    doc.getString("comment") ?: "",
                    doc.getString("imageUri"),
                    doc.getBoolean("parallel") ?: false,
                    doc.getBoolean("position") ?: false,
                    doc.getLong("order")?.toInt()
                )
            }
        }

        // Precompila dati esistenti se si sta modificando un'attività
        if (taskToEdit != null) {
            titleEditText.setText(taskToEdit.title)
            durationEditText.setText(taskToEdit.estimatedDurationMinutes.toString())

            // Carica le sottotask esistenti.
            // Prima tenta la query ottimale che richiede un indice composito.
            db.collection("subtasks")
                .whereEqualTo("taskId", taskToEdit.id)
                .orderBy("order")
                .get()
                .addOnSuccessListener { result ->
                    // Successo: i documenti sono già ordinati da Firestore.
                    populateSubtasks(result.documents)
                }
                .addOnFailureListener { e ->
                    db.collection("subtasks")
                        .whereEqualTo("taskId", taskToEdit.id)
                        .get()
                        .addOnSuccessListener { result ->
                            // Ordiniamo manualmente la lista dei documenti in base al campo "order", se l'ordinamento dalla query fallisce
                            val sortedDocs = result.documents.sortedBy { it.getLong("order") ?: Long.MAX_VALUE }
                            populateSubtasks(sortedDocs)
                        }
                }
        } else {
            // Se è una nuova attività, aggiungo una sottotask vuota di default.
            addSubtaskView(subtasksContainer)
        }

        // Configurazione del pulsante per aggiungere nuove sottotask
        addSubtaskButton.setOnClickListener {
            addSubtaskView(subtasksContainer)
        }

        builder.setView(dialogView)

        // Pulsante "Salva"
        builder.setPositiveButton("Salva") { dialog, _ ->
            val title = titleEditText.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Il titolo è obbligatorio", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val estimatedDuration = durationEditText.text.toString().toIntOrNull() ?: 10

            val taskRef = if (taskToEdit == null) db.collection("tasks").document() else db.collection("tasks").document(taskToEdit.id)

            if (taskToEdit == null) {
                // Logica per creare una NUOVA attività
                val taskData = hashMapOf(
                    "userId" to userId,
                    "title" to title,
                    "status" to "Da iniziare",
                    "estimatedDurationMinutes" to estimatedDuration,
                    "startedAt" to null,
                    "finishedAt" to null,
                    "vote" to null
                )
                // Calcola l'ordine di visualizzazione
                db.collection("tasks")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { tasks ->
                        val maxSortOrder = tasks.maxOfOrNull { it.getLong("sortOrder") ?: 0 } ?: 0
                        taskData["sortOrder"] = maxSortOrder + 1

                        taskRef.set(taskData).addOnSuccessListener {
                            saveSubtasks(subtasksContainer, taskRef.id)
                            loadTasksForUser(userId)
                            Toast.makeText(this, "Attività salvata", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Errore nel calcolo dell'ordine", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Logica per AGGIORNARE un'attività esistente
                db.collection("tasks").document(taskToEdit.id).get()
                    .addOnSuccessListener { snapshot ->
                        val currentStatus = snapshot.getString("status")
                        if (currentStatus != "Da iniziare") {
                            Toast.makeText(this, "Non puoi modificare un'attività già iniziata", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        val updates = mapOf(
                            "title" to title,
                            "estimatedDurationMinutes" to estimatedDuration
                        )

                        taskRef.update(updates).addOnSuccessListener {
                            saveSubtasks(subtasksContainer, taskRef.id)
                            loadTasksForUser(userId)
                            Toast.makeText(this, "Attività aggiornata", Toast.LENGTH_SHORT).show()
                        }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Errore durante l'aggiornamento: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Errore nel recupero stato: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        builder.setNegativeButton("Annulla") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }


    // Aggiunge una nuova view per l'input di una sottotask al container.
    private fun addSubtaskView(
        container: LinearLayout,
        description: String = "",
        comment: String = "",
        imageUri: String? = null,
        isParallel: Boolean = false,
        isPosition: Boolean = false,
        order: Int? = null
    ) {
        val inflater = LayoutInflater.from(this)
        val subtaskView = inflater.inflate(R.layout.item_subtask_input, container, false)

        val descEditText = subtaskView.findViewById<EditText>(R.id.subtaskDescriptionEditText)
        val commentEditText = subtaskView.findViewById<EditText>(R.id.subtaskCommentEditText)
        val deleteButton = subtaskView.findViewById<ImageButton>(R.id.deleteSubtaskButton)
        val loadImageButton = subtaskView.findViewById<ImageButton>(R.id.loadImageButton)
        val imageView = subtaskView.findViewById<ImageView>(R.id.subtaskImageView)
        val parallelCheckbox = subtaskView.findViewById<CheckBox>(R.id.subtaskParallelCheckbox)
        val positionCheckBox = subtaskView.findViewById<CheckBox>(R.id.shareLocationCheckBox)

        descEditText.setText(description)
        commentEditText.setText(comment)
        parallelCheckbox.isChecked = isParallel
        positionCheckBox.isChecked = isPosition

        // Carica l'immagine se presente
        if (imageUri != null && imageUri.isNotEmpty()) {
            android.util.Log.d("TaskListActivity", "Caricamento immagine: $imageUri")
            loadImageFromUrl(imageUri, imageView)
        } else {
            imageView.visibility = View.GONE
            imageView.tag = null
        }

        deleteButton.setOnClickListener {
            val currentImageUri = imageView.tag as? String
            currentImageUri?.let { uriString ->
                try {
                    val storageRefToDelete = storage.getReferenceFromUrl(uriString)
                    storageRefToDelete.delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Immagine associata eliminata da Storage.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Errore nell'eliminazione dell'immagine da Storage: ${e.message}", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Errore nel riferimento all'immagine di Storage.", Toast.LENGTH_SHORT).show()
                }
            }
            container.removeView(subtaskView)
        }

        loadImageButton.setOnClickListener {
            imagePickerCallback = { uri ->
                loadImageFromUrl(uri.toString(), imageView)
            }
            imagePickerLauncher.launch("image/*")
        }

        subtaskView.tag = order
        container.addView(subtaskView)
    }

    private fun loadImageFromUrl(imageUrl: String, imageView: ImageView) {
        // Uso un thread separato per il download
        Thread {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()

                val inputStream = connection.getInputStream()
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                // Torna al thread principale per aggiornare l'UI
                runOnUiThread {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        imageView.tag = imageUrl
                        android.util.Log.d("TaskListActivity", "Immagine caricata con successo: $imageUrl")
                    } else {
                        imageView.visibility = View.GONE
                        imageView.tag = null
                        Toast.makeText(this, "Errore nella decodifica dell'immagine", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskListActivity", "Errore nel caricamento immagine: ${e.message}")
                runOnUiThread {
                    imageView.visibility = View.GONE
                    imageView.tag = null
                    Toast.makeText(this, "Errore nel caricamento immagine: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun uploadImageToFirebaseStorage(fileUri: Uri, onSuccess: (Uri?) -> Unit) {
        val fileName = "images/${UUID.randomUUID()}.jpg" // Crea un percorso unico in Storage
        val storageRef = storage.reference.child(fileName)

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri) // Restituisce l'URL di download pubblico
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Errore nel recupero URL immagine: ${e.message}", Toast.LENGTH_LONG).show()
                    onSuccess(null)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore nel caricamento immagine su Storage: ${e.message}", Toast.LENGTH_LONG).show()
                onSuccess(null)
            }
    }

    private fun showRedeemDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Riscatta un premio per l'utente")

        val layout = layoutInflater.inflate(R.layout.dialog_redeem_prize, null)
        val prizeCountText = layout.findViewById<TextView>(R.id.prizeCountText)
        val redeemButton = layout.findViewById<Button>(R.id.confirmRedeemButton)

        val prizeDocRef = db.collection("prizes").document(userId)

        prizeDocRef.get().addOnSuccessListener { document ->
            var currentCount = document.getLong("count")?.toInt() ?: 0

            fun updateUI() {
                if (currentCount > 0) {
                    prizeCountText.text = "Premi disponibili: $currentCount"
                    redeemButton.isEnabled = true
                } else {
                    prizeCountText.text = "Nessun premio disponibile"
                    redeemButton.isEnabled = false
                }
            }

            updateUI()

            redeemButton.setOnClickListener {
                if (currentCount > 0) {
                    currentCount--
                    prizeDocRef.update("count", currentCount)
                        .addOnSuccessListener {
                            updateUI()
                            Toast.makeText(this, "Premio riscattato!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }

            builder.setView(layout)
            builder.setNegativeButton("Chiudi", null)
            builder.show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Errore nel recupero premi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoteDialog(task: Task) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Dai un voto all'attività")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        // RatingBar centrato
        val ratingContainer = FrameLayout(this)
        val ratingBar = RatingBar(this, null, android.R.attr.ratingBarStyle).apply {
            numStars = 5
            stepSize = 0.5f
            rating = task.vote?.toFloat() ?: 1f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        ratingContainer.addView(ratingBar)
        container.addView(ratingContainer)

        // Campo per il commento
        val commentInput = EditText(this).apply {
            hint = "Aggiungi un commento (opzionale)"
        }
        container.addView(commentInput)

        builder.setView(container)

        builder.setPositiveButton("Conferma") { _, _ ->
            val newVote = ratingBar.rating
            val commentText = commentInput.text.toString().trim()

            val taskRef = db.collection("tasks").document(task.id)

            taskRef.get().addOnSuccessListener { docSnapshot ->
                val userId = docSnapshot.getString("userId")
                if (userId == null) {
                    Toast.makeText(this, "Impossibile recuperare l'utente della task", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Salva il voto in Firestore
                taskRef.update("vote", newVote)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Voto salvato", Toast.LENGTH_SHORT).show()

                        // Se presente, invia un commento in chat all’utente
                        if (commentText.isNotEmpty()) {
                            val caregiverId = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener

                            sendMessageToChat(
                                senderId = caregiverId,
                                recipientId = userId,
                                text = "Hai ricevuto un voto di $newVote su \"${task.title}\":\n$commentText",
                                imageUrl = null
                            )
                        }

                        // Aggiorna premi se voto = 5
                        if (newVote == 5f) {
                            val prizeDocRef = db.collection("prizes").document(userId)

                            prizeDocRef.get().addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val currentCount = document.getLong("count") ?: 0
                                    prizeDocRef.update("count", currentCount + 1)
                                } else {
                                    prizeDocRef.set(mapOf("count" to 1))
                                }
                            }.addOnFailureListener { e ->
                                Toast.makeText(this, "Errore premi: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        loadTasksForUser(userId)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Errore nel salvataggio del voto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        builder.setNegativeButton("Annulla", null)
        builder.show()
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

    private fun saveSubtasks(container: LinearLayout, taskId: String) {
        // batch serve per eseguire operazioni multiple in modo atomico.
        val batch = db.batch()

        // trovo tutte le sottotask esistenti associate a questa attività.
        val oldSubtasksQuery = db.collection("subtasks").whereEqualTo("taskId", taskId)

        oldSubtasksQuery.get()
            .addOnSuccessListener { oldSubtasksSnapshot ->
                // elimino le vecchie sottotask. Per poter ottenere delle modifiche più pulite
                for (doc in oldSubtasksSnapshot.documents) {
                    batch.delete(doc.reference)
                }

                // Itera attraverso le view delle sottotask attualmente presenti nel dialogo.
                for (i in 0 until container.childCount) {
                    val subtaskView = container.getChildAt(i)
                    val descEditText = subtaskView.findViewById<EditText>(R.id.subtaskDescriptionEditText)
                    val commentEditText = subtaskView.findViewById<EditText>(R.id.subtaskCommentEditText)
                    val imageView = subtaskView.findViewById<ImageView>(R.id.subtaskImageView)
                    val parallelCheckbox = subtaskView.findViewById<CheckBox>(R.id.subtaskParallelCheckbox)
                    val positionCheckBox = subtaskView.findViewById<CheckBox>(R.id.shareLocationCheckBox)

                    val description = descEditText.text.toString().trim()

                    // Salva una sottotask solo se ha una descrizione.
                    if (description.isNotEmpty()) {
                        val comment = commentEditText.text.toString().trim()
                        val imageUri = imageView.tag as? String // Recupera l'URL dell'immagine dal tag
                        val isParallel = parallelCheckbox.isChecked
                        val isPosition = positionCheckBox.isChecked
                        val order = i

                        // Crea un riferimento per il nuovo documento della sottotask.
                        val newSubtaskRef = db.collection("subtasks").document()

                        // Prepara i dati da salvare.
                        val subtaskData = hashMapOf(
                            "description" to description,
                            "comment" to comment,
                            "taskId" to taskId,
                            "parallel" to isParallel,
                            "position" to isPosition,
                            "createdAt" to Timestamp.now(),
                            "order" to order,
                            "startedAt" to null as Timestamp?,
                            "finishedAt" to null as Timestamp?
                        )

                        // Aggiungo l'URL dell'immagine solo se esiste.
                        if (!imageUri.isNullOrEmpty()) {
                            subtaskData["imageUri"] = imageUri
                        }

                        // Aggiungo l'operazione di creazione della nuova sottotask al batch.
                        batch.set(newSubtaskRef, subtaskData)
                    }
                }

                // Eseguo le operazioni del batch in modo atomico
                batch.commit()
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Errore critico nel salvataggio delle sottotask: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Impossibile leggere le sottotask esistenti per l'aggiornamento: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun deleteTask(task: Task) {
        // Prima elimina tutte le sottotask associate e le loro immagini da Firebase Storage
        db.collection("subtasks")
            .whereEqualTo("taskId", task.id)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch() //permette di salvare le operazioni da svolgere ed effettuarle tutte in contemporanea (scritture e rimozioni)
                for (doc in querySnapshot.documents) {
                    // Elimina il file immagine associato da Firebase Storage
                    val imageUriString = doc.getString("imageUri")
                    imageUriString?.let { uriString ->
                        try {
                            val storageRefToDelete = storage.getReferenceFromUrl(uriString)
                            storageRefToDelete.delete()
                                .addOnFailureListener { e ->
                                    // Logga l'errore, ma non blocca l'eliminazione della sottotask
                                    e.printStackTrace()
                                }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    batch.delete(doc.reference)
                }
                // Poi elimina l'attività stessa
                batch.delete(db.collection("tasks").document(task.id))

                batch.commit() // avvio lo svolgimento delle azioni raggruppate
                    .addOnSuccessListener {
                        taskList.remove(task)
                        taskAdapter.notifyDataSetChanged()
                        loadTasksForUser(userId)
                        Toast.makeText(this, "Attività e sottotask eliminate", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Errore durante l'eliminazione: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore durante il recupero delle sottotask per eliminazione: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTasksForUser(userId: String) {
        db.collection("tasks")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                taskList.clear()
                for (doc in documents) {
                    val sortOrder = doc.getLong("sortOrder")?.toInt()
                    if (sortOrder == null) continue // Ignora attività senza sortOrder

                    val task = Task(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        status = doc.getString("status") ?: "Da iniziare",
                        estimatedDurationMinutes = doc.getLong("estimatedDurationMinutes")?.toInt() ?: 0,
                        sortOrder = sortOrder,
                        startedAt = doc.getTimestamp("startedAt"),
                        finishedAt = doc.getTimestamp("finishedAt"),
                        vote = doc.getDouble("vote")
                    )
                    checkTaskOverdue(task)
                    taskList.add(task)
                }

                // Ordina le attività per sortOrder
                taskList.sortByDescending { it.sortOrder }

                taskAdapter.notifyDataSetChanged()
                emptyText.visibility = if (taskList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Errore nel caricamento delle attività: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
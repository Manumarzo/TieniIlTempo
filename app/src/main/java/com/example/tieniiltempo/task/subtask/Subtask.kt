package com.example.tieniiltempo.task

import com.google.firebase.Timestamp

data class Subtask(
    var id: String = "",
    val description: String = "",
    val comment: String? = null,
    val taskId: String = "",
    val imageUri: String? = null,
    val parallel: Boolean = false,
    val position: Boolean = false,
    val createdAt: Timestamp? = null,
    var order: Int = 0,
    var status: String = "Da iniziare",
    var startedAt: Timestamp? = null,
    var finishedAt: Timestamp? = null
)
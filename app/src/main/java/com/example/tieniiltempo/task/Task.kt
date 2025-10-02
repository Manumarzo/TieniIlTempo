package com.example.tieniiltempo.task

import com.google.firebase.Timestamp

data class Task(
    val id: String = "",
    var title: String = "",
    var estimatedDurationMinutes: Int = 0, // in minuti
    var status: String = "Da iniziare",
    var sortOrder: Int = 0,
    var startedAt: Timestamp? = null,
    var finishedAt: Timestamp? = null,
    var vote: Double? = null
)





package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: Int,
    val contactName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "Call", "Message", "Email"
    val outcome: String = "Completed"
)

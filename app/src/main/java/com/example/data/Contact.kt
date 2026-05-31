package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val company: String = "",
    val category: String = "Personal",
    val isFavorite: Boolean = false,
    val notes: String = "",
    val avatarColorHex: String = "#6200EE",
    val reminderTime: Long? = null,
    val reminderMessage: String? = null,
    val lastContacted: Long = 0L
) : Serializable {
    val fullName: String
        get() = "$firstName $lastName".trim()

    val initials: String
        get() {
            val f = firstName.firstOrNull()?.uppercase() ?: ""
            val l = lastName.firstOrNull()?.uppercase() ?: ""
            return if (f.isNotEmpty() && l.isNotEmpty()) "$f$l" else if (f.isNotEmpty()) f else "C"
        }
}

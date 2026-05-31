package com.example.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallLog
import com.example.data.Contact
import com.example.data.ContactRepository
import com.example.receiver.ReminderReceiver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val prefName = "easycaller_settings"
    private val keyTheme = "theme_mode" // "system", "light", "dark"
    private val prefs = application.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    private val database = AppDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao())

    // Theme state
    private val _themeMode = MutableStateFlow(prefs.getString(keyTheme, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active Contact ID for detail screen
    private val _selectedContactId = MutableStateFlow<Int?>(null)
    val selectedContactId: StateFlow<Int?> = _selectedContactId.asStateFlow()

    // Contacts stream based on search query
    @OptIn(ExperimentalCoroutinesApi::class)
    val contactsList: StateFlow<List<Contact>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allContacts
            } else {
                repository.searchContacts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteContacts: StateFlow<List<Contact>> = repository.favoriteContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLog>> = repository.allCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedContact: StateFlow<Contact?> = _selectedContactId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getContactById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val upcomingReminders: StateFlow<List<Contact>> = repository.getUpcomingReminders(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically check if database is empty and seed sample contacts
        viewModelScope.launch {
            repository.allContacts.first().let { currentList ->
                if (currentList.isEmpty()) {
                    seedSampleData()
                }
            }
        }
    }

    private suspend fun seedSampleData() {
        val sampleContacts = listOf(
            Contact(
                firstName = "Tony",
                lastName = "Stark",
                phone = "+1 (555) 382-7827",
                email = "tony@starkindustries.com",
                company = "Stark Industries",
                category = "Work",
                isFavorite = true,
                notes = "Genius, billionaire, playboy, philanthropist. Demands high quality audio calls.",
                avatarColorHex = "#E23636" // Crimson
            ),
            Contact(
                firstName = "Bruce",
                lastName = "Wayne",
                phone = "+1 (555) 911-0000",
                email = "bruce@waynecorp.com",
                company = "Wayne Enterprises",
                category = "Family",
                isFavorite = true,
                notes = "Prefers dark mode. Usually calls during the night.",
                avatarColorHex = "#2B2B2B" // Dark Gray
            ),
            Contact(
                firstName = "Peter",
                lastName = "Parker",
                phone = "+1 (555) 762-9273",
                email = "peter.parker@dailybugle.com",
                company = "Daily Bugle",
                category = "Personal",
                isFavorite = false,
                notes = "Always forgets to pick up milk. Take photos of Spider-Man.",
                avatarColorHex = "#2196F3" // Beautiful Blue
            ),
            Contact(
                firstName = "Diana",
                lastName = "Prince",
                phone = "+1 (555) 888-1984",
                email = "diana@themyscira.org",
                company = "The Louvre",
                category = "Personal",
                isFavorite = true,
                notes = "Extremely busy. Fluent in 100 languages. Keep on Favorite dials.",
                avatarColorHex = "#009688" // Teal
            ),
            Contact(
                firstName = "Sarah",
                lastName = "Connor",
                phone = "+1 (555) 019-2831",
                email = "sconnor@cyberdyne.net",
                company = "Resistance HQ",
                category = "Work",
                isFavorite = false,
                notes = "Very alert. Watch out for Terminators.",
                avatarColorHex = "#FF9800" // Vivid Amber
            )
        )
        sampleContacts.forEach { repository.insertContact(it) }

        // Seed some starter Call Logs
        val designLogs = listOf(
            CallLog(contactId = 1, contactName = "Tony Stark", type = "Call", outcome = "Completed"),
            CallLog(contactId = 2, contactName = "Bruce Wayne", type = "Message", outcome = "Replied"),
            CallLog(contactId = 4, contactName = "Diana Prince", type = "Call", outcome = "Missed")
        )
        designLogs.forEach { repository.insertCallLog(it) }
    }

    fun setThemeMode(mode: String) {
        if (mode in listOf("system", "light", "dark")) {
            _themeMode.value = mode
            prefs.edit().putString(keyTheme, mode).apply()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedContactId(id: Int?) {
        _selectedContactId.value = id
    }

    fun saveContact(
        id: Int,
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        company: String,
        category: String,
        isFavorite: Boolean,
        notes: String,
        avatarColorHex: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val contact = Contact(
                id = if (id == 0) 0 else id,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = phone.trim(),
                email = email.trim(),
                company = company.trim(),
                category = category,
                isFavorite = isFavorite,
                notes = notes,
                avatarColorHex = avatarColorHex
            )
            repository.insertContact(contact)
            onSuccess()
        }
    }

    fun toggleFavorite(contact: Contact) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(isFavorite = !contact.isFavorite))
        }
    }

    fun deleteContact(contact: Contact, onSuccess: () -> Unit) {
        viewModelScope.launch {
            cancelReminderInOS(contact.id)
            repository.deleteContact(contact)
            onSuccess()
        }
    }

    fun registerCallInteraction(contact: Contact, type: String) {
        viewModelScope.launch {
            // Update last contacted time
            repository.updateContact(contact.copy(lastContacted = System.currentTimeMillis()))
            // Log interaction
            repository.insertCallLog(
                CallLog(
                    contactId = contact.id,
                    contactName = contact.fullName,
                    type = type,
                    outcome = "Initiated out"
                )
            )
        }
    }

    fun scheduleReminder(contact: Contact, timeMs: Long, message: String) {
        viewModelScope.launch {
            val updated = contact.copy(reminderTime = timeMs, reminderMessage = message)
            repository.updateContact(updated)
            scheduleReminderInOS(updated, timeMs, message)
        }
    }

    fun cancelReminder(contact: Contact) {
        viewModelScope.launch {
            val updated = contact.copy(reminderTime = null, reminderMessage = null)
            repository.updateContact(updated)
            cancelReminderInOS(contact.id)
        }
    }

    private fun scheduleReminderInOS(contact: Contact, timeMs: Long, message: String) {
        try {
            val context = getApplication<Application>()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("contact_id", contact.id)
                putExtra("contact_name", contact.fullName)
                putExtra("reminder_message", message)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                contact.id,
                intent,
                pendingIntentFlags
            )

            // Cancel any pre-existing alarm first
            alarmManager.cancel(pendingIntent)

            // Schedule alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeMs, pendingIntent)
            }
            Log.d("ContactViewModel", "Successfully scheduled alarm for ${contact.fullName} at $timeMs")
        } catch (e: Exception) {
            Log.e("ContactViewModel", "Failed to schedule alarm", e)
        }
    }

    private fun cancelReminderInOS(contactId: Int) {
        try {
            val context = getApplication<Application>()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                contactId,
                intent,
                pendingIntentFlags
            )

            alarmManager.cancel(pendingIntent)
            Log.d("ContactViewModel", "Cancelled alarm for contactId: $contactId")
        } catch (e: Exception) {
            Log.e("ContactViewModel", "Failed to cancel alarm", e)
        }
    }

    fun getCallLogsForContact(contactId: Int): Flow<List<CallLog>> {
        return repository.getCallLogsForContact(contactId)
    }

    // Direct Notification Poster helper to easily trigger a notification right away for testing!
    fun triggerInstantNotification(contact: Contact) {
        val context = getApplication<Application>()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("contact_id", contact.id)
            putExtra("contact_name", contact.fullName)
            putExtra("reminder_message", "Instant test reminder! Reminding you to reach out to ${contact.firstName}!")
        }
        context.sendBroadcast(intent)
    }
}

package com.example.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val favoriteContacts: Flow<List<Contact>> = contactDao.getFavoriteContacts()
    val allCallLogs: Flow<List<CallLog>> = contactDao.getAllCallLogs()

    fun getContactById(id: Int): Flow<Contact?> = contactDao.getContactByIdFlow(id)

    suspend fun getContactByIdDirect(id: Int): Contact? = contactDao.getContactById(id)

    fun searchContacts(query: String): Flow<List<Contact>> = contactDao.searchContacts(query)

    suspend fun insertContact(contact: Contact): Long = contactDao.insertContact(contact)

    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteCallLogsForContact(contact.id)
        contactDao.deleteContact(contact)
    }

    fun getCallLogsForContact(contactId: Int): Flow<List<CallLog>> = contactDao.getCallLogsForContact(contactId)

    suspend fun insertCallLog(callLog: CallLog) = contactDao.insertCallLog(callLog)

    fun getUpcomingReminders(currentTime: Long): Flow<List<Contact>> = contactDao.getUpcomingReminders(currentTime)
}

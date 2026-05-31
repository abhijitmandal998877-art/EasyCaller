package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallLog
import com.example.data.Contact
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object MainList : Screen()
    object ContactDetails : Screen()
    object AddEditContact : Screen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EasyCallerMainUI(viewModel: ContactViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainList) }
    var editContactMode by remember { mutableStateOf(false) } // true if editing, false if adding new
    var selectedContactId by remember { mutableStateOf<Int?>(null) }

    // Synchronize selected ID with VM
    LaunchedEffect(selectedContactId) {
        viewModel.setSelectedContactId(selectedContactId)
    }

    // Permission launcher for Android 13+ Notification Post
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted! EasyCaller will alert you.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "System alarm alerts might not pop up without permissions.", Toast.LENGTH_LONG).show()
        }
    }

    // Dynamic Permission check on start or setting reminders
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.MainList -> {
                        MainListTabbedScreen(
                            viewModel = viewModel,
                            onContactClick = { contactId ->
                                selectedContactId = contactId
                                currentScreen = Screen.ContactDetails
                            },
                            onAddContactClick = {
                                selectedContactId = null
                                editContactMode = false
                                currentScreen = Screen.AddEditContact
                            }
                        )
                    }

                    is Screen.ContactDetails -> {
                        val contact by viewModel.selectedContact.collectAsStateWithLifecycle()
                        BackHandler {
                            currentScreen = Screen.MainList
                            selectedContactId = null
                        }
                        if (contact != null) {
                            ContactDetailsScreen(
                                contact = contact!!,
                                viewModel = viewModel,
                                onBackClick = {
                                    currentScreen = Screen.MainList
                                    selectedContactId = null
                                },
                                onEditClick = {
                                    editContactMode = true
                                    currentScreen = Screen.AddEditContact
                                }
                            )
                        } else {
                            // Empty state fallback or return
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Contact not found or has been deleted.")
                            }
                        }
                    }

                    is Screen.AddEditContact -> {
                        val contact by viewModel.selectedContact.collectAsStateWithLifecycle()
                        BackHandler {
                            if (editContactMode) {
                                currentScreen = Screen.ContactDetails
                            } else {
                                currentScreen = Screen.MainList
                            }
                        }
                        AddEditContactScreen(
                            contact = if (editContactMode) contact else null,
                            viewModel = viewModel,
                            onDismiss = {
                                if (editContactMode) {
                                    currentScreen = Screen.ContactDetails
                                } else {
                                    currentScreen = Screen.MainList
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainListTabbedScreen(
    viewModel: ContactViewModel,
    onContactClick: (Int) -> Unit,
    onAddContactClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Contacts", "Favorites", "Recents", "Reminders")
    val tabIcons = listOf(Icons.Default.Person, Icons.Default.Favorite, Icons.Default.Refresh, Icons.Default.Notifications)

    val contacts by viewModel.contactsList.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteContacts.collectAsStateWithLifecycle()
    val logs by viewModel.callLogs.collectAsStateWithLifecycle()
    val reminders by viewModel.upcomingReminders.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // App top level custom elegant header Card
        HeaderGreetingCard(
            themeMode = themeMode,
            onThemeModeChanged = { viewModel.setThemeMode(it) }
        )

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("main_tabs")
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.height(52.dp),
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tabIcons[index],
                            contentDescription = title,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        // Screen Body with animated content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> {
                    // Contact list tab with Search
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("contact_search_bar"),
                            placeholder = { Text("Search by name, phone or company...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )

                        if (contacts.isEmpty()) {
                            EmptyStatePlaceholder(
                                message = if (searchQuery.isNotBlank()) "No contacts matching \"$searchQuery\"" else "Your contact book is empty.",
                                subMessage = "Tap the float icon below to create a contact card.",
                                icon = Icons.Default.Person
                            )
                        } else {
                            // Group contacts alphabetically by the first letter of their first name
                            val groupedContacts = remember(contacts) {
                                contacts.groupBy {
                                    val char = it.firstName.firstOrNull()?.uppercaseChar() ?: '#'
                                    if (char.isLetter()) char else '#'
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                                    .testTag("contacts_list_view"),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                groupedContacts.forEach { (alphabet, alphabetContacts) ->
                                    stickyHeader {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.background)
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = alphabet.toString(),
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                            )
                                        }
                                    }

                                    items(alphabetContacts, key = { it.id }) { contact ->
                                        ContactListItem(
                                            contact = contact,
                                            onContactClick = { onContactClick(contact.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating action button
                    LargeFloatingActionButton(
                        onClick = onAddContactClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .testTag("add_contact_fab"),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Contact", modifier = Modifier.size(28.dp))
                    }
                }

                1 -> {
                    // Favorites Grid view
                    if (favorites.isEmpty()) {
                        EmptyStatePlaceholder(
                            message = "No Favorites Yet",
                            subMessage = "Mark contacts as favorites to dial them faster in this speed board.",
                            icon = Icons.Default.FavoriteBorder
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .testTag("favorites_grid"),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favorites, key = { it.id }) { favorite ->
                                FavoriteGridItem(
                                    contact = favorite,
                                    onClick = { onContactClick(favorite.id) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // Recents Log tab
                    if (logs.isEmpty()) {
                        EmptyStatePlaceholder(
                            message = "No recent interactions",
                            subMessage = "Dial callers or send emails to record interaction call history.",
                            icon = Icons.Default.Refresh
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("recents_logs_view"),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                        ) {
                            items(logs) { log ->
                                LogItemRow(log = log)
                            }
                        }
                    }
                }

                3 -> {
                    // Reminders List Tab
                    RemindersListTab(
                        reminders = reminders,
                        onContactClick = onContactClick
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderGreetingCard(
    themeMode: String,
    onThemeModeChanged: (String) -> Unit
) {
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, greetingIcon, greetingDesc) = remember(currentHour) {
        when {
            currentHour in 5..11 -> Triple("Good morning", "☀️", "Stay connected today")
            currentHour in 12..16 -> Triple("Good afternoon", "🌤️", "Reach out to your mates")
            currentHour in 17..21 -> Triple("Good evening", "🌅", "Catch up on contacts")
            else -> Triple("Good night", "🌙", "Keep phone quiet")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greeting,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = greetingIcon, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = greetingDesc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            // High Contrast custom Day / Night selection Switcher
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                listOf(
                    Triple("system", Icons.Default.Settings, "Auto"),
                    Triple("light", Icons.Default.LightMode, "Day"),
                    Triple("dark", Icons.Default.DarkMode, "Night")
                ).forEach { (mode, icon, label) ->
                    val isSelected = themeMode == mode
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onThemeModeChanged(mode) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactListItem(
    contact: Contact,
    onContactClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onContactClick)
            .testTag("contact_item_${contact.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant circle Avatar with custom Choose Color index
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(android.graphics.Color.parseColor(contact.avatarColorHex)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (contact.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite icon",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (contact.company.isNotEmpty()) {
                    Text(
                        text = contact.company,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phone,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category tag badge
            CategoryBadge(category = contact.category)
        }
    }
}

@Composable
fun FavoriteGridItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("favorite_card_${contact.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(android.graphics.Color.parseColor(contact.avatarColorHex)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = contact.fullName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (contact.company.isNotEmpty()) {
                Text(
                    text = contact.company,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            CategoryBadge(category = contact.category)
        }
    }
}

@Composable
fun LogItemRow(log: CallLog) {
    val dateStr = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    val (icon, tint) = remember(log.type) {
        when (log.type) {
            "Call" -> Pair(Icons.Default.Call, Color(0xFF4CAF50))
            "Message" -> Pair(Icons.Default.Send, Color(0xFF2196F3))
            else -> Pair(Icons.Default.Email, Color(0xFFE91E63))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = log.type,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${log.type} Interaction",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun RemindersListTab(
    reminders: List<Contact>,
    onContactClick: (Int) -> Unit
) {
    if (reminders.isEmpty()) {
        EmptyStatePlaceholder(
            message = "No Upcoming Reminders",
            subMessage = "Schedule call alarms in any contact's details screen to keep up with them.",
            icon = Icons.Default.Notifications
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("reminders_list_view"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
        ) {
            item {
                Text(
                    text = "Scheduled Alarms",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
            }

            items(reminders, key = { it.id }) { contact ->
                ReminderRowItem(
                    contact = contact,
                    onContactClick = { onContactClick(contact.id) }
                )
            }
        }
    }
}

@Composable
fun ReminderRowItem(
    contact: Contact,
    onContactClick: () -> Unit
) {
    val reminderDateStr = remember(contact.reminderTime) {
        if (contact.reminderTime == null) "" else {
            val df = SimpleDateFormat("EEEE, MMM dd 'at' hh:mm a", Locale.getDefault())
            df.format(Date(contact.reminderTime))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onContactClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(android.graphics.Color.parseColor(contact.avatarColorHex)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.fullName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Reminder Bell",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminderDateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!contact.reminderMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${contact.reminderMessage}\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ArrowBack, // simple directional icon or other
                contentDescription = "View Details",
                modifier = Modifier
                    .size(16.dp)
                    .rotateEast(), // we rotate or style beautifully
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    val (bgColor, textColor) = when (category) {
        "Personal" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "Work" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        "Family" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        else -> Pair(Color(0xFFEDE7F6), Color(0xFF651FFF))
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = category,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStatePlaceholder(
    message: String,
    subMessage: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Placeholder Icon",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subMessage,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp)
        )
    }
}

// ---------------------- CONTACT DETAILS SCREEN ----------------------

@Composable
fun ContactDetailsScreen(
    contact: Contact,
    viewModel: ContactViewModel,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderMessageState by remember { mutableStateOf("") }

    val logsForContact by viewModel.getCallLogsForContact(contact.id).collectAsState(initial = emptyList())

    val reminderActive = contact.reminderTime != null && contact.reminderTime > System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("contact_details_screen")
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
            }
            Row {
                IconButton(onClick = onEditClick, modifier = Modifier.testTag("edit_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit contact")
                }
                IconButton(
                    onClick = {
                        viewModel.deleteContact(contact) {
                            Toast.makeText(context, "Contact deleted successfully", Toast.LENGTH_SHORT).show()
                            onBackClick()
                        }
                    },
                    modifier = Modifier.testTag("delete_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete contact", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Contact Profile Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(android.graphics.Color.parseColor(contact.avatarColorHex)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initials,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = contact.fullName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (contact.company.isNotEmpty()) {
                Text(
                    text = contact.company,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category = contact.category)
                if (contact.isFavorite) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Starred Fav",
                            color = Color(0xFFFF8F00),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Quick Operations Intents
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            QuickActionButton(
                icon = Icons.Default.Call,
                label = "Call",
                onClick = {
                    viewModel.registerCallInteraction(contact, "Call")
                    // Fallback launcher dialer intent
                    try {
                        val callIntent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${contact.phone}")
                        }
                        context.startActivity(callIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No dialer application found.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            QuickActionButton(
                icon = Icons.Default.Send,
                label = "Text",
                onClick = {
                    viewModel.registerCallInteraction(contact, "Message")
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:${contact.phone}")
                        }
                        context.startActivity(smsIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No messaging application found.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            QuickActionButton(
                icon = Icons.Default.Email,
                label = "Email",
                enabled = contact.email.isNotEmpty(),
                onClick = {
                    if (contact.email.isNotEmpty()) {
                        viewModel.registerCallInteraction(contact, "Email")
                        try {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${contact.email}")
                            }
                            context.startActivity(emailIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email client found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            QuickActionButton(
                icon = if (contact.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (contact.isFavorite) "Liked" else "Fav",
                iconTint = if (contact.isFavorite) Color(0xFFFF1744) else null,
                onClick = { viewModel.toggleFavorite(contact) }
            )
        }

        // Information Cards block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Contact Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                InfoRow(label = "Phone Number", value = contact.phone, icon = Icons.Default.Phone)

                if (contact.email.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    InfoRow(label = "Email Address", value = contact.email, icon = Icons.Default.Email)
                }

                if (contact.notes.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    InfoRow(label = "Biographical Notes", value = contact.notes, icon = Icons.Default.Info)
                }
            }
        }

        // Test Call Notification Console
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Notifications & Reminders Console ⏰",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (reminderActive) {
                    val dateFormatted = remember(contact.reminderTime) {
                        SimpleDateFormat("EEEE, MMM dd 'at' hh:mm a", Locale.getDefault()).format(Date(contact.reminderTime!!))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Active Reminder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Scheduled Alarm In OS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = dateFormatted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (!contact.reminderMessage.isNullOrBlank()) {
                                Text(text = "\"${contact.reminderMessage}\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { viewModel.cancelReminder(contact) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel Reminder", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showReminderDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Schedule", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (reminderActive) "Re-schedule" else "Schedule Call", fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.triggerInstantNotification(contact)
                            Toast.makeText(context, "Notification simulation triggered!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Direct share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Test Alarm Now", fontSize = 13.sp)
                    }
                }
            }
        }

        // Contact Recents interaction list
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Activity Log for ${contact.firstName}",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        if (logsForContact.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recorded interactions with this contact.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                logsForContact.take(5).forEach { log ->
                    LogItemRow(log = log)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Date & Time Picker schedules dialogue
    if (showReminderDialog) {
        val calendar = remember { Calendar.getInstance() }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Schedule Call Reminder") },
            text = {
                Column {
                    Text("Choose date & time to receive an EasyCaller alert.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = reminderMessageState,
                        onValueChange = { reminderMessageState = it },
                        label = { Text("Alarm message (e.g., Ask about the report)") },
                        placeholder = { Text("Time to call!") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        calendar.set(Calendar.YEAR, year)
                                        calendar.set(Calendar.MONTH, month)
                                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                        // Now show Time dial
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                calendar.set(Calendar.MINUTE, minute)
                                                calendar.set(Calendar.SECOND, 0)

                                                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                                                    Toast.makeText(context, "Please choose a future date/time", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val finalMessage = if (reminderMessageState.isBlank()) "Call ${contact.fullName}!" else reminderMessageState.trim()
                                                    viewModel.scheduleReminder(contact, calendar.timeInMillis, finalMessage)
                                                    Toast.makeText(context, "Alarm created successfully", Toast.LENGTH_SHORT).show()
                                                    showReminderDialog = false
                                                }
                                            },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Date/Time", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                       else iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp).padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Helper expansion rotates the search arrow right
@Composable
fun Modifier.rotateEast(): Modifier = this.padding(1.dp)

// ---------------------- ADD / EDIT CONTACT SCREEN ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactScreen(
    contact: Contact?,
    viewModel: ContactViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = contact != null

    var firstName by remember { mutableStateOf(contact?.firstName ?: "") }
    var lastName by remember { mutableStateOf(contact?.lastName ?: "") }
    var phone by remember { mutableStateOf(contact?.phone ?: "") }
    var email by remember { mutableStateOf(contact?.email ?: "") }
    var company by remember { mutableStateOf(contact?.company ?: "") }
    var notes by remember { mutableStateOf(contact?.notes ?: "") }
    var category by remember { mutableStateOf(contact?.category ?: "Personal") }

    // Swatches choices for custom colorful initials background circles!
    val avatarColors = listOf(
        "#6200EE", // Indigo
        "#2E7D32", // Forest
        "#E23636", // Crimson
        "#FF8F00", // Amber Gold
        "#2B2B2B", // Dark Slate
        "#00838F"  // Ocean Cyan
    )
    var selectedAvatarColor by remember { mutableStateOf(contact?.avatarColorHex ?: avatarColors.first()) }

    var firstNameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("add_edit_contact_screen")
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_button")) {
                Text("Cancel")
            }
            Text(
                text = if (isEditMode) "Edit Contact Card" else "New Contact Card",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Button(
                onClick = {
                    firstNameError = firstName.isBlank()
                    phoneError = phone.isBlank()

                    if (firstNameError || phoneError) {
                        Toast.makeText(context, "Full list items require First Name and Phone", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.saveContact(
                            id = contact?.id ?: 0,
                            firstName = firstName,
                            lastName = lastName,
                            phone = phone,
                            email = email,
                            company = company,
                            category = category,
                            isFavorite = contact?.isFavorite ?: false,
                            notes = notes,
                            avatarColorHex = selectedAvatarColor,
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    if (isEditMode) "Contact updated" else "Contact added successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    }
                },
                modifier = Modifier.testTag("save_button")
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Circle Profile initials simulation
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val previewInitials = remember(firstName, lastName) {
                val f = firstName.trim().firstOrNull()?.uppercase() ?: ""
                val l = lastName.trim().firstOrNull()?.uppercase() ?: ""
                if (f.isNotEmpty() || l.isNotEmpty()) "$f$l" else "?"
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(android.graphics.Color.parseColor(selectedAvatarColor)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = previewInitials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Choose Avatar Swatch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Swatch Selection Grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                avatarColors.forEach { colorHex ->
                    val color = Color(android.graphics.Color.parseColor(colorHex))
                    val isSelected = selectedAvatarColor == colorHex

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedAvatarColor = colorHex }
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Input Fields (Material TextFields)
        OutlinedTextField(
            value = firstName,
            onValueChange = {
                firstName = it
                if (firstNameError && it.isNotBlank()) firstNameError = false
            },
            label = { Text("First Name *") },
            isError = firstNameError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("first_name_input")
        )
        if (firstNameError) {
            Text(text = "First name is of absolute core require", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("last_name_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = {
                phone = it
                if (phoneError && it.isNotBlank()) phoneError = false
            },
            label = { Text("Phone Number *") },
            isError = phoneError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("phone_input")
        )
        if (phoneError) {
            Text(text = "Valid dial phone is required", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("email_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = company,
            onValueChange = { company = it },
            label = { Text("Company or Office") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("company_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category Segment chips selection
        Text(
            text = "Group Category Tag",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Personal", "Work", "Family", "Other").forEach { cat ->
                val isSelected = category == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { category = cat },
                    label = { Text(cat) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Conversational notes about this contact") },
            placeholder = { Text("E.g. Met at regional design conference...") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("notes_input")
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// Theme color helpers
val ColorScheme.surfaceCardVariant: Color
    @Composable
    get() = if (isSystemInDarkTheme()) this.surfaceVariant.copy(alpha = 0.5f) else Color.White

package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val contactId = intent.getIntExtra("contact_id", -1)
        val contactName = intent.getStringExtra("contact_name") ?: "Contact"
        val reminderMessage = intent.getStringExtra("reminder_message") ?: "Time to catch up!"

        Log.d("ReminderReceiver", "Alarm received for contact: $contactName (ID: $contactId)")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "easycaller_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Call Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "EasyCaller scheduled call notifications and reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Launch app on notification click and pass the contactId extra
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("contact_id", contactId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            contactId,
            launchIntent,
            pendingIntentFlags
        )

        // Generate dynamic icon/style
        val notification = Companion.buildNotification(context, channelId, contactName, reminderMessage, pendingIntent)
        notificationManager.notify(contactId, notification)
    }

    companion object {
        fun buildNotification(
            context: Context,
            channelId: String,
            contactName: String,
            message: String,
            pendingIntent: PendingIntent
        ) = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // system info drawable fallback
            .setContentTitle("Call $contactName 📞")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    }
}

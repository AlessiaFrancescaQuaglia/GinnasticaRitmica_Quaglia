package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // HOST ACTIVITY: Carica il layout principale che fa da contenitore per i Fragment (NavGraph).
        setContentView(R.layout.activity_main)

        // RUNTIME PERMISSION & NOTIFICATION CHANNEL: Da fare all'avvio dell'app per tutti
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

            val channel = android.app.NotificationChannel(
                "CANALE_SCADENZE",
                "Avvisi Scadenze Certificati",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifiche per i certificati medici scaduti delle atlete"

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
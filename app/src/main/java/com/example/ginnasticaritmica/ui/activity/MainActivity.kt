package com.example.ginnasticaritmica.ui.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ginnasticaritmica.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // HOST ACTIVITY: Carica il layout principale che fa da contenitore per i Fragment (NavGraph).
        setContentView(R.layout.activity_main)

        // RUNTIME PERMISSION & NOTIFICATION CHANNEL: Da fare all'avvio dell'app per tutti
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

            val channel = NotificationChannel(
                "CANALE_SCADENZE",
                "Avvisi Scadenze Certificati",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifiche per i certificati medici scaduti delle atlete"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
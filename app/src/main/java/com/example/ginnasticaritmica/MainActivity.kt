package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Questo comando carica la "cornice" vuota, e il NavGraph farà il resto
        setContentView(R.layout.activity_main)


        // --- CREAZIONE CANALE NOTIFICHE (Lezione 14) ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            val channel = android.app.NotificationChannel(
                "CANALE_SCADENZE", // ID del canale
                "Avvisi Scadenze Certificati", // Nome visibile all'utente nelle impostazioni
                android.app.NotificationManager.IMPORTANCE_HIGH // Importanza alta (suona e appare a schermo)
            )
            channel.description = "Notifiche per i certificati medici scaduti delle atlete"

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // --- SCHEDULAZIONE DEL WORKER IN BACKGROUND (Lezione 15) ---
        // Creiamo una richiesta di lavoro che si ripete ogni 1 giorno (24 ore)
        val controlloScadenzeRequest = androidx.work.PeriodicWorkRequestBuilder<ScadenzaWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()

        // Diciamo al WorkManager di metterlo in coda. "KEEP" significa che se c'è già non lo duplica.
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ControlloScadenzeGiornaliero",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            controlloScadenzeRequest
        )
        // --- TRUCCO PER TESTARE SUBITO IL LAVORATORE ---
        val testImmediato = androidx.work.OneTimeWorkRequestBuilder<ScadenzaWorker>().build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "TestImmediato",
            androidx.work.ExistingWorkPolicy.REPLACE,
            testImmediato
        )
    }
}
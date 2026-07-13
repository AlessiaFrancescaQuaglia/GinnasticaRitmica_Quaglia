package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. HOST ACTIVITY: Carica il layout principale che fa da contenitore per i Fragment (NavGraph).
        setContentView(R.layout.activity_main)

        //
        // RUNTIME PERMISSION: Da Android 13 (Tiramisu) è obbligatorio chiedere il permesso esplicito per le notifiche.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

            // NOTIFICATION CHANNEL: Obbligatorio da Android 8+. Categorizza le notifiche permettendo all'utente di gestirle.
            val channel = android.app.NotificationChannel(
                "CANALE_SCADENZE",
                "Avvisi Scadenze Certificati",
                android.app.NotificationManager.IMPORTANCE_HIGH // Consente alla notifica di suonare e apparire a comparsa
            )
            channel.description = "Notifiche per i certificati medici scaduti delle atlete"

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // 4. PERIODIC WORK: Definisce un lavoro asincrono (il controllo scadenze) che Android eseguirà ogni 24 ore.
        val controlloScadenzeRequest = androidx.work.PeriodicWorkRequestBuilder<ScadenzaWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()

        // 5. UNIQUE WORK POLICY: "KEEP" garantisce che se riapro l'app, non verranno creati cloni dello stesso timer.
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ControlloScadenzeGiornaliero",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            controlloScadenzeRequest
        )

        //test immediato
        //  ONE-TIME WORK: Forza l'esecuzione immediata per poter testare il codice senza aspettare 24 ore.
        val testImmediato = androidx.work.OneTimeWorkRequestBuilder<ScadenzaWorker>().build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "TestImmediato",
            androidx.work.ExistingWorkPolicy.REPLACE,
            testImmediato
        )
    }
}
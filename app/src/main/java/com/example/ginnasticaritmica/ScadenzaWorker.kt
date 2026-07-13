package com.example.ginnasticaritmica

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScadenzaWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    // Questo è il metodo che il WorkManager esegue in background
    override fun doWork(): Result {
        val uidAllenatrice = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        try {
            // Uso Tasks.await()
            // Visto che siamo in Background, possiamo dire all'app di "fermarsi e aspettare"
            // la risposta di Firebase senza far bloccare lo schermo all'utente
            val snapshot = Tasks.await(
                db.collection("Atlete").whereEqualTo("idAllenatrice", uidAllenatrice).get()
            )

            var certificatiScaduti = 0
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dataOggi = Date()

            for (documento in snapshot.documents) {
                val scadenza = documento.getString("scadenzaCertificato") ?: ""
                if (scadenza.isNotEmpty() && scadenza != "Nessuna scadenza inserita") {
                    val dataScad = sdf.parse(scadenza)
                    if (dataScad != null) {
                        val diffGiorni = TimeUnit.DAYS.convert(dataScad.time - dataOggi.time, TimeUnit.MILLISECONDS)
                        if (diffGiorni < 0) {
                            certificatiScaduti++
                        }
                    }
                } else {
                    certificatiScaduti++ // Se non ha il certificato, lo contiamo come scaduto/mancante
                }
            }

            // Se troviamo problemi, facciamo partire la notifica
            if (certificatiScaduti > 0) {
                mostraNotifica(certificatiScaduti)
            }

            return Result.success()

        } catch (_: Exception) {
            return Result.failure()
        }
    }

    private fun mostraNotifica(numeroScaduti: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Costruiamo la notifica agganciandola al "CANALE_SCADENZE" creato prima
        val notifica = NotificationCompat.Builder(applicationContext, "CANALE_SCADENZE")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Icona di pericolo standard
            .setContentTitle("Attenzione! Certificati Scaduti")
            .setContentText("Hai $numeroScaduti atlete con il certificato medico scaduto o mancante.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // 1 è l'ID univoco della notifica
        notificationManager.notify(1, notifica)
    }
}
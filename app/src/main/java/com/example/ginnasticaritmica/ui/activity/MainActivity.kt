package com.example.ginnasticaritmica.ui.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.ginnasticaritmica.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 1. GESTIONE MANUALE BARRA INFERIORE (Risolve il problema della freccia indietro)
        bottomNavigationView.setOnItemSelectedListener { item ->
            // Evita di ricaricare la stessa pagina se ci clicchi sopra due volte
            if (item.itemId == navController.currentDestination?.id) {
                return@setOnItemSelectedListener true
            }

            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                // Questo comando dice al sistema che la Dashboard è la nostra pagina base, non l'HomeInfo!
                .setPopUpTo(R.id.coachDashboardFragment, inclusive = false, saveState = true)
                .build()

            navController.navigate(item.itemId, null, navOptions)
            true
        }

        // 2. NASCONDI BARRA E SINCRONIZZA ICONE
        navController.addOnDestinationChangedListener { _, destination, _ ->

            // Nasconde la barra dove darebbe fastidio (Home, Login, Fotocamera)
            if (destination.id == R.id.loginFragment ||
                destination.id == R.id.homeInfoFragment ||
                destination.id == R.id.analisiPosaFragment) {
                bottomNavigationView.visibility = View.GONE
            } else {
                bottomNavigationView.visibility = View.VISIBLE
            }

            // Mantiene illuminata l'icona corretta anche se si usa la freccia indietro
            val menu = bottomNavigationView.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.itemId == destination.id) {
                    item.isChecked = true
                    break
                }
            }
        }

        // Configurazione Notifiche
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

            val channel = NotificationChannel(
                "CANALE_SCADENZE",
                "Avvisi Scadenze Certificati",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche per i certificati medici scaduti delle atlete"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
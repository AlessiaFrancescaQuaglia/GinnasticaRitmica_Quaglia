package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

//Questa è l Activity che contiene il fragment CoachDashboard.
class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //estende lo sfondo dietro la barra della batteria e di navigazione.
        enableEdgeToEdge()

        // Collega il file grafico XML
        setContentView(R.layout.activity_dashboard)

        //Calcola l'ingombro per le finestre della fotocamera e delle barre di sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            //applica un padding per evitare che i testi vengano coperti.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Trova il bottone "Accedi" collegandolo all'ID che abbiamo messo nell'XML
        val btnLogin = findViewById<android.widget.Button>(R.id.btn_login)

        // Aggiungi l'azione da eseguire quando viene cliccato
        btnLogin.setOnClickListener {
            // Creiamo l'Intent per passare alla Dashboard
            val intent = android.content.Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }
    }
}
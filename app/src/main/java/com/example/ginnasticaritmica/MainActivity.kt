package com.example.ginnasticaritmica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Questo comando carica la "cornice" vuota, e il NavGraph farà il resto
        setContentView(R.layout.activity_main)
    }
}
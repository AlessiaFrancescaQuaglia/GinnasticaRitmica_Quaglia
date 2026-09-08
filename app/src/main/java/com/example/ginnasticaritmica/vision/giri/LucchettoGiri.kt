package com.example.ginnasticaritmica.vision.giri

object LucchettoGiri {
    var giroAttivo: String? = null

    // Controlla se questa classe ha il permesso di valutare
    fun possoValutare(idGiro: String): Boolean {
        return giroAttivo == null || giroAttivo == idGiro
    }

    // Chiude il lucchetto
    fun blocca(idGiro: String) {
        giroAttivo = idGiro
    }

    // Sblocca SOLO se è lo stesso giro che lo aveva chiuso
    fun sblocca(idGiro: String) {
        if (giroAttivo == idGiro) {
            giroAttivo = null
        }
    }
}
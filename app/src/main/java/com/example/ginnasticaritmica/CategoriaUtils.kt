package com.example.ginnasticaritmica

import java.util.Calendar

// 1. SINGLETON OBJECT: Creiamo un oggetto unico (Utils) per centralizzare la logica di business.
// Applicazione perfetta del principio SRP (Single Responsibility Principle) e DRY (Don't Repeat Yourself).
object CategoriaUtils {

    // Calcolo preciso dell'età considerando anno, mese e giorno (usato dal DatePickerDialog)
    fun calcolaCategoria(annoNascita: Int, meseNascita: Int, giornoNascita: Int): String {
        val cal = Calendar.getInstance()
        val annoCorrente = cal.get(Calendar.YEAR)
        var eta = annoCorrente - annoNascita

        // Se l'atleta non ha ancora compiuto gli anni nel mese/giorno corrente, togliamo 1 all'età
        if (cal.get(Calendar.MONTH) < meseNascita ||
            (cal.get(Calendar.MONTH) == meseNascita && cal.get(Calendar.DAY_OF_MONTH) < giornoNascita)) {
            eta--
        }

        return determinaNomeCategoria(eta)
    }

    // Calcolo a partire da una stringa ISO "YYYY-MM-DD" (usato dal ViewModel)
    fun calcolaCategoriaDaStringa(dataIso: String): String {
        return try {
            val parts = dataIso.split("-")
            val anno = parts[0].toInt()
            // I mesi in Kotlin Calendar partono da 0 (Gennaio = 0), quindi sottraggo 1 dalla stringa
            val mese = parts[1].toInt() - 1
            val giorno = parts[2].toInt()

            calcolaCategoria(anno, mese, giorno)
        } catch (e: Exception) {
            "Categoria Sconosciuta"
        }
    }

    // Mappatura centralizzata Età -> Categoria
    private fun determinaNomeCategoria(eta: Int): String {
        return when {
            eta < 8 -> "Pre-agonismo (< 8 anni)"
            eta in 8..9 -> "Allieve 1"
            eta == 10 -> "Allieve 2"
            eta == 11 -> "Allieve 3"
            eta == 12 -> "Allieve 4"
            eta == 13 -> "Allieve 5 – Prejunior"
            eta == 14 -> "Junior 1"
            eta == 15 -> "Junior 2"
            eta == 16 -> "Senior 1"
            eta in 17..18 -> "Senior 2"
            else -> "Senior 3"
        }
    }
}
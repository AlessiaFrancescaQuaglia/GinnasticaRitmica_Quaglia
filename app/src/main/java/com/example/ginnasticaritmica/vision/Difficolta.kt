package com.example.ginnasticaritmica.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

// La scatola con il risultato finale
data class RisultatoDifficolta(
    val isValida: Boolean,
    val valoreFinale: Double,
    val penalita: Double,
    val inReleve: Boolean,
    val messaggioErrore: String = ""
)

// Il "Contratto": ogni nuovo equilibrio dovrà avere un nome e una funzione valuta()
interface Difficolta {
    val nome: String
    fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta?
}
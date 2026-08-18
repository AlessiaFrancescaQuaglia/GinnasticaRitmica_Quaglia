package com.example.ginnasticaritmica.vision

import com.example.ginnasticaritmica.vision.giri.GiroGambaAvanti
import com.example.ginnasticaritmica.vision.giri.GiroPasse
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object AnalizzatoreGiri {

    private val listaGiri: List<Difficolta> = listOf(
        GiroGambaAvanti(),
        GiroPasse() // Il nostro primo giro

    )

    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        for (giro in listaGiri) {
            val risultato = giro.valuta(landmarks)
            if (risultato != null) {
                return Pair(giro.nome, risultato)
            }
        }
        return null
    }
}
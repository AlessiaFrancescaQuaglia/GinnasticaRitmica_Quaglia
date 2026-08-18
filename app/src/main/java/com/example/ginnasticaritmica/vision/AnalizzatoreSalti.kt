package com.example.ginnasticaritmica.vision

import com.example.ginnasticaritmica.vision.salti.SaltoBiche
import com.example.ginnasticaritmica.vision.salti.SaltoBicheCambio
import com.example.ginnasticaritmica.vision.salti.SaltoBoucle
import com.example.ginnasticaritmica.vision.salti.SaltoButterfly
import com.example.ginnasticaritmica.vision.salti.SaltoCabriole
import com.example.ginnasticaritmica.vision.salti.SaltoCarpiato
import com.example.ginnasticaritmica.vision.salti.SaltoCosacco
import com.example.ginnasticaritmica.vision.salti.SaltoEnjambee
import com.example.ginnasticaritmica.vision.salti.SaltoEnjambeeCambio
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object AnalizzatoreSalti {

    private val listaSalti: List<Difficolta> = listOf(
        SaltoEnjambeeCambio(),
        SaltoBicheCambio(),
        SaltoEnjambee(),
        SaltoButterfly(),
        SaltoBiche(),
        SaltoCabriole(),
        SaltoCarpiato(),
        SaltoBoucle(),
        SaltoCosacco()
    )

    // Brevissimo cooldown solo per evitare sfarfallii tra un frame e l'altro (circa 300ms)
    private var ultimoSaltoRiconosciutoTime: Long = 0L
    private const val COOLDOWN_RAPIDO_MS = 300L

    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        val currentTime = System.currentTimeMillis()

        // Se siamo in un piccolissimo blocco post-riconoscimento, evitiamo doppie scritture istantanee
        if (currentTime - ultimoSaltoRiconosciutoTime < COOLDOWN_RAPIDO_MS) {
            // Se c'è già un salto attivo nella classe, lasciamo che continui a mostrarlo
        }

        // Analisi immediata su questo singolo fotogramma
        for (salto in listaSalti) {
            val risultato = salto.valuta(landmarks)
            if (risultato != null && risultato.isValida) {
                ultimoSaltoRiconosciutoTime = currentTime
                return Pair(salto.nome, risultato)
            }
        }

        return null
    }
}
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

    // Inseriremo qui tutti i salti, in ordine dal più complesso (Carpiato) al più semplice (Cosacco)
    private val listaSalti: List<Difficolta> = listOf(
        SaltoEnjambeeCambio(), // Il sensore temporale va sempre per primo!
        SaltoBicheCambio(),
        SaltoEnjambee(),
        SaltoBiche(),
        SaltoButterfly(),
        SaltoCabriole(),       // Posizione perfetta (se hai già creato il file)
        SaltoCarpiato(),       // Trovato e lasciato uno solo
        SaltoBoucle(),
        SaltoCosacco()
    )

    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        for (salto in listaSalti) {
            val risultato = salto.valuta(landmarks)
            if (risultato != null) {
                return Pair(salto.nome, risultato)
            }
        }
        return null
    }
}
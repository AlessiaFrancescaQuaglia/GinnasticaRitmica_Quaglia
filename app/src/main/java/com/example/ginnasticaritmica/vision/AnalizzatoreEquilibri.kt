package com.example.ginnasticaritmica.vision


import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioDegage
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioPasse
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioGrandEcart
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioArabesque
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioAttitude
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioBoucle
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioCosacco
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioFouette
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioPenche
import com.example.ginnasticaritmica.vision.equilibri.EquilibrioTilt

object AnalizzatoreEquilibri {

    // ARCHIVIO CENTRALE DELLE DIFFICOLTÀ
    // Vuoi aggiungere l'Arabesque? Ti basterà aggiungere una riga qui sotto!
    private val archivioDifficolta = listOf(

        EquilibrioFouette(),      // 1. PRIMO ASSOLUTO: La macchina a stati dinamica
        EquilibrioBoucle(),       // 2. Pose estreme
        EquilibrioPenche(),
        EquilibrioGrandEcart(),
        EquilibrioTilt(),
        EquilibrioArabesque(),    // 3. Pose intermedie
        EquilibrioDegage(),
        EquilibrioPasse(),
        EquilibrioAttitude(),
        EquilibrioCosacco()
    )

    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        for (difficolta in archivioDifficolta) {
            val risultato = difficolta.valuta(landmarks)
            // Appena un file riconosce la posa, fermiamo la ricerca e inviamo il risultato al Pittore
            if (risultato != null) return Pair(difficolta.nome, risultato)
        }
        return null
    }
}
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

/**
 * Orchestratore logico implementato come Singleton (object).
 * Applica il Design Pattern "Strategy": astrae la logica di valutazione specifica
 * delegandola alle singole classi polimorfiche delle difficoltà.
 */
object AnalizzatoreEquilibri {

    // L'ordine di questa lista NON è casuale, ma è il cuore della prevenzione
    // del "Fall-Through Algoritmico".
    // È strutturata come una coda di priorità (Priority Queue) gerarchica:
    // le pose più complesse e con più vincoli (es. Penché o Boucle)
    // DEVONO essere valutate prima di quelle semplici (es. Passé o Attitude).
    // Se l'ordine fosse inverso, una posa complessa verrebbe
    // intercettata e "rubata" dal validatore di una posa più semplice
    // che ne condivide la geometria di base (es. un Boucle verrebbe scambiato per un Attitude).
    private val archivioDifficolta = listOf(

        // 1. Pose ad altissimo vincolo geometrico e spaziale
        EquilibrioPenche(),
        EquilibrioFouette(),
        EquilibrioBoucle(),

        // 2. Pose estreme (Spaccate, sbilanciamenti del baricentro)
        EquilibrioGrandEcart(),
        EquilibrioTilt(),
        EquilibrioArabesque(),

        // 3. Pose intermedie e di base (Minor numero di vincoli vettoriali)
        EquilibrioDegage(),
        EquilibrioPasse(),
        EquilibrioAttitude(),
        EquilibrioCosacco()
    )

    /**
     * Ciclo di inferenza logica applicato al singolo frame video.
     * @param landmarks Il tensore 3D generato dalla rete neurale MediaPipe.
     */
    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        // Il sistema interroga ogni analizzatore rispettando la gerarchia di complessità.
        for (difficolta in archivioDifficolta) {

            // Ogni classe applica i propri controlli matematico-vettoriali su angoli e distanze 3D
            val risultato = difficolta.valuta(landmarks)

            // Ottimizzazione computazionale per mantenere i 30 FPS stabili.
            // Appena una classe certifica matematicamente la validità della posa, l'iterazione si interrompe.
            // Il risultato viene inoltrato al thread grafico (OverlayView).
            if (risultato != null) return Pair(difficolta.nome, risultato)
        }

        // Nessun elemento rilevato nel fotogramma corrente
        return null
    }
}
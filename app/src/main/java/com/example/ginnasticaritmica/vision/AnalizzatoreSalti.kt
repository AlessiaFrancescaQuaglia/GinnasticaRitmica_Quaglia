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

/**
 * Orchestratore logico per la classificazione degli elementi balistici (Salti).
 * A differenza degli equilibri, l'analisi dei salti impone un cambio di paradigma:
 * il sistema agisce come un "Peak Scanner"[cite: 2]. L'obiettivo non è valutare una posa statica
 * prolungata, bensì estrapolare e validare le metriche articolari nel singolo
 * e rapido istante in cui l'atleta raggiunge l'apice della parabola aerea[cite: 2].
 */
object AnalizzatoreSalti {

    // Coda di priorità polimorfica per prevenire le collisioni logiche.
    // I salti in rotazione o "con Cambio" (che richiedono l'accesso alla Macchina a Stati
    // per tracciare l'inversione degli arti durante il decollo) sono posizionati piu in alto.
    // Questo previene che la rete valuti prematuramente l'elemento come un salto
    // semplice prima che la dinamica completa del salto sia effettivamente conclusa in volo.
    private val listaSalti: List<Difficolta> = listOf(
        SaltoEnjambee(),
        SaltoEnjambeeCambio(),
        SaltoBicheCambio(),

        SaltoButterfly(),
        SaltoBiche(),
        SaltoCabriole(),
        SaltoCarpiato(),
        SaltoBoucle(),
        SaltoCosacco()
    )

    // Implementazione di un meccanismo di Debouncing (Isteresi temporale).
    // Il tracking visivo ad alti framerate (30+ FPS) sulle traiettorie di salto
    // può generare fluttuazioni millimetriche delle coordinate attorno al picco del salto.
    private var ultimoSaltoRiconosciutoTime: Long = 0L
    private const val COOLDOWN_RAPIDO_MS = 300L

    /**
     * Funzione di inferenza eseguita su ogni singolo frame processato dal thread della fotocamera.
     */
    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        val currentTime = System.currentTimeMillis()

        // Se l'applicativo ha appena certificato l'apice di un salto, inibisce temporaneamente (per 300ms)
        // le valutazioni successive per lo stesso evento balistico.
        // Questa gestione dello stato previene flickering sull'HUD
        // e impedisce l'invio multiplo di log di riconoscimento per un singolo salto.
        if (currentTime - ultimoSaltoRiconosciutoTime < COOLDOWN_RAPIDO_MS) {
            // Durante questo brevissimo periodo, il sistema di rendering
            // mantiene in persistenza visiva l'ultimo elemento certificato.
        }

        // Analisi istantanea del tensore spaziale vettoriale (Coordinate X, Y, Z).
        // Le classi interrogano i propri filtri (ad es. il "Filtro Prospettico X-Axis"
        // per mitigare la distorsione ottica e distinguere salti simili ma rivolti verso direzioni diverse).
        for (salto in listaSalti) {
            val risultato = salto.valuta(landmarks)

            // Non appena l'algoritmo accerta matematicamente il superamento dei vincoli angolari di oversplit
            // o estensione,riavvia il timer di debouncing e inoltra la validazione all'interfaccia utente.
            if (risultato != null && risultato.isValida) {
                ultimoSaltoRiconosciutoTime = currentTime
                return Pair(salto.nome, risultato)
            }
        }

        // Nessuna geometria balistica rilevata nel fotogramma analizzato.
        return null
    }
}
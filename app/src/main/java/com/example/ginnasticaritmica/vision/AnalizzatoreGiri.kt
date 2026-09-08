package com.example.ginnasticaritmica.vision

import com.example.ginnasticaritmica.vision.giri.GiroArabesque
import com.example.ginnasticaritmica.vision.giri.GiroBustoOrizzontale
import com.example.ginnasticaritmica.vision.giri.GiroDegage
import com.example.ginnasticaritmica.vision.giri.GiroGrandEcart
import com.example.ginnasticaritmica.vision.giri.GiroPasse
import com.example.ginnasticaritmica.vision.giri.GiroPenche
import com.example.ginnasticaritmica.vision.giri.GiroPresa
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Orchestratore logico dedicato esclusivamente alla classificazione delle Rotazioni (Pivot).
 * A differenza degli Equilibri (che sono statici), il tracciamento dei Giri è la sfida
 * computazionale più complessa per la Computer Vision poiché l'atleta ruota di 360° su se stessa,
 * causando continue occlusioni anatomiche e variazioni estreme della metrica di profondità (Asse Z).
 */
object AnalizzatoreGiri {

    // Anche in questo modulo è implementata una Priority Queue per prevenire i Fall-Through logici.
    // Nelle rotazioni questo aspetto è critico: durante la fase di preparazione (plié),
    // l'atleta passa per frazioni di secondo attraverso pose intermedie che potrebbero attivare il validatore sbagliato.
    private val listaGiri: List<Difficolta> = listOf(

        // 1. Massima priorità: Giri con forti alterazioni del baricentro (fuori asse).
        // Devono essere analizzati per primi per evitare che il sensore interpreti
        // l'abbassamento del busto come un "cedimento" o un errore di postura di un giro eretto.
        GiroBustoOrizzontale(),
        GiroPenche(),

        // 2. Giri in flessibilità ed estensione vettoriale elevata.
        GiroDegage(),
        GiroArabesque(),
        GiroPresa(),
        GiroGrandEcart(),

        // 3. Giri base con asse spinale verticale e minor raggio di ingombro spaziale.
        // Posizionato intenzionalmente per ultimo per evitare che "rubi" l'inferenza ai giri
        // più complessi nei primissimi millisecondi di movimento .
        GiroPasse()
    )

    /**
     * Motore di inferenza specifico per i fotogrammi soggetti a inerzia rotazionale.
     * @param landmarks Tensore spaziale 3D in ingresso.
     */
    fun analizza(landmarks: List<NormalizedLandmark>): Pair<String, RisultatoDifficolta>? {
        for (giro in listaGiri) {

            // Il metodo valuta() di queste classi è profondamente diverso da quello degli equilibri.
            // Non esegue solo calcoli trigonometrici statici, ma interroga la Macchina a Stati temporizzata
            // e verifica lo stato del movimento in tempo reale per evitare gare con il modulo degli equilibri
            val risultato = giro.valuta(landmarks)

            // Se la Macchina a Stati del giro specifico certifica
            // l'evoluzione corretta del movimento, blocchiamo l'iterazione per
            // risparmiare cicli di CPU e mantenere il framerate fluido (30 FPS).
            if (risultato != null) {
                return Pair(giro.nome, risultato)
            }
        }

        return null
    }
}
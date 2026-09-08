package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri // Assicurati di importarlo in alto!

class EquilibrioFouette : Difficolta {
    override var nome = "FOUETTÉ"

    // --- MEMORIA INTERNA DELLA MACCHINA A STATI ---
    // Queste variabili sopravvivono tra un fotogramma e l'altro per tracciare l'evoluzione
    private var formeCompletate = 0
    private var countFormeOrizzontali = 0
    private var countFormeAlte = 0
    private var hasTurned = false
    private var lastFacingRight: Boolean? = null
    private var lastShape: String = "NONE"

    // Funzione per resettare la sequenza (es. se scende dal relevé)
    private fun resetSequence() {
        formeCompletate = 0
        countFormeOrizzontali = 0
        countFormeAlte = 0
        hasTurned = false
        lastFacingRight = null
        lastShape = "NONE"
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val isAppoggioLeft = landmarks[27].y() > landmarks[28].y()
        val spalla = if (isAppoggioLeft) landmarks[12] else landmarks[11]
        val anca = if (isAppoggioLeft) landmarks[24] else landmarks[23]
        val ginocchio = if (isAppoggioLeft) landmarks[26] else landmarks[25]

        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]

        // 1. CONTROLLO RELEVÉ (Condizione vitale per il Fouetté)
        // Se la ginnasta scende dal relevé durante la sequenza, il Fouetté si interrompe.
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            resetSequence()
            return null
        }

        // 2. SENSORE DI ROTAZIONE (Giro)
        val isFacingRight = supportToe.x() > supportHeel.x()
        if (lastFacingRight != null && lastFacingRight != isFacingRight) {
            hasTurned = true // Ha cambiato fronte di 180° o 360°!
        }
        lastFacingRight = isFacingRight

        // 3. IDENTIFICAZIONE DELLA FORMA ATTUALE
        val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)

        // Misuriamo se la gamba è piegata (Passé) o tesa (Fouetté aperto)
        val ancaGambaLibera = if (isAppoggioLeft) landmarks[23] else landmarks[24]
        val ginocchioLibero = if (isAppoggioLeft) landmarks[25] else landmarks[26]
        val cavigliaLibera = if (isAppoggioLeft) landmarks[27] else landmarks[28]
        val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(ancaGambaLibera, ginocchioLibero, cavigliaLibera)

        var currentShape = "NONE"

        if (tensioneGinocchio < 120.0) {
            currentShape = "PASSE" // Forma di transizione classica
        } else if (altezzaGamba in 90.0..120.0) {
            currentShape = "ORIZZONTALE" // Gamba tesa a 90 gradi circa
        } else if (altezzaGamba < 90.0) {
            currentShape = "ALTA" // Gamba tesa sopra l'orizzontale (es. spaccata)
        }

        // 4. TRANSIZIONE DI STATO (Ha cambiato forma?)
        if (currentShape != "NONE" && currentShape != lastShape) {
            formeCompletate++

            // Registriamo l'altezza della forma appena raggiunta
            if (currentShape == "ORIZZONTALE") countFormeOrizzontali++
            if (currentShape == "ALTA") countFormeAlte++

            lastShape = currentShape
        }

        // 5. VALUTAZIONE FINALE (Check dei requisiti del Codice dei Punteggi)
        // Il Fouetté è valido solo se ha fatto almeno 3 forme E ha girato
        if (formeCompletate >= 3 && hasTurned) {

            var valoreFinale = 0.0

            // Item 2.1805: Minimo 2 forme SOPRA l'orizzontale
            if (countFormeAlte >= 2) {
                nome = "EQUILIBRIO FOUETTÉ (Gamba alta)"
                valoreFinale = 0.50
            }
            // Item 2.1803: Minimo 2 forme ALL'orizzontale (o 1 orizzontale e 1 alta)
            else if ((countFormeOrizzontali + countFormeAlte) >= 2) {
                nome = "EQUILIBRIO FOUETTÉ (Gamba orizzontale)"
                valoreFinale = 0.30
            }

            // Se ha completato la sequenza ma non ha raggiunto le altezze necessarie,
            // la sequenza non riceve il valore speciale del Fouetté.
            if (valoreFinale > 0.0) {
                // Una volta validato e "stampato" a schermo, resettiamo per il prossimo elemento
                val risultato = RisultatoDifficolta(
                    isValida = true,
                    valoreFinale = valoreFinale,
                    penalita = 0.0,
                    inReleve = true,
                    messaggioErrore = ""
                )
                resetSequence()
                return risultato
            }
        }

        // Finché non completa le 3 forme e il giro, restituisce null per continuare ad analizzare i fotogrammi successivi
        return null
    }
}
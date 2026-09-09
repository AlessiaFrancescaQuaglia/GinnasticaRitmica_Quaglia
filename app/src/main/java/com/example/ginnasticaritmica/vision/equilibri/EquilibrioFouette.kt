package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Modulo dedicato all'Equilibrio "Fouetté".
 * A differenza degli altri equilibri (che sono analizzati come pose statiche nel singolo fotogramma),
 * il Fouetté è una sequenza dinamica. L'atleta deve rimanere in equilibrio mentre ruota
 * e cambia ripetutamente la forma della gamba sollevata.
 * Questo richiede l'implementazione di una Macchina a Stati Finiti (FSM) provvista di memoria interna per tracciare il flusso temporale.
 */
class EquilibrioFouette : Difficolta {
    override var nome = "FOUETTÉ"


    // MEMORIA INTERNA DELLA MACCHINA A STATI
    // Variabili di istanza che persistono tra un frame e l'altro (a 30 FPS).
    // Memorizzano la "storia" dell'elemento biomeccanico durante la sua esecuzione.
    private var formeCompletate = 0
    private var countFormeOrizzontali = 0
    private var countFormeAlte = 0
    private var hasTurned = false
    private var lastFacingRight: Boolean? = null
    private var lastShape: String = "NONE"

    /**
     * Svuota il buffer di memoria se l'atleta commette un errore fatale che
     * interrompe tecnicamente la catena dell'equilibrio.
     */
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


        // Il regolamento impone che l'intera sequenza avvenga ininterrottamente
        // sull'avampiede (Relevé). Se il tallone tocca terra anche per un solo fotogramma,
        // la Macchina a Stati subisce un reset immediato e il Fouetté è annullato.
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            resetSequence()
            return null
        }

        //SENSORE DI ROTAZIONE VETTORIALE
        // La telecamera 2D fatica a percepire la profondità (rotazione sull'asse Y).
        // Il sistema deduce la rotazione tracciando l'inversione del vettore
        // formato dal Tallone e dalla Punta del piede di appoggio rispetto all'asse X.
        val isFacingRight = supportToe.x() > supportHeel.x()
        if (lastFacingRight != null && lastFacingRight != isFacingRight) {
            hasTurned = true // Cambio di fronte spaziale rilevato (180° o 360°)
        }
        lastFacingRight = isFacingRight

        //  DISCRETIZZAZIONE DEGLI STATI
        // L'angolo continuo estratto dalla cinematica viene mappato in
        // categorie discrete ("Stati") intellegibili per il regolamento F.I.G.
        val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)

        val ancaGambaLibera = if (isAppoggioLeft) landmarks[23] else landmarks[24]
        val ginocchioLibero = if (isAppoggioLeft) landmarks[25] else landmarks[26]
        val cavigliaLibera = if (isAppoggioLeft) landmarks[27] else landmarks[28]
        val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(ancaGambaLibera, ginocchioLibero, cavigliaLibera)

        var currentShape = "NONE"

        if (tensioneGinocchio < 120.0) {
            currentShape = "PASSE" // Stato 1: Gamba flessa di transizione
        } else if (altezzaGamba in 90.0..120.0) {
            currentShape = "ORIZZONTALE" // Stato 2: Estensione standard
        } else if (altezzaGamba < 90.0) {
            currentShape = "ALTA" // Stato 3: Massima flessibilità (Spaccata)
        }

        // TRANSIZIONE DI STATO
        // La memoria si aggiorna solo ed esclusivamente nel momento in cui avviene
        // un cambio di stato (es. da PASSE a ORIZZONTALE), filtrando il rumore
        // dei fotogrammi intermedi.
        if (currentShape != "NONE" && currentShape != lastShape) {
            formeCompletate++

            // Aggiornamento dei contatori categoriali per il punteggio finale
            if (currentShape == "ORIZZONTALE") countFormeOrizzontali++
            if (currentShape == "ALTA") countFormeAlte++

            lastShape = currentShape
        }

        // VALIDAZIONE FINALE DELLA SEQUENZA LOGICA
        // La valutazione terminale scatta solo quando la FSM ha collezionato
        // i requisiti minimi di complessità: 3 transizioni di stato e 1 rotazione spaziale.
        if (formeCompletate >= 3 && hasTurned) {

            var valoreFinale = 0.0

            // Calcolo del Base Value proporzionale alla qualità degli stati collezionati
            if (countFormeAlte >= 2) {
                nome = "EQUILIBRIO FOUETTÉ (Gamba alta)"
                valoreFinale = 0.50
            }
            else if ((countFormeOrizzontali + countFormeAlte) >= 2) {
                nome = "EQUILIBRIO FOUETTÉ (Gamba orizzontale)"
                valoreFinale = 0.30
            }

            if (valoreFinale > 0.0) {
                // Costruzione dell' output e svuotamento buffer per intercettare
                // eventuali Fouetté successivi (es. se la ginnasta continua a girare).
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

        // Se l'elemento è ancora in progress, si restituisce null
        // per permettere alla fotocamera di continuare a processare in modo fluido i frame successivi.
        return null
    }
}
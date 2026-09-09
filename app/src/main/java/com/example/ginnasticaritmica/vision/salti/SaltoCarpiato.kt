package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Modulo per il "Salto Carpiato" (o Salto Divaricato).
 * In questo elemento l'atleta esegue un salto elevando entrambe le gambe tese in avanti/fuori
 * a tempo e flettendo il tronco verso di esse.
 * La parte principale consiste nel disambiguare l'apertura laterale (di profilo) da un'apertura
 * sagittale (frontale) utilizzando proiezioni planari 2D, per aggirare i limiti del sensore di profondità.
 */
class SaltoCarpiato : Difficolta {
    override var nome = "SALTO CARPIATO"


    // MEMORIA DI STATO ROTAZIONALE (Time-Series)
    // Mantiene uno storico temporale per rilevare un'eventuale rincorsa
    // in rotazione ("En Tournant") precedente alla fase di volo.
    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        // 1. SCUDO ANTI-GIRO (Gestione Concorrenza / Mutex)
        // Previene Race Conditions con l'analisi degli equilibri in rotazione.
        // Se un Pivot detiene il lock, l'algoritmo deduce l'assenza di fase aerea.
        if (com.example.ginnasticaritmica.vision.giri.LucchettoGiri.giroAttivo != null) {
            return null
        }

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val altezzaTronco = kotlin.math.abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)


        // FIREWALL ANTI-EQUILIBRIO (Sensore di Fase Aerea)
        // Calcola la lunghezza del femore come scalare per definire una soglia di ancoraggio a terra
        // indipendente dalle variazioni di altezza dovute all'inclinazione del tronco.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Se un piede è al di sotto della soglia del bacino + femore, con la gamba estesa (>135°),'elemento non è un salto.
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Tintercetta elementi in accosciata profonda
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null
        }


        // FIREWALL ANTI-ENJAMBÉE E AMBIGUITÀ PROSPETTICA
        // È il blocco logico fondamentale per la classificazione geometrica:
        // distingue un salto Carpiato (gambe aperte frontalmente) da un salto Enjambée
        // (spaccata sagittale, una gamba avanti e una dietro).
        val distSpalleX = kotlin.math.abs(landmarks[11].x() - landmarks[12].x())
        val distCaviglieX = kotlin.math.abs(leftAnkle.x() - rightAnkle.x())

        // Sensore di Profilo Assoluto:
        // Se la distanza sull'asse X tra le spalle è trascurabile (< 0.08f), la ginnasta
        // è inquadrata perfettamente di lato (occlusione spalla su spalla).
        // Da questa angolazione un Carpiato frontale apparirebbe come un'unica gamba.
        // Ne deduciamo che la figura visibile è sicuramente una spaccata sagittale (Enjambée).
        if (distSpalleX < 0.08f) {
            return null
        }

        // Sensore di Occlusione Frontale ):
        // Se la ginnasta è inquadrata di fronte (distSpalleX grande), ma le caviglie sull'asse X
        // non sono sufficientemente distanti l'una dall'altra (non superano la larghezza delle spalle),
        // significa che le gambe sono disposte sull'asse Z (una verso la telecamera, una lontana).
        // Si tratta quindi di un Enjambée frontale mascherato dalla prospettiva.
        if (distCaviglieX < distSpalleX * 1.2f) {
            return null
        }



        // REQUISITI CARPIATO
        // La tensione delle ginocchia è impostata a 120° (invece di 135°/160°)
        // per perdonare imperfezioni anatomiche accentuate dall'esplosività del salto.
        if (leftKneeAngle < 120.0 || rightKneeAngle < 120.0) return null

        // Le caviglie devono obbligatoriamente salire oltre la soglia minima del bacino.
        if (leftAnkle.y() > bacinoY + 0.20f || rightAnkle.y() > bacinoY + 0.20f) return null

        val centroBacino = NormalizedLandmark.create((leftHip.x() + rightHip.x()) / 2f, bacinoY, 0f)

        // Calcolo dell'angolo di divaricazione globale (estensione della flessibilità)
        val angoloDivaricata = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)
        if (angoloDivaricata < 120.0) return null

        // Tracciamento Rotazione Preparatoria
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()

        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }


        // SENSORE DI FLESSIONE (Identificazione Variante "Profondo")
        // Misura il livello di compressione del tronco verso le gambe.
        // Utilizza una metrica ibrida (Distanza Naso-Bacino OR Compressione Y Tronco)
        // per aggirare le occlusioni quando la testa si nasconde dietro le braccia.
        val nose = landmarks[0]
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isCarpiatoFlesso = distNasoBacino < 0.25f || altezzaTronco < 0.15f

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        // Albero Decisionale per l'attribuzione del Valore
        var valoreBase = 0.0

        if (isCarpiatoFlesso) {
            nome = if (isEnTournant) "SALTO CARPIATO TOURNANT + FLESSIONE" else "SALTO CARPIATO PROFONDO"
            valoreBase = if (isEnTournant) 0.50 else 0.40
        } else {
            nome = if (isEnTournant) "SALTO CARPIATO EN TOURNANT" else "SALTO CARPIATO"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }


        // PENALITÀ SULL'ELEVAZIONE
        // Discretizza l'altitudine Y media delle caviglie (variabile continua)
        // in scaglioni di penalità predefiniti dal Codice dei Punteggi.
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val avgAnkleY = (leftAnkle.y() + rightAnkle.y()) / 2f

        if (avgAnkleY <= bacinoY + 0.05f) {
            penalita += 0.0 // Forma perfetta: piedi all'altezza del bacino o superiori
        } else if (avgAnkleY <= bacinoY + 0.12f) {
            penalita += 0.10 // Lieve deficit di slancio
        } else if (avgAnkleY <= bacinoY + 0.20f) {
            penalita += 0.30 // Deficit grave
        } else {
            penalita += 0.50
            isValida = false // Drop tecnico: figura geometrica non riconosciuta
            msgErrore = "Elevazione gambe e divaricata insufficiente"
        }

        // Svuotamento buffer rotazionale in caso di validazione positiva
        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Modulo per il "Salto Cabriole".
 * Questo salto richiede che l'atleta slanci una gamba in aria
 * e la colpisca con l'altra gamba prima di atterrare.
 * le gambe si uniscono e si separano in una frazione di secondo.
 * L'algoritmo utilizza un sistema di "Peak Detection" per registrare
 * la massima elevazione (angolo minimo) raggiunta durante la collisione in volo.
 */
class SaltoCabriole : Difficolta {
    override var nome = "SALTO CABRIOLE"


    // MEMORIA DI STATO E PEAK DETECTION
    // Mantiene in memoria l'evento ("battuta") per 1 secondo,
    // permettendo all'applicazione di valutarlo anche durante la successiva fase di caduta.
    companion object {
        private var tempoUltimaBattuta: Long = 0L
        private var bestElevazione = 180.0 // Inizializzato al valore peggiore (gambe basse)
        private var nomeSalvato = "SALTO CABRIOLE"
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        // 1. SCUDO ANTI-GIRO (Gestione Concorrenza / Mutex)
        // Esclude valutazioni incrociate: se il Singleton dei giri è bloccato,
        // l'atleta non è in volo. L'inferenza abortisce.
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
        val bacinoX = (leftHip.x() + rightHip.x()) / 2f
        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Misurazione scalare (Femore) per calibrazione della profondità Y
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f


        // TASTO DI RESET (Filtro Gravitazionale / Atterraggio)
        // Identifica l'avvenuto atterraggio (almeno un piede riacquista portanza).
        // Funge da Garbage Collector: resetta i registri statici del Cabriole,
        // preparando la classe per il salto successivo.
        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            tempoUltimaBattuta = 0L
            bestElevazione = 180.0
            return null
        }


        // CALCOLO APERTURA IBRIDA (Fusione Sensoriale 2D/3D)
        // Calcola l'angolo di separazione tra le gambe. Nel Cabriole, questo angolo deve
        // tendere a 0 (gambe unite). La fusione 2D/3D previene falsi positivi
        // in cui la prospettiva fa apparire unite gambe che in realtà sono divaricate in profondità Z.
        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)

        val vLeftX = leftAnkle.x() - bacinoX
        val vLeftY = leftAnkle.y() - bacinoY
        val vRightX = rightAnkle.x() - bacinoX
        val vRightY = rightAnkle.y() - bacinoY
        val dot = (vLeftX * vRightX) + (vLeftY * vRightY)
        val magL = kotlin.math.sqrt((vLeftX * vLeftX + vLeftY * vLeftY).toDouble())
        val magR = kotlin.math.sqrt((vRightX * vRightX + vRightY * vRightY).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magL * magR)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        // Prendiamo l'angolo più fedele alla realtà, sfuggendo ai bug di prospettiva
        val aperturaReale = max(angoloSpaccata3D, angoloSpaccata2D)


        //FIREWALL ANTI-ENJAMBÉE
        // In un Cabriole le gambe sbattono insieme. Se le gambe iniziano ad aprirsi a forbice
        // (angolo > 50 gradi) o se la distanza è grande, l'elemento è cinematicamente divergente.
        // Autodistruzione immediata della memoria: il sistema cede l'elaborazione
        // ai moduli predisposti per le spaccate in volo (es. Salto Enjambée).
        if (aperturaReale > 50.0 || distanzaCaviglie > 0.25f) {
            tempoUltimaBattuta = 0L
            bestElevazione = 180.0
            return null
        }

        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val spallaCentro = NormalizedLandmark.create((spallaSx.x() + spallaDx.x()) / 2f, (spallaSx.y() + spallaDx.y()) / 2f, 0f)
        val centroCaviglie = NormalizedLandmark.create((leftAnkle.x() + rightAnkle.x()) / 2f, (leftAnkle.y() + rightAnkle.y()) / 2f, 0f)

        // L'elevazione si misura calcolando l'angolo formato dal tronco (Spalle-Bacino)
        // rispetto alle gambe unite (Bacino-Centro Caviglie). Più le gambe salgono, più l'angolo diminuisce.
        val elevazioneGambe = PoseAnalyzer.calculateAngle3D(spallaCentro, centroBacino, centroCaviglie)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2f

        val isLeftHigh = leftAnkle.y() < bacinoY + 0.15f
        val isRightHigh = rightAnkle.y() < bacinoY + 0.15f


        // Questo è il trigger point: scatta solo quando l'algoritmo rileva il contatto fisico (battuta).
        // Condizioni: ginocchia dritte (>135°), caviglie unite (<20cm di distanza),
        // gambe sollevate e angolo di divergenza piccolo (<40°).
        if (avgKneeAngle > 135.0 && distanzaCaviglie < 0.20f && isLeftHigh && isRightHigh && aperturaReale < 40.0) {

            tempoUltimaBattuta = System.currentTimeMillis()

            // aggiorna il record solo se l'angolo tronco-gambe diminuisce (gambe più alte)
            if (elevazioneGambe < bestElevazione) {
                bestElevazione = elevazioneGambe
            }

            // Mappatura Vettoriale per Direzione (Avanti vs Indietro/Lato)
            val nose = landmarks[0]
            val isFacingRight = nose.x() > bacinoX
            val isAvanti = if (isFacingRight) centroCaviglie.x() > bacinoX else centroCaviglie.x() < bacinoX
            nomeSalvato = if (isAvanti) "SALTO CABRIOLE AVANTI" else "SALTO CABRIOLE INDIETRO / LATO"
        }


        // FORZATURA DEL RISULTATO
        // Mantiene in vita la valutazione per 1000ms successivi alla "battuta",
        // restituendo il risultato migliore elaborato dalla Peak Detection e sopprimendo
        // il decadimento visivo tipico della fase di caduta balistica.
        val tempoTrascorso = System.currentTimeMillis() - tempoUltimaBattuta
        val isCabrioleAttivo = tempoTrascorso < 1000L && tempoUltimaBattuta != 0L

        if (isCabrioleAttivo) {
            nome = nomeSalvato
            var penalita = 0.0
            var msgErrore = ""

            // Discretizzazione delle penalità in base all'angolo di elevazione del blocco gambe
            if (bestElevazione <= 100.0) penalita += 0.0
            else if (bestElevazione <= 120.0) penalita += 0.10
            else penalita += 0.30

            // Controllo Qualità Esecutiva: le gambe in volo devono rimanere rigidamente tese
            if (avgKneeAngle < 135.0) {
                penalita += 0.30
                msgErrore = "Ginocchia piegate"
            }

            return RisultatoDifficolta(true, 0.10, penalita, false, msgErrore)
        }

        return null
    }
}
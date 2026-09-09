package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * Modulo per il "Salto Biche con Cambio".
 * Aumenta la complessità del salto base introducendo un vincolo temporale
 * chiamato "Cambio": l'atleta deve lanciare una gamba verso l'alto (Slancio) e scambiarla
 * con la gamba opposta prima di raggiungere l'apice (Apex) del volo.
 */
class SaltoBicheCambio : Difficolta {
    override var nome = "BICHE CON CAMBIO"

    // MEMORIA DI STATO IN VOLO E ROTAZIONALE
    // Buffer temporale per tracciare gli eventi cinematici precedenti alla fase di volo.
    companion object {
        // Registra il timestamp e l'identità della prima gamba lanciata in aria.
        private var tempoUltimoSlancio: Long = 0L
        private var gambaSlancioIsLeft: Boolean? = null

        // Registra l'eventuale rotazione preparatoria (Tournant).
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        //SCUDO ANTI-GIRO (Gestione Concorrenza):
        // Se un giro (Pivot) ha acquisito il lock globale, inibisce l'analisi.
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

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // FIREWALL ANTI-EQUILIBRIO (Sensore di Volo)
        // Usiamo la lunghezza del femore come unità di misura invariabile
        // per stabilire matematicamente se i piedi sono staccati da terra.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Una gamba è considerata piantata a terra se scende in profondità (più del femore)
        // ed è sufficientemente tesa per sostenere il peso del corpo (angolo > 135°).
        // Nel salto Biche la gamba anteriore scende ma è piegata a 90° (forma a cervo),
        // quindi il Firewall riconosce che non sta sorreggendo il peso e la lascia passare.
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Sicurezza extra: se una caviglia è molto in basso (vicino al fondo dello schermo)
        // e lontana dal bacino, deduciamo un appoggio  e spegniamo i salti a prescindere dall'angolo.
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null // Piede a terra confermato, l'esecuzione viene ceduta ai moduli di Equilibrio.
        }

        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

        // Tracciamento Inversione Spalle (Zero-Crossing asse X) per rilevare rotazioni preliminari
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        val isFacingRight = spallaDx.z() < spallaSx.z()
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]


        // RILEVAMENTO DELLO SLANCIO PREPARATORIO (Fase 1 del Cambio)
        // Monitora il differenziale Y tra le due caviglie.
        // Se una caviglia si eleva significativamente rispetto all'altra con la gamba tesa (>140°),
        // l'algoritmo registra l'istante temporale (Timestamp) e l'identità dell'arto (Left/Right).
        val yDiff = leftAnkle.y() - rightAnkle.y()
        if (abs(yDiff) > 0.15f) {
            val isLeftHigher = leftAnkle.y() < rightAnkle.y()
            val higherKneeAngle = if (isLeftHigher) leftKneeAngle else rightKneeAngle
            if (higherKneeAngle > 140.0) {
                tempoUltimoSlancio = System.currentTimeMillis()
                gambaSlancioIsLeft = isLeftHigher
            }
        }

        // Assegnazione Dinamica Arti (Avanti/Dietro) basata sul vettore di sguardo
        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) rightToe else leftToe

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)

        // Se la gamba anteriore non è piegata a formare il caratteristico "Cervo", non è un Biche.
        if (frontKneeAngle > 115.0 || frontKneeAngle < 35.0) return null

        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val centroBacinoY = (leftHip.y() + rightHip.y()) / 2f
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)


        // CALCOLO SPACCATA IBRIDO - Ginocchio Avanti vs Caviglia Dietro

        // Poiché la gamba anteriore è piegata, il vertice del vettore non è la caviglia ma il Ginocchio.
        // Fonde il Prodotto Scalare 3D con l'ArcoCoseno 2D per mitigare la parallasse.
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontKnee, centroBacino, backAnkle)

        val v1x = frontKnee.x() - centroBacinoX
        val v1y = frontKnee.y() - centroBacinoY
        val v2x = backAnkle.x() - centroBacinoX
        val v2y = backAnkle.y() - centroBacinoY
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
        val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dotProduct / (mag1 * mag2)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val angoloSpaccata = kotlin.math.max(angoloSpaccata3D, angoloSpaccata2D)


        if (angoloSpaccata < 130.0) return null


        // VALIDAZIONE DEL CAMBIO (Verifica Time-Series)
        // Accerta che l'attuale gamba frontale sia DIVERSA da quella slanciata in memoria,
        // e che lo scambio sia avvenuto in una finestra temporale valida (< 1.5s).
        val tempoTrascorsoSlancio = System.currentTimeMillis() - tempoUltimoSlancio

        if (tempoTrascorsoSlancio < 1500L && gambaSlancioIsLeft != null && isLeftFront != gambaSlancioIsLeft) {

            val tempoTrascorsoRotazione = System.currentTimeMillis() - tempoUltimaRotazione
            val isEnTournant = tempoTrascorsoRotazione < 1500L // Valuta il moltiplicatore "In Rotazione"

            val nose = landmarks[0]
            val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
            val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)


            // SENSORE DI FLESSIONE ANTI-GLITCH
            // Risoluzione per le occlusioni.
            // Quando la ginnasta si inarca all'indietro in volo, il suo stesso corpo nasconde
            // la gamba posteriore alla telecamera, causando glitch.
            // Invece di fidarsi dei landmark del piede, l'algoritmo valuta la compressione geometrica del torso.
            val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
            val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f

            val spostamentoSpalleDietro = if (isFacingRight) centroBacinoX - spallaCentroX else spallaCentroX - centroBacinoX
            val schiacciamentoTroncoY = bacinoY - spallaCentroY
            val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

            // Verifichiamo se il busto è palesemente inarcato a prescindere da dove sia letto il piede dietro.
            val isFlessioneEvidente = distNasoBacino < 0.25f ||
                    spostamentoSpalleDietro > 0.25f ||
                    (schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f)

            // Assegniamo le varianti (Boucle/Flessione) ignorando i glitch della gamba se il tronco
            // dimostra una flessione estrema.
            val isBoucle = backKneeAngle <= 130.0 && distanzaTestaPiede <= 0.22f && !isFlessioneEvidente
            val isFlessione = isFlessioneEvidente || (backKneeAngle > 130.0 && distanzaTestaPiede <= 0.20f)


            // Albero decisionale per i Valori Base
            var valoreBase = if (isEnTournant) 0.50 else 0.40

            if (isBoucle) {
                nome = if (isEnTournant) "SALTO BICHE CAMBIO TOURNANT+BOUCLE" else "SALTO BICHE CAMBIO + BOUCLE"
                valoreBase = if (isEnTournant) 0.70 else 0.60
            } else if (isFlessione) {
                nome = if (isEnTournant) "SALTO BICHE CAMBIO TOURNANT+FLESS." else "SALTO BICHE CAMBIO + FLESSIONE"
                valoreBase = if (isEnTournant) 0.70 else 0.60
            } else {
                nome = if (isEnTournant) "SALTO BICHE EN TOURNANT CON CAMBIO" else "SALTO BICHE CON CAMBIO"
            }

            var penalita = 0.0
            var isValida = true
            var msgErrore = ""

            // Quantificazione del deficit di ampiezza spaccata
            if (angoloSpaccata >= 170.0) penalita += 0.0
            else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
            else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
            else {
                penalita += 0.50
                isValida = false
                msgErrore = "Apertura spaccata insufficiente"
            }

            // PENALITÀ GAMBA DIETRO
            // Se sussiste una Flessione Evidente che ha mandato in occlusione la telecamera,
            // l'algoritmo bypassa la penalità sulla gamba piegata, riconoscendo
            // il limite hardware del sensore ed evitando di sottrarre punti ingiustamente.
            if (!nome.contains("BOUCLE") && isValida && backKneeAngle < 150.0 && !isFlessioneEvidente) {
                penalita += 0.30
                if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
            }

            // Reset dei buffer di memoria per i salti successivi
            tempoUltimoSlancio = 0L
            gambaSlancioIsLeft = null
            if (isValida) tempoUltimaRotazione = 0L

            return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
        }
        return null
    }
}
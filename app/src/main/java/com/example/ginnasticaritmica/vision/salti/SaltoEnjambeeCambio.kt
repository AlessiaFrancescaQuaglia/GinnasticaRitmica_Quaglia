package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

class SaltoEnjambeeCambio : Difficolta {
    override var nome = "ENJAMBÉE CON CAMBIO"

    companion object {
        private var tempoUltimoSlancio: Long = 0L
        private var gambaSlancioIsLeft: Boolean? = null
        private var isSlancioTeso: Boolean = true
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. SCUDO ANTI-GIRO: Se un giro è in corso, non è un salto!
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

        // Usiamo la lunghezza del femore come unità di misura invariabile (immune agli inarcamenti)
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Una gamba è piantata a terra SOLO se scende in profondità (più del femore)
        // ED è sufficientemente tesa per sostenere il peso (angolo > 135°).
        // Nel Biche la gamba scende ma è piegata a 90°, quindi il Firewall la lascerà passare!
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Sicurezza extra: se una caviglia è paurosamente in basso (vicino al fondo dello schermo)
        // e lontana dal bacino, spegniamo i salti a prescindere dall'angolo.
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null // Piede a terra confermato, passa agli equilibri!
        }





        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

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

        val yDiff = leftAnkle.y() - rightAnkle.y()
        if (abs(yDiff) > 0.15f) {
            val isLeftHigher = leftAnkle.y() < rightAnkle.y()
            val higherKneeAngle = if (isLeftHigher) leftKneeAngle else rightKneeAngle

            if (higherKneeAngle > 140.0) {
                tempoUltimoSlancio = System.currentTimeMillis()
                gambaSlancioIsLeft = isLeftHigher
                isSlancioTeso = true
            } else if (higherKneeAngle in 70.0..139.9) {
                tempoUltimoSlancio = System.currentTimeMillis()
                gambaSlancioIsLeft = isLeftHigher
                isSlancioTeso = false
            }
        }

        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) rightToe else leftToe

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle < 145.0) return null

        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val centroBacinoY = (leftHip.y() + rightHip.y()) / 2f
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)

        // ==========================================
        // CALCOLO SPACCATA IBRIDO
        // ==========================================
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontAnkle, centroBacino, backAnkle)
        val v1x = frontAnkle.x() - centroBacinoX
        val v1y = frontAnkle.y() - centroBacinoY
        val v2x = backAnkle.x() - centroBacinoX
        val v2y = backAnkle.y() - centroBacinoY
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
        val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dotProduct / (mag1 * mag2)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val angoloSpaccata = kotlin.math.max(angoloSpaccata3D, angoloSpaccata2D)

        if (angoloSpaccata < 130.0) return null

        val tempoTrascorsoSlancio = System.currentTimeMillis() - tempoUltimoSlancio

        if (tempoTrascorsoSlancio < 1500L && gambaSlancioIsLeft != null && isLeftFront != gambaSlancioIsLeft) {

            val tempoTrascorsoRotazione = System.currentTimeMillis() - tempoUltimaRotazione
            val isEnTournant = tempoTrascorsoRotazione < 1500L

            val nose = landmarks[0]
            val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
            val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

            // ==========================================
            // SENSORE DI FLESSIONE ANTI-GLITCH (Core-Based)
            // ==========================================
            val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
            val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f
            val spostamentoSpalleDietro = if (isFacingRight) centroBacinoX - spallaCentroX else spallaCentroX - centroBacinoX

            val schiacciamentoTroncoY = bacinoY - spallaCentroY
            val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

            // Verifichiamo se il busto è palesemente inarcato
            val isFlessioneEvidente = distNasoBacino < 0.25f ||
                    spostamentoSpalleDietro > 0.25f ||
                    (schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f)

            val isBoucle = backKneeAngle <= 130.0 && distanzaTestaPiede <= 0.22f && !isFlessioneEvidente
            val isFlessione = isFlessioneEvidente || (backKneeAngle > 130.0 && distanzaTestaPiede <= 0.20f)
            // ==========================================

            var valoreBase = 0.0
            val prefissoRotazione = if (isEnTournant) " SALTO CON CAMBIO TOURNANT" else "SALTO CON CAMBIO"

            if (isSlancioTeso) {
                if (isBoucle) {
                    nome = "$prefissoRotazione (Tesa) + BOUCLE"
                    valoreBase = if (isEnTournant) 0.80 else 0.70
                } else if (isFlessione) {
                    nome = "$prefissoRotazione (Tesa) + FLESS."
                    valoreBase = if (isEnTournant) 0.80 else 0.70
                } else {
                    nome = "$prefissoRotazione (Tesa)"
                    valoreBase = if (isEnTournant) 0.60 else 0.50
                }
            } else {
                if (isBoucle) {
                    nome = "$prefissoRotazione (Flessa) + BOUCLE"
                    valoreBase = if (isEnTournant) 0.70 else 0.60
                } else if (isFlessione) {
                    nome = "$prefissoRotazione (Flessa) + FLESS."
                    valoreBase = if (isEnTournant) 0.70 else 0.60
                } else {
                    nome = "$prefissoRotazione (Flessa)"
                    valoreBase = if (isEnTournant) 0.50 else 0.40
                }
            }

            var penalita = 0.0
            var isValida = true
            var msgErrore = ""

            if (angoloSpaccata >= 170.0) penalita += 0.0
            else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
            else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
            else {
                penalita += 0.50
                isValida = false
                msgErrore = "Apertura spaccata insufficiente"
            }

            // --- PERDONO PENALITÀ GINOCCHIO IN FLESSIONE EXTREMA ---
            if (!nome.contains("BOUCLE") && isValida && !isFlessioneEvidente) {
                if (backKneeAngle < 150.0) {
                    penalita += 0.30
                    if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
                }
            }

            tempoUltimoSlancio = 0L
            gambaSlancioIsLeft = null
            if (isValida) tempoUltimaRotazione = 0L

            return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
        }
        return null
    }
}
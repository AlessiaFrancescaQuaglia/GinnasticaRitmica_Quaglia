package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

class SaltoBicheCambio : Difficolta {
    override var nome = "BICHE CON CAMBIO"

    // --- DOPPIA MEMORIA (Rotazione + Slancio) ---
    companion object {
        private var tempoUltimoSlancio: Long = 0L
        private var gambaSlancioIsLeft: Boolean? = null

        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. TRACCIAMENTO DELLA ROTAZIONE
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()

        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // 2. TRACCIAMENTO DELLO SLANCIO
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val yDiff = leftAnkle.y() - rightAnkle.y()
        if (abs(yDiff) > 0.15f) {
            val isLeftHigher = leftAnkle.y() < rightAnkle.y()
            val higherKneeAngle = if (isLeftHigher) leftKneeAngle else rightKneeAngle
            if (higherKneeAngle > 140.0) { // Slancio a gamba tesa
                tempoUltimoSlancio = System.currentTimeMillis()
                gambaSlancioIsLeft = isLeftHigher
            }
        }

        // 3. RILEVAMENTO DELLA FORMA (Biche)
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]
        val leftHeel = landmarks[29]
        val rightHeel = landmarks[30]
        val isFacingRight = (leftToe.x() + rightToe.x()) > (leftHeel.x() + rightHeel.x())

        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) rightToe else leftToe

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle > 140.0 || frontKneeAngle < 45.0) return null

        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val centroBacinoY = (leftHip.y() + rightHip.y()) / 2f
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)
        val angoloSpaccata = PoseAnalyzer.calculateAngle3D(frontKnee, centroBacino, backKnee)

        if (angoloSpaccata < 130.0) return null

        // 4. MATCH TEMPORALE COMPLETO (Cambio + Rotazione)
        val tempoTrascorsoSlancio = System.currentTimeMillis() - tempoUltimoSlancio

        if (tempoTrascorsoSlancio < 1500L && gambaSlancioIsLeft != null && isLeftFront != gambaSlancioIsLeft) {

            val tempoTrascorsoRotazione = System.currentTimeMillis() - tempoUltimaRotazione
            val isEnTournant = tempoTrascorsoRotazione < 1500L

            val nose = landmarks[0]
            val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
            val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)
            val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
            val isTorsoBentBack = if (isFacingRight) spallaCentroX < centroBacinoX - 0.1f else spallaCentroX > centroBacinoX + 0.1f

            var valoreBase = if (isEnTournant) 0.50 else 0.40

            if (backKneeAngle <= 130.0 && distanzaTestaPiede < 0.35f) {
                nome = if (isEnTournant) "BICHE CAMBIO TOURNANT+BOUCLE" else "BICHE CAMBIO + BOUCLE"
                valoreBase = if (isEnTournant) 0.70 else 0.60
            } else if (backKneeAngle > 140.0 && (isTorsoBentBack || distanzaTestaPiede < 0.40f)) {
                nome = if (isEnTournant) "BICHE CAMBIO TOURNANT+FLESS." else "BICHE CAMBIO + FLESSIONE"
                valoreBase = if (isEnTournant) 0.70 else 0.60
            } else {
                nome = if (isEnTournant) "BICHE EN TOURNANT CON CAMBIO" else "BICHE CON CAMBIO"
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

            // Reset delle memorie se salto completato
            tempoUltimoSlancio = 0L
            gambaSlancioIsLeft = null
            if (isValida) tempoUltimaRotazione = 0L

            return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
        }

        return null
    }
}
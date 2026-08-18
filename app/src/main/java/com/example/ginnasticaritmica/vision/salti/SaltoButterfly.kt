package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class SaltoButterfly : Difficolta {
    override var nome = "SALTO BUTTERFLY"

    companion object {
        private var tempoUltimoButterfly: Long = 0L
        private var bestApertura = 0.0
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoX = (leftHip.x() + rightHip.x()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        val inVolo = !(isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted)

        if (!inVolo) {
            tempoUltimoButterfly = 0L
            bestApertura = 0.0
            return null
        }

        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)
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

        val aperturaReale = max(angoloSpaccata3D, angoloSpaccata2D)

        val isButterflyAttivo = (System.currentTimeMillis() - tempoUltimoButterfly < 1000L) && tempoUltimoButterfly != 0L

        if (!isButterflyAttivo) {
            val spallaSx = landmarks[11]
            val spallaDx = landmarks[12]
            val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f

            // 1. Corpo Eretto: Se le spalle sono palesemente in alto, scarta.
            if (bacinoY - spallaCentroY > 0.16f) return null

            // 2. Ginocchia
            val gambaMigliore = max(leftKneeAngle, rightKneeAngle)
            if (gambaMigliore < 120.0) return null

            // 3. Spaccata orizzontale
            val dyCaviglie = kotlin.math.abs(leftAnkle.y() - rightAnkle.y())
            val dxCaviglie = kotlin.math.abs(leftAnkle.x() - rightAnkle.x())
            val inclinazioneSpaccata = Math.toDegrees(kotlin.math.atan2(dyCaviglie.toDouble(), dxCaviglie.toDouble()))
            if (inclinazioneSpaccata > 35.0) return null

            // 4. Volo a T (Caviglie alte)
            if (leftAnkle.y() > bacinoY + 0.12f || rightAnkle.y() > bacinoY + 0.12f) return null

            // ==========================================
            // IL FIX: MISURATORE DELLA SPINA DORSALE
            // ==========================================
            val spallaCentro = NormalizedLandmark.create(
                (spallaSx.x() + spallaDx.x()) / 2f,
                spallaCentroY,
                (spallaSx.z() + spallaDx.z()) / 2f
            )

            val lunghezzaTronco = PoseAnalyzer.calculateDistance3D(spallaCentro, centroBacino)
            val distNasoBacino = PoseAnalyzer.calculateDistance3D(landmarks[0], centroBacino)

            // Se il naso si avvicina al bacino più del normale (meno di 1.25 volte il tronco),
            // la ginnasta si sta flettendo all'INDIETRO. Il Butterfly non può innescarsi!
            if (distNasoBacino < lunghezzaTronco * 1.25f) return null

            if (aperturaReale > 90.0) {
                tempoUltimoButterfly = System.currentTimeMillis()
                bestApertura = aperturaReale
            }

        } else {
            if (aperturaReale > bestApertura) {
                bestApertura = aperturaReale
            }
        }

        val isAttivoOra = (System.currentTimeMillis() - tempoUltimoButterfly < 1000L) && tempoUltimoButterfly != 0L

        if (isAttivoOra) {
            val valoreBase = 0.50
            var penalita = 0.0
            var isValida = true
            var msgErrore = ""

            if (bestApertura >= 145.0) penalita += 0.0
            else if (bestApertura >= 125.0) penalita += 0.10
            else if (bestApertura >= 100.0) penalita += 0.30
            else {
                penalita += 0.50
                isValida = false
                msgErrore = "Apertura divaricata insufficiente"
            }

            return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
        }

        return null
    }
}
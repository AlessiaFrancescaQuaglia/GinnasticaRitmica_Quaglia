package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class GiroPasse : Difficolta {
    override var nome = "PIVOT EN PASSÉ"

    companion object {
        private var gradiAccumulati = 0.0
        private var ultimoAngoloOrientamento = -1.0
        private var timestampUltimoFrame = 0L
        private var isGiroInCorso = false
        private var giriCompletati = 0
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        // RESET ISTANTANEO (Piedi a terra)
        if (abs(liftedAnkle.y() - supportAnkle.y()) < 0.04f) {
            resetGiro()
            return null
        }

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val dxAnkleKnee = liftedAnkle.x() - supportKnee.x()
        val dyAnkleKnee = liftedAnkle.y() - supportKnee.y()
        val distAnkleToKnee2D = sqrt((dxAnkleKnee * dxAnkleKnee + dyAnkleKnee * dyAnkleKnee).toDouble())

        val sogliaDistanza = if (isGiroInCorso) 0.50 else 0.35
        val sogliaAltezzaGinocchio = if (isGiroInCorso) bacinoY + 0.40f else bacinoY + 0.20f
        val sogliaAngoloChiuso = if (isGiroInCorso) 140.0 else 105.0

        val isGinocchioAlto = liftedKnee.y() < sogliaAltezzaGinocchio
        val isGinocchioChiuso = liftedKneeAngle < sogliaAngoloChiuso

        val isPosaCorretta = distAnkleToKnee2D <= sogliaDistanza && isGinocchioAlto && isGinocchioChiuso
        var isOcclusione = false

        if (!isPosaCorretta) {
            if (isGiroInCorso && (System.currentTimeMillis() - timestampUltimoFrame < 500L)) {
                isOcclusione = true
            } else {
                resetGiro()
                return null
            }
        } else {
            timestampUltimoFrame = System.currentTimeMillis()
        }

        val dx = spallaDx.x() - spallaSx.x()
        val dz = spallaDx.z() - spallaSx.z()
        var angoloAttuale = Math.toDegrees(atan2(dz.toDouble(), dx.toDouble()))
        if (angoloAttuale < 0) angoloAttuale += 360.0

        if (ultimoAngoloOrientamento == -1.0) {
            ultimoAngoloOrientamento = angoloAttuale
            isGiroInCorso = true
        } else {
            var deltaAngolo = angoloAttuale - ultimoAngoloOrientamento
            while (deltaAngolo < -180.0) deltaAngolo += 360.0
            while (deltaAngolo > 180.0) deltaAngolo -= 360.0

            // Alzato il limite anti-vibrazione
            if (abs(deltaAngolo) > 2.5) {
                gradiAccumulati += abs(deltaAngolo)
                ultimoAngoloOrientamento = angoloAttuale
            }

            val nuoviGiri = (gradiAccumulati / 360.0).toInt()
            if (nuoviGiri > giriCompletati) {
                giriCompletati = nuoviGiri
            }
        }

        // ==========================================
        // RITARDO DI INNESCO (Protegge gli Equilibri)
        // ==========================================
        if (giriCompletati == 0 && gradiAccumulati < 15.0) {
            return null
        }

        val valoreBaseDifficolta = 0.10
        val valoreTotale = if (giriCompletati >= 1) {
            valoreBaseDifficolta + ((giriCompletati - 1) * 0.10)
        } else {
            0.0
        }

        if (giriCompletati >= 1) {
            nome = "PIVOT EN PASSÉ ($giriCompletati Giri)"
        } else if (isOcclusione) {
            nome = "PIVOT EN PASSÉ (Occlusione...)"
        } else {
            nome = "PIVOT EN PASSÉ (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        gradiAccumulati = 0.0
        ultimoAngoloOrientamento = -1.0
        isGiroInCorso = false
        giriCompletati = 0
    }
}
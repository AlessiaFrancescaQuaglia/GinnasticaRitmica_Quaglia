package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class GiroGambaAvanti : Difficolta {
    override var nome = "PIVOT GAMBA AVANTI"

    companion object {
        private var gradiAccumulati = 0.0
        private var ultimoAngoloOrientamento = -1.0
        private var timestampUltimoFrame = 0L
        private var isGiroInCorso = false
        private var giriCompletati = 0
        private var isGambaTesa = false
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        // RESET ISTANTANEO (Piedi a terra)
        if (abs(liftedAnkle.y() - supportAnkle.y()) < 0.04f) {
            resetGiro()
            return null
        }

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        // FIREWALL BUSTO: Più severo per impedire al Giro di rubare il Penché!
        // Le spalle devono essere più alte del bacino.
        val sogliaBusto = if (isGiroInCorso) bacinoY else bacinoY - 0.10f
        val isBustoEretto = spallaY < sogliaBusto

        val sogliaGinocchio = if (isGiroInCorso) bacinoY + 0.40f else bacinoY + 0.25f
        val isKneeAlto = liftedKnee.y() < sogliaGinocchio
        val sogliaAngoloAperto = if (isGiroInCorso) 75.0 else 105.0
        val isAngoloAperto = liftedKneeAngle >= sogliaAngoloAperto

        val isPosaCorretta = isBustoEretto && isKneeAlto && isAngoloAperto
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

        if (!isOcclusione) {
            if (!isGiroInCorso) {
                isGambaTesa = liftedKneeAngle > 155.0
            } else {
                if (liftedKneeAngle > 155.0) {
                    isGambaTesa = true
                }
            }
        }

        val spallaSx = leftShoulder
        val spallaDx = rightShoulder
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

            // Alziamo il limite per ignorare i micro-movimenti degli equilibri
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
        // Mantiene la memoria ma ritorna NULL finché non avvengono almeno 15 gradi di rotazione reale.
        // Questo permette agli equilibri statici di brillare senza farsi rubare la scena dalle vibrazioni!
        if (giriCompletati == 0 && gradiAccumulati < 15.0) {
            return null
        }

        val valoreBaseDifficolta = if (isGambaTesa) 0.30 else 0.10
        val valoreTotale = if (giriCompletati >= 1) {
            valoreBaseDifficolta + ((giriCompletati - 1) * 0.10)
        } else {
            0.0
        }

        val etichettaGiro = if (isGambaTesa) "GAMBA TESA (0.30)" else "GAMBA FLESSA 30° (0.10)"

        if (giriCompletati >= 1) {
            nome = "PIVOT $etichettaGiro ($giriCompletati Giri)"
        } else if (isOcclusione) {
            nome = "PIVOT $etichettaGiro (Occlusione...)"
        } else {
            nome = "PIVOT $etichettaGiro (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        gradiAccumulati = 0.0
        ultimoAngoloOrientamento = -1.0
        isGiroInCorso = false
        giriCompletati = 0
        isGambaTesa = false
    }
}
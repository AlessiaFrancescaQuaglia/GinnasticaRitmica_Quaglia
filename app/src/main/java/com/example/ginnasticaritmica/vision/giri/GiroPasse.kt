package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class GiroPasse : Difficolta {
    override var nome = "PIVOT EN PASSÉ"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        private var isFormaBloccata = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        // ==========================================
        // 0. FIREWALL BASE & RESET DINAMICO
        // ==========================================
        // Anti-Capriola
        if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.2f)) {
            resetGiro()
            return null
        }

        // ==========================================
        // LO SCUDO ANTI-PENCHÉ SUPREMO
        // ==========================================
        // È messo fuori da ogni blocco: viene valutato in OGNI frame.
        // Se la spalla si abbassa troppo (la ginnasta si piega in avanti),
        // il Passé si azzera all'istante e sblocca il lucchetto!
        if (spallaY > bacinoY - (scalaCorpo * 0.35f)) {
            resetGiro()
            return null
        }

        val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()
        if (liftedAnkle.y() > ginocchioSupportoY) {
            if (!isFormaBloccata) {
                resetGiro()
                return null
            } else {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    if (!isGiroInCorso) {
                        resetGiro()
                        return null
                    }
                }
            }
        } else {
            timestampGambaGiu = 0L
        }

        // ==========================================
        // 1. VERIFICA POSA E LATCH FORMA
        // ==========================================
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        if (!isFormaBloccata) {
            val distAnkleToKnee = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)

            // Anatomico: Ginocchio sollevato, chiuso e PIEDE STRETTAMENTE ATTACCATO!
            val isGinocchioAlto = liftedKnee.y() < bacinoY + (scalaCorpo * 0.3f)
            val isGinocchioChiuso = liftedKneeAngle < 105.0

            // La tolleranza è dimezzata: ora il piede deve essere davvero vicino al ginocchio.
            val isPiedeAlGinocchio = distAnkleToKnee < (scalaCorpo * 0.4f)

            if (!(isGinocchioAlto && isGinocchioChiuso && isPiedeAlGinocchio)) return null

            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)

            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()

            // Zero "Preparazione...": Subito in rotazione!
            isGiroInCorso = true
        } else {
            // Se la gamba si allontana o cede, sblocca
            val distAnkleToKnee = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)
            val isAncoraInPasse = (liftedKnee.y() < bacinoY + (scalaCorpo * 0.6f)) && (distAnkleToKnee < (scalaCorpo * 0.6f))

            if (isAncoraInPasse) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                val tolleranzaTempo = if (isGiroInCorso) 1500L else 800L
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > tolleranzaTempo) {
                    resetGiro()
                    return null
                }
            }

            if (isGiroInCorso && System.currentTimeMillis() - timestampUltimoSwap > 2000L) {
                resetGiro()
                return null
            }
        }

        // ==========================================
        // 3. MOTORE ADATTIVO X-SWAP CON RICARICA
        // ==========================================
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
            } else if (statoFronteRetro != 0 && statoAttualeSpalle != 0 && statoAttualeSpalle != statoFronteRetro) {
                val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap

                if (tempoDalUltimoSwap > 150L) {
                    mezziGiriCompletati++
                    statoFronteRetro = statoAttualeSpalle
                    timestampUltimoSwap = System.currentTimeMillis()

                    timestampUltimoFrameBusto = System.currentTimeMillis()
                    timestampGambaGiu = 0L
                }
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0 && !isFormaBloccata) return null

        // ==========================================
        // 4. RESTITUZIONE PUNTEGGIO
        // ==========================================
        val valoreBaseDifficolta = 0.10
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        val etichettaFinale = "PIVOT EN PASSÉ"

        nome = if (giriCompletati >= 1) {
            "$etichettaFinale ($giriCompletati Giri)"
        } else {
            "$etichettaFinale (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        gambaDiSupportoIsLeft = null
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
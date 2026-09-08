package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class GiroBustoOrizzontale : Difficolta {
    override var nome = "PIVOT BUSTO ORIZZONTALE"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0

        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        private var haUsatoAiuto = false
        private var isGambaFlessaLocked = false
        private var isFormaBloccata = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        // ==========================================
        // 0. FIREWALL BASE & RESET DINAMICO
        // ==========================================
        if (!isFormaBloccata) {
            // Anti-Capriola
            if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.2f)) {
                resetGiro()
                return null
            }

            // ANTI-PENCHÉ GRAND ECART
            if (spallaY > bacinoY + (scalaCorpo * 0.6f)) {
                resetGiro()
                return null
            }
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

        // SENSIBILITÀ ESTREMA (Calibrata per i torsi orizzontali)
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        if (!isFormaBloccata) {
            val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
            val midHipLandmark = NormalizedLandmark.create(bacinoCenterX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
            val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)

            val isBustoOrizzontale = spallaY > bacinoY - (scalaCorpo * 0.4f)

            // Regola del Raffaeli (Piede lontano)
            val isRaffaeli = (liftedKneeAngle < 145.0) && (distNasoCaviglia >= (scalaCorpo * 0.6f))

            val isGambaAlta = if (isRaffaeli) {
                liftedKnee.y() < bacinoY + (scalaCorpo * 0.2f)
            } else {
                (aperturaSpaccata >= 120.0) && (liftedAnkle.y() < bacinoY + (scalaCorpo * 0.2f))
            }

            if (!(isBustoOrizzontale && isGambaAlta)) return null

            isGambaFlessaLocked = isRaffaeli
            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)

            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()

            // ELIMINATA LA "PREPARAZIONE" -> Si entra subito in rotazione!
            isGiroInCorso = true
        } else {
            val isBustoAncoraGiu = spallaY > bacinoY - (scalaCorpo * 0.7f)
            if (isBustoAncoraGiu) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > 1500L) {
                    resetGiro()
                    return null
                }
            }

            if (System.currentTimeMillis() - timestampUltimoSwap > 2000L) {
                resetGiro()
                return null
            }
        }

        // ==========================================
        // 2. SENSORE AIUTO
        // ==========================================
        if (!isFormaBloccata) {
            val distPolsoSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            if (distPolsoSx < (scalaCorpo * 0.8f) || distPolsoDx < (scalaCorpo * 0.8f)) haUsatoAiuto = true
        }

        // ==========================================
        // 3. MOTORE ADATTIVO X-SWAP (Mezzi Giri 180°)
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

        if (mezziGiriCompletati == 0 && !isFormaBloccata) return null

        // ==========================================
        // 4. RESTITUZIONE PUNTEGGIO
        // ==========================================
        val valoreBaseDifficolta = if (haUsatoAiuto) 0.40 else 0.50
        val valoreTotale = if (mezziGiriCompletati >= 1) valoreBaseDifficolta + ((mezziGiriCompletati - 1) * 0.10) else 0.0

        val titoloBase = if (isGambaFlessaLocked) "PIVOT RAFFAELI" else "PIVOT ELKATIB/LAT."
        val etichettaAiuto = if (haUsatoAiuto) "(Con Aiuto)" else "(Libero)"

        // Addio scritta "Preparazione...", si salta direttamente al succo!
        nome = if (mezziGiriCompletati >= 1) {
            val gradiTesto = mezziGiriCompletati * 180
            "$titoloBase $etichettaAiuto ($gradiTesto°)"
        } else {
            "$titoloBase $etichettaAiuto (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        haUsatoAiuto = false
        isGambaFlessaLocked = false
        isFormaBloccata = false
        gambaDiSupportoIsLeft = null
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
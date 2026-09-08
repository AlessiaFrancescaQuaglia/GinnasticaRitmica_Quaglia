package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class GiroPenche : Difficolta {
    override var nome = "GIRO GRAND ÉCART INDIETRO"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0

        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        private var haUsatoAiuto = false
        private var isBoucleSakuraLocked = false
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

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        // ==========================================
        // 0. FIREWALL BASE & RESET DINAMICO
        // ==========================================
        if (!isFormaBloccata) {
            if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.2f)) {
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
        // 1. VERIFICA POSA E LATCH DELLA FORMA
        // ==========================================
        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.015f, scalaCorpo * 0.08f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        if (!isFormaBloccata) {
            val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)

            val dx1 = supportAnkle.x() - bacinoCenterX
            val dy1 = supportAnkle.y() - bacinoY
            val dx2 = liftedAnkle.x() - bacinoCenterX
            val dy2 = liftedAnkle.y() - bacinoY
            val dot = (dx1 * dx2) + (dy1 * dy2)
            val mag1 = kotlin.math.sqrt((dx1 * dx1 + dy1 * dy1).toDouble())
            val mag2 = kotlin.math.sqrt((dx2 * dx2 + dy2 * dy2).toDouble())
            var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (mag1 * mag2)))
            if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

            val midHipLandmark = NormalizedLandmark.create(bacinoCenterX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
            val aperturaSpaccata3D = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
            val aperturaSpaccata = max(angoloSpaccata2D, aperturaSpaccata3D)

            val isBustoOrizzontaleOSotto = spallaY > bacinoY - (scalaCorpo * 0.5f)

            // LA TUA REGOLA D'ORO: Il Penché Boucle HA IL PIEDE ATTACCATO ALLA TESTA!
            // (La distanza deve essere molto piccola: scalaCorpo * 0.6f)
            val isBoucleAttuale = (liftedKneeAngle < 145.0) && (distNasoCaviglia < (scalaCorpo * 0.6f))

            val isBustoSprofondato = spallaY > bacinoY + (scalaCorpo * 0.1f)
            if (isBoucleAttuale && !isBustoSprofondato) {
                return null
            }

            val isPencheTeso = aperturaSpaccata >= 110.0

            if (!(isBustoOrizzontaleOSotto && (isBoucleAttuale || isPencheTeso))) {
                return null
            }

            isBoucleSakuraLocked = isBoucleAttuale
            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)

            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()
        } else {
            val isBustoAncoraGiu = spallaY > bacinoY - (scalaCorpo * 0.7f)
            if (isBustoAncoraGiu) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                val tolleranzaBusto = if (isGiroInCorso) 1500L else 800L
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > tolleranzaBusto) {
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
        // 2. SENSORE AIUTO
        // ==========================================
        if (!isFormaBloccata) {
            val distPolsoSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            if (distPolsoSx < (scalaCorpo * 0.8f) || distPolsoDx < (scalaCorpo * 0.8f)) haUsatoAiuto = true
        }

        // ==========================================
        // 3. MOTORE HYSTERESIS X-SWAP
        // ==========================================
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
            } else if (statoFronteRetro != 0 && statoAttualeSpalle != 0 && statoAttualeSpalle != statoFronteRetro) {
                val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap

                if (tempoDalUltimoSwap > 150L) {
                    mezziGiriCompletati++
                    statoFronteRetro = statoAttualeSpalle
                    timestampUltimoSwap = System.currentTimeMillis()

                    timestampUltimoFrameBusto = System.currentTimeMillis()
                    timestampGambaGiu = 0L
                    isGiroInCorso = true
                }
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0 && !isFormaBloccata) return null

        val valoreBaseDifficolta = if (haUsatoAiuto) 0.40 else 0.50
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        val etichettaForma = if (isBoucleSakuraLocked) "BOUCLE" else " "
        val etichettaAiuto = if (haUsatoAiuto) "(Con Aiuto)" else "(Libero)"
        val etichettaFinale = "GIRO PENCHÉ $etichettaForma$etichettaAiuto"

        nome = if (giriCompletati >= 1) {
            "$etichettaFinale ($giriCompletati Giri)"
        } else if (mezziGiriCompletati > 0) {
            "$etichettaFinale (In rotazione...)"
        } else {
            "$etichettaFinale (Preparazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        haUsatoAiuto = false
        isBoucleSakuraLocked = false
        isFormaBloccata = false
        gambaDiSupportoIsLeft = null
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
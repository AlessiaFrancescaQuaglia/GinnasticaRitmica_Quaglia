package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.max

class GiroGrandEcart : Difficolta {
    override var nome = "PIVOT GRAND ÉCART"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrame = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // Memorie bloccate
        private var isFormaBloccata = false
        private var haUsatoAiutoLocked = false
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
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        // ==========================================
        // 0. RESET ISTANTANEO (E FIREWALL PENCHÉ)
        // ==========================================
        if (abs(liftedAnkle.y() - supportAnkle.y()) < 0.04f) {
            resetGiro()
            return null
        }

        // Se il busto si schiaccia (Penché), scappa subito! Il Grand Écart è dritto.
        if (spallaY > bacinoY + 0.05f) {
            resetGiro()
            return null
        }

        // ==========================================
        // IL DANNATO FILTRO (LUCCHETTO FISSO)
        // ==========================================
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        // ==========================================
        // 1. VERIFICA POSA E LATCH FORMA
        // ==========================================
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

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

        if (!isFormaBloccata) {
            val isBustoEretto = spallaY < bacinoY - 0.05f
            val isSpaccataAmpia = aperturaSpaccata >= 150.0
            val isGinocchioTeso = liftedKneeAngle >= 155.0
            val isGambaAltissima = liftedAnkle.y() < spallaY + 0.15f

            if (!(isBustoEretto && isSpaccataAmpia && isGinocchioTeso && isGambaAltissima)) return null

            val distPolsoSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            haUsatoAiutoLocked = distPolsoSx < 0.15f || distPolsoDx < 0.15f

            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)
            timestampUltimoFrame = System.currentTimeMillis()
        } else {
            val tolleranzaTempo = if (mezziGiriCompletati >= 2) 1200L else 500L
            // Finché mantiene un po' di spaccata, teniamo vivo il giro
            if (aperturaSpaccata > 120.0) {
                timestampUltimoFrame = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - timestampUltimoFrame > tolleranzaTempo) {
                resetGiro()
                return null
            }
        }

        // ==========================================
        // 3. MOTORE A SCAMBIO DI ASSE (X-SWAP) - INFALLIBILE
        // ==========================================
        val dxSpalle = rightShoulder.x() - leftShoulder.x()

        val sogliaSwap = 0.015f
        var nuovoStato = statoFronteRetro

        if (dxSpalle > sogliaSwap) {
            nuovoStato = 1
        } else if (dxSpalle < -sogliaSwap) {
            nuovoStato = -1
        }

        if (statoFronteRetro == 0 && nuovoStato != 0) {
            statoFronteRetro = nuovoStato
            isGiroInCorso = true
            timestampUltimoSwap = System.currentTimeMillis()
        } else if (statoFronteRetro != 0 && nuovoStato != statoFronteRetro) {
            val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap
            if (tempoDalUltimoSwap > 200L) {
                mezziGiriCompletati++
                statoFronteRetro = nuovoStato
                timestampUltimoSwap = System.currentTimeMillis()
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0) return null

        // ==========================================
        // 4. RESTITUZIONE PUNTEGGIO
        // ==========================================
        val valoreBaseDifficolta = if (haUsatoAiutoLocked) 0.30 else 0.50
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        val etichettaGiro = if (haUsatoAiutoLocked) "CON AIUTO (0.30)" else "LIBERO (0.50)"

        if (giriCompletati >= 1) {
            nome = "PIVOT GRAND ÉCART $etichettaGiro ($giriCompletati Giri)"
        } else {
            nome = "PIVOT GRAND ÉCART $etichettaGiro (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        haUsatoAiutoLocked = false
        timestampUltimoSwap = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
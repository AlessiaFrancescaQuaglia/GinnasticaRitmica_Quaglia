package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class GiroArabesque : Difficolta {
    override var nome = "GIRO ARABESQUE"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        private var isFormaBloccata = false
        private var isAttitudeLocked = false
        private var isBustoOrizzontaleLocked = false
        private var isBustoIndietroLocked = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
        private var dxSpalleIniziale = 0f
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

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoCenterZ = (leftHip.z() + rightHip.z()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val spallaCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val spallaCenterZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        // ==========================================
        // 1. VERIFICA POSA
        // ==========================================
        if (!isFormaBloccata) {

            if (supportAnkle.y() < bacinoY + (scalaCorpo * 0.2f)) return null

            // --- SCUDO ANTI-DÉGAGÉ (Body Plane Check) ---
            // Vettore Spina Dorsale (dal bacino alle spalle, asse Y locale)
            val spineX = spallaCenterX - bacinoCenterX
            val spineY = spallaY - bacinoY
            val spineZ = spallaCenterZ - bacinoCenterZ

            // Vettore Fianchi (dal fianco sinistro al destro, asse X locale)
            val hipX = rightHip.x() - leftHip.x()
            val hipY = rightHip.y() - leftHip.y()
            val hipZ = rightHip.z() - leftHip.z()

            // Prodotto Vettoriale (Spine x Hip) = Normale che punta "avanti" rispetto al torace
            val forwardX = spineY * hipZ - spineZ * hipY
            val forwardY = spineZ * hipX - spineX * hipZ
            val forwardZ = spineX * hipY - spineY * hipX

            // Vettore della gamba sollevata (dal bacino al ginocchio)
            val legX = liftedKnee.x() - bacinoCenterX
            val legY = liftedKnee.y() - bacinoY
            val legZ = liftedKnee.z() - bacinoCenterZ

            // Prodotto scalare per verificare da che lato del petto si trova la gamba
            val dotFrontBack = forwardX * legX + forwardY * legY + forwardZ * legZ

            // Se la gamba è nel semispazio anteriore (Dégagé), interrompe l'Arabesque
            if (dotFrontBack > -0.01f) {
                return null
            }

            // --- CONTROLLI ARABESQUE STANDARD ---
            val isKneeAlto = liftedKnee.y() < bacinoY + (scalaCorpo * 0.5f)
            if (!isKneeAlto) return null

            val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
            val distPiedeGinocchio = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)

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

            val isBustoInclinato = spallaY > bacinoY - (scalaCorpo * 0.45f)
            val isSpaccataPenche = aperturaSpaccata >= 115.0
            val isBoucle = (liftedKneeAngle < 145.0) && (distNasoCaviglia < (scalaCorpo * 1.5f))
            if (isBustoInclinato && (isSpaccataPenche || isBoucle)) return null
            if (spallaY > bacinoY + (scalaCorpo * 0.05f)) return null

            val isGambaFlessa = liftedKneeAngle < 145.0
            val isPiedeMoltoAlto = liftedAnkle.y() < bacinoY - (scalaCorpo * 0.1f)
            if (isGambaFlessa && isPiedeMoltoAlto) return null
            if (liftedKneeAngle < 110.0 && distPiedeGinocchio < (scalaCorpo * 0.9f)) return null

            // LATCH!
            isAttitudeLocked = liftedKneeAngle < 145.0
            isBustoOrizzontaleLocked = spallaY > bacinoY - (scalaCorpo * 0.3f)

            val dxSpalleCentrale = spallaCenterX - bacinoCenterX
            val dzSpalleCentrale = spallaCenterZ - bacinoCenterZ
            val dxGamba = liftedAnkle.x() - bacinoCenterX
            val dzGamba = liftedAnkle.z() - bacinoCenterZ
            isBustoIndietroLocked = (dxSpalleCentrale * dxGamba) + (dzSpalleCentrale * dzGamba) > 0.0f

            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true

            dxSpalleIniziale = dxSpalle
            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()
        } else {
            // ==========================================
            // 2. ROTAZIONE
            // ==========================================
            if (!isGiroInCorso) {
                if (kotlin.math.abs(dxSpalle - dxSpalleIniziale) > scalaCorpo * 0.15f) {
                    isGiroInCorso = true
                }
            }

            val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()
            if (liftedAnkle.y() > ginocchioSupportoY) {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    resetGiro()
                    return null
                }
            } else {
                timestampGambaGiu = 0L
            }

            val isKneeAncoraSu = liftedKnee.y() < bacinoY + (scalaCorpo * 0.8f)
            if (isKneeAncoraSu) {
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
        // 3. MOTORE HYSTERESIS X-SWAP
        // ==========================================
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
                isGiroInCorso = true
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

        if (!isGiroInCorso) return null
        LucchettoGiri.blocca(idGiroFisso)

        val giriCompleti = mezziGiriCompletati / 2

        val valoreBaseDifficolta = if (isBustoOrizzontaleLocked && isBustoIndietroLocked) {
            0.50
        } else if (isBustoOrizzontaleLocked && !isBustoIndietroLocked) {
            0.40
        } else {
            0.30
        }

        val valoreTotale = if (mezziGiriCompletati >= 2) valoreBaseDifficolta + ((giriCompleti - 1) * 0.10) else 0.0

        val nomeBase = if (isAttitudeLocked) {
            if (isBustoOrizzontaleLocked && isBustoIndietroLocked) "BOUCLE S. AIUTO" else "ATTITUDE"
        } else {
            if (isBustoOrizzontaleLocked && isBustoIndietroLocked) "ARABESQUE BUSTO IND." else "ARABESQUE"
        }

        val etichettaBusto = if (isBustoOrizzontaleLocked && !isBustoIndietroLocked) "(Busto Av./Lat.)" else ""
        val etichettaFinale = "GIRO $nomeBase $etichettaBusto"

        nome = if (giriCompleti >= 1) {
            "$etichettaFinale ($giriCompleti Giri)"
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
        isAttitudeLocked = false
        isBustoOrizzontaleLocked = false
        isBustoIndietroLocked = false
        gambaDiSupportoIsLeft = null
        dxSpalleIniziale = 0f
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
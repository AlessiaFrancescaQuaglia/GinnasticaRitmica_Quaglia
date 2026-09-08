package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class GiroDegage : Difficolta {
    override var nome = "PIVOT DÉGAGÉ"

    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false
        private var isFormaBloccata = false
        private var isGambaTesaLocked = false
        private var timestampGambaGiu = 0L

        private var dxSpalleIniziale = 0f
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

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
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoCenterZ = (leftHip.z() + rightHip.z()) / 2f
        val spallaCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val spallaCenterZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee

        val leftKneeAngle3D = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle3D = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle3D else leftKneeAngle3D

        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        // ==========================================
        // 1. VERIFICA POSA E AGGANCIO FORMA
        // ==========================================
        if (!isFormaBloccata) {

            if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.0f)) return null
            if (spallaY > bacinoY + (scalaCorpo * 0.1f)) return null

            val midShoulder = NormalizedLandmark.create(spallaCenterX, spallaY, spallaCenterZ)
            val midHip = NormalizedLandmark.create(bacinoCenterX, bacinoY, bacinoCenterZ)
            val angleSpineThigh3D = PoseAnalyzer.calculateAngle3D(midShoulder, midHip, liftedKnee)
            val angleSpineThigh2D = calculateAngle2D(midShoulder, midHip, liftedKnee)
            val angleSpineThigh = kotlin.math.min(angleSpineThigh2D, angleSpineThigh3D)

            if (angleSpineThigh > 140.0) return null

            val isGambaAlta = liftedAnkle.y() < bacinoY + (scalaCorpo * 0.35f)
            val isAngoloAperto = liftedKneeAngle >= 100.0
            val distPiedeGinocchio = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)
            val isNonPasse = distPiedeGinocchio > (scalaCorpo * 0.4f)

            if (!(isGambaAlta && isAngoloAperto && isNonPasse)) return null

            // REGOLE MORBIDE PER GAMBA TESA (>= 135.0)
            isGambaTesaLocked = liftedKneeAngle >= 135.0
            isFormaBloccata = true

            dxSpalleIniziale = dxSpalle
            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()

        } else {
            // ==========================================
            // 2. FASE DI ROTAZIONE E MEMORIA MUSCOLARE
            // ==========================================

            // LA MEMORIA MUSCOLARE: Se in qualsiasi momento del giro la gamba si stende (>= 135.0),
            // viene marchiata a fuoco come GAMBA TESA per sempre! Non torna più flessa.
            if (liftedKneeAngle >= 135.0) {
                isGambaTesaLocked = true
            }

            if (!isGiroInCorso) {
                if (kotlin.math.abs(dxSpalle - dxSpalleIniziale) > scalaCorpo * 0.15f) {
                    isGiroInCorso = true
                }
            }

            // Timeout prolungato a 1.2s per consentire i movimenti di Plié nei Fouetté
            if (liftedAnkle.y() > supportKnee.y()) {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else {
                    val limiteCaduta = if (isGiroInCorso) 1200L else 800L
                    if (System.currentTimeMillis() - timestampGambaGiu > limiteCaduta) {
                        resetGiro()
                        return null
                    }
                }
            } else {
                timestampGambaGiu = 0L
            }

            val isBustoCrollato = spallaY > bacinoY + (scalaCorpo * 0.2f)
            if (isBustoCrollato) {
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
        // 3. MOTORE HYSTERESIS X-SWAP
        // ==========================================
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
                isGiroInCorso = true
            } else if (statoFronteRetro != 0
                && statoAttualeSpalle != 0
                && statoAttualeSpalle != statoFronteRetro) {

                val tempoDalUltimoSwap = System.currentTimeMillis()-timestampUltimoSwap
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

        // ==========================================
        // 4. GESTIONE LUCCHETTI ED EQUILIBRI
        // ==========================================
        if (!isGiroInCorso) {
            return null
        }

        LucchettoGiri.blocca(idGiroFisso)

        val giriCompletati = mezziGiriCompletati / 2
        val valoreBaseDifficolta = if (isGambaTesaLocked) 0.30 else 0.10
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0
        val etichettaGiro = if (isGambaTesaLocked) "GAMBA TESA (0.30)" else "GAMBA FLESSA (0.10)"

        nome = if (giriCompletati >= 1) {
            "PIVOT DÉGAGÉ $etichettaGiro ($giriCompletati Giri)"
        } else {
            "PIVOT DÉGAGÉ $etichettaGiro (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    private fun calculateAngle2D(p1: NormalizedLandmark, p2: NormalizedLandmark, p3: NormalizedLandmark): Double {
        val rad = kotlin.math.atan2((p3.y() - p2.y()).toDouble(), (p3.x() - p2.x()).toDouble()) -
                kotlin.math.atan2((p1.y() - p2.y()).toDouble(), (p1.x() - p2.x()).toDouble())
        var deg = Math.toDegrees(rad)
        if (deg < 0) deg += 360.0
        if (deg > 180.0) deg = 360.0 - deg
        return deg
    }

    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        isGambaTesaLocked = false
        dxSpalleIniziale = 0f
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
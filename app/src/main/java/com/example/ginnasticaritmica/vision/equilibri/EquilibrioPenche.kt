package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

class EquilibrioPenche : Difficolta {
    override var nome = "GRAND ÉCART IND. (Penché)"

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
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        if (LucchettoGiri.giroAttivo != null) return null

        // ==========================================
        // 0. SCUDO DIREZIONALE ANTI-DÉGAGÉ!
        // ==========================================
        val supportHeel = if (isLeftLowest) landmarks[29] else landmarks[30]
        val supportToe = if (isLeftLowest) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Un Penché prevede la gamba sollevata ALL'INDIETRO. Se è avanti, lascialo al Dégagé!
        val isLegBackward = if (isFacingRight) liftedAnkle.x() < bacinoCenterX + (scalaCorpo * 0.1f) else liftedAnkle.x() > bacinoCenterX - (scalaCorpo * 0.1f)
        if (!isLegBackward) {
            return null
        }

        // ==========================================
        // 1. FIREWALL VERTICALITÀ (Flessibile)
        // ==========================================
        if (supportAnkle.y() < bacinoY + 0.15f) return null
        if (liftedAnkle.y() > bacinoY + 0.10f) return null
        if (liftedKnee.y() > bacinoY + 0.10f) return null

        // ==========================================
        // AGGIUNTA CHIRURGICA: SCUDO ANTI-CAMBRÉ
        // ==========================================
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val isLeaningBackward = if (isFacingRight) {
            shoulderCenterX < bacinoCenterX - 0.05f
        } else {
            shoulderCenterX > bacinoCenterX + 0.05f
        }
        if (isLeaningBackward) return null

        // ==========================================
        // 2. FIREWALL BUSTO (Anti-Arabesque)
        // ==========================================
        val isBustoEretto = spallaY < bacinoY - 0.10f
        val isFlessioneDorsale = isBustoEretto && liftedAnkle.y() < spallaY + 0.20f

        if (isBustoEretto && !isFlessioneDorsale) {
            return null // Busto alto senza inarcamento -> Arabesque
        }

        // ==========================================
        // 3. CALCOLO IBRIDO SPACCATA (Anti-Illusione 3D)
        // ==========================================
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
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)

        val aperturaSpaccata = max(angoloSpaccata2D, angoloSpaccata3D)
        val isBoucle = liftedKneeAngle < 100.0

        if (!isBoucle && aperturaSpaccata < 135.0) {
            return null
        }

        // ==========================================
        // 4. VERIFICA RELEVÉ E PRESE
        // ==========================================
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isLeftLowest)

        // ==========================================
        // 5. ASSEGNAZIONE VALORI UFFICIALI FIG
        // ==========================================
        var valoreBase = 0.0
        if (isBoucle) {
            nome = if (conAiuto) "EQILIBRIO PENCHÉ CON BOUCLE (con aiuto)" else " EQUILIBRIO PENCHÉ CON BOUCLE (libero)"
            valoreBase = if (conAiuto) 0.50 else 0.60
        } else {
            val isBustoProfondo = spallaY > bacinoY + 0.10f

            if (isFlessioneDorsale) {
                nome = if (conAiuto) "EQUILIBRIO PENCHÉ (Flessione dorsale, aiuto)" else "EQUILIBRIO PENCHÉ (Flessione dorsale)"
                valoreBase = if (conAiuto) 0.50 else 0.60
            } else if (!isBustoProfondo && conAiuto) {
                nome = "EQUILIBRIO PENCHÉ (Orizzontale con aiuto)"
                valoreBase = 0.40
            } else if (!isBustoProfondo && !conAiuto) {
                nome = "EQUILIBRIO PENCHÉ (Orizzontale libero)"
                valoreBase = 0.60
            } else if (isBustoProfondo && conAiuto) {
                nome = "EQUILIBRIO PENCHÉ (Sotto l'orizz. con aiuto)"
                valoreBase = 0.50
            } else {
                nome = "EQUILIBRIO PENCHÉ (Sotto l'orizz. libero)"
                valoreBase = 0.60
            }
        }

        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (isBoucle) 0.0 else (if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata)

        if (!isBoucle) {
            if (deficitAmpiezza <= 15.0) penalita = 0.0
            else if (deficitAmpiezza in 15.1..30.0) penalita = 0.10
            else if (deficitAmpiezza in 30.1..50.0) penalita = 0.30
            else {
                penalita = 0.50
                isValida = false
                msgErrore = "Apertura insufficiente"
            }
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
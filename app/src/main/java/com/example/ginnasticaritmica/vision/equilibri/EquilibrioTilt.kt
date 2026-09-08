package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

class EquilibrioTilt : Difficolta {
    override var nome = "GRAND ÉCART LATERALE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // SCUDO ANTI-ROTAZIONE
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        // ==========================================
        // FIREWALL 0: ANTI-PENCHÉ (Controllo Faccia / Frontale)
        // ==========================================
        // Nel Tilt (frontale), la faccia è rivolta verso la telecamera e il naso
        // si trova all'interno o vicino alla proiezione orizzontale delle spalle.
        // Nel Penché (profilo), il naso è spostato tutto a un lato fuori dalle spalle.
        val minShoulderX = kotlin.math.min(leftShoulder.x(), rightShoulder.x())
        val maxShoulderX = kotlin.math.max(leftShoulder.x(), rightShoulder.x())
        val margin = (maxShoulderX - minShoulderX) * 0.4f

        val isFacingFront = nose.x() >= (minShoulderX - margin) && nose.x() <= (maxShoulderX + margin)
        if (!isFacingFront) {
            return null // È di profilo -> Lascialo al Penché!
        }
        // ==========================================

        // ==========================================
        // FIREWALL 1: ANTI-VOLO INTELLIGENTE
        // ==========================================
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val altezzaTronco = abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val lowestAnkleY = if (isLeftLowest) leftAnkle.y() else rightAnkle.y()
        val lowestKneeAngle = if (isLeftLowest) leftKneeAngle else rightKneeAngle

        if (lowestKneeAngle < 135.0) return null
        if (lowestAnkleY < bacinoY + (altezzaTronco * 0.7f)) return null
        // ==========================================

        // ==========================================
        // FIREWALL 2: ANTI-COSACCO (Requisito Spaccata)
        // ==========================================
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 120.0
        val isRightTucked = rightKneeAngle < 120.0

        if ((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked)) {
            return null
        }
        // ==========================================

        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isAppoggioLeft) leftAnkle else rightAnkle
        val liftedAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle

        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val midHipLandmark = NormalizedLandmark.create(midHipX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)

        // 1. Apertura Spaccata 3D
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 115.0) return null

        // 2. Controllo Aiuto 3D
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        // 3. Calcolo Flessione Laterale (Tilt)
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val dx = shoulderCenterX - midHipX
        val dy = bacinoY - spallaY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        var valoreBase = 0.0
        if (gradiBusto in 50.0..130.0) {
            if (conAiuto) {
                nome = "EQUILIBRIO GRAND ÉCART LAT. (Tilt, con aiuto)"
                valoreBase = 0.40
            } else {
                nome = "EQUILIBRIO GRAND ÉCART LAT. (Tilt, senza aiuto)"
                valoreBase = 0.60
            }
        } else {
            nome = if (conAiuto) "EQUILIBRIO GRAND ÉCART LATERALE (Con aiuto)" else "EQUILIBRIO GRAND ÉCART LATERALE (Senza aiuto)"
            valoreBase = if (conAiuto) 0.30 else 0.50
        }

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) valoreBase -= 0.10

        // 4. Penalità (Oversplit protetto)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata
        if (deficitAmpiezza <= 10.0) penalita = 0.0
        else if (deficitAmpiezza in 10.1..25.0) penalita = 0.10
        else if (deficitAmpiezza in 25.1..45.0) penalita = 0.30
        else { penalita = 0.50; isValida = false; msgErrore = "Apertura laterale insufficiente" }

        if (valoreBase <= 0.0) { isValida = false; if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
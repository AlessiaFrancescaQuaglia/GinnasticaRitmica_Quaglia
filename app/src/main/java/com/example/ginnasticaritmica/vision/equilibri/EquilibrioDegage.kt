package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioDegage : Difficolta {
    override val nome = "DÉGAGÉ"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // ==========================================
        // FIREWALL ANTI-VOLO (UNIVERSALE PER EQUILIBRI)
        // ==========================================
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        if (!isLeftPlanted && !isRightPlanted) {
            return null // Non è un equilibrio, spegni la classe e lascia fare ai salti!
        }
        // ==========================================

        // Ginocchia tese in 3D (Usiamo i calcoli già fatti dal Firewall)
        if (leftKneeAngle < 130.0 || rightKneeAngle < 130.0) return null

        // Troviamo quale gamba è sollevata (coordinata Y minore = più in alto)
        val isLeftLifted = leftAnkle.y() < rightAnkle.y()
        val isAppoggioLeft = !isLeftLifted

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) leftHip else rightHip
        val ginocchio = if (isLeftLifted) leftKnee else rightKnee
        val caviglia = if (isLeftLifted) leftAnkle else rightAnkle
        val supportHip = if (isAppoggioLeft) leftHip else rightHip

        val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)

        // Direzione Infallibile Tallone-Punta
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        val isBackward = if (isFacingRight) caviglia.x() < supportHip.x() else caviglia.x() > supportHip.x()
        if (isBackward) return null // Se è indietro, lascia spazio all'Arabesque!

        // Se la gamba è dritta in basso (sta in piedi normale), scartiamo la posa
        if (altezzaGamba > 130.0) return null

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20
        if (!inReleve) valoreBase -= 0.10

        // La tensione del ginocchio è l'angolo della gamba sollevata
        val tensioneGinocchio = if (isLeftLifted) leftKneeAngle else rightKneeAngle

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (altezzaGamba <= 95.0) penalita += 0.0
        else if (altezzaGamba in 95.1..105.0) penalita += 0.10
        else if (altezzaGamba in 105.1..120.0) penalita += 0.30
        else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa " }

        if (isValida) {
            if (tensioneGinocchio >= 170.0) penalita += 0.0
            else if (tensioneGinocchio in 160.0..169.9) penalita += 0.10
            else if (tensioneGinocchio in 150.0..159.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio piegato " }
        }

        if (valoreBase <= 0.0) { isValida = false; msgErrore += "(In Pianta)" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
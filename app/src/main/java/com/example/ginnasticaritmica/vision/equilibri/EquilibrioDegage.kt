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

        // Ginocchia tese in 3D
        val leftKnee = PoseAnalyzer.calculateAngle3D(landmarks[23], landmarks[25], landmarks[27])
        val rightKnee = PoseAnalyzer.calculateAngle3D(landmarks[24], landmarks[26], landmarks[28])
        if (leftKnee < 130.0 || rightKnee < 130.0) return null

        // Troviamo quale gamba è sollevata (coordinata Y minore = più in alto)
        val isLeftLifted = landmarks[27].y() < landmarks[28].y()
        val isAppoggioLeft = !isLeftLifted

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) landmarks[23] else landmarks[24]
        val ginocchio = if (isLeftLifted) landmarks[25] else landmarks[26]
        val caviglia = if (isLeftLifted) landmarks[27] else landmarks[28]
        val supportHip = if (isAppoggioLeft) landmarks[23] else landmarks[24]

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

        val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(anca, ginocchio, caviglia)

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
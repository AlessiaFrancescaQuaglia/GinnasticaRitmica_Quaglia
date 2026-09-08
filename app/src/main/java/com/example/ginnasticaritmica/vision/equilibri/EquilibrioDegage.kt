package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

class EquilibrioDegage : Difficolta {
    override val nome = "EQUILIBRIO DÉGAGÉ"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

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
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        // ==========================================
        // 1. FIREWALL BUSTO RILASSATO
        // ==========================================
        // Rilassato a 0.20f per permettere bilanciamento
        if (spallaY > bacinoY - (scalaCorpo * 0.20f)) {
            return null
        }

        // ==========================================
        // 2. FIREWALL ANTI-VOLO
        // ==========================================
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY < bacinoY + (scalaCorpo * 0.9f)) {
            return null // In volo
        }

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isLeftLifted = leftAnkle.y() < rightAnkle.y()
        val isAppoggioLeft = !isLeftLifted

        // ==========================================
        // 3. CONTROLLO MORBIDO GINOCCHIA
        // ==========================================
        val angoloAppoggio = if (isAppoggioLeft) leftKneeAngle else rightKneeAngle
        val tensioneGinocchio = if (isLeftLifted) leftKneeAngle else rightKneeAngle

        // Non scartiamo più tutto a 130! Scartiamo solo se è PALESEMENTE un Attitude o un salto (es. < 135)
        if (angoloAppoggio < 135.0) return null
        if (tensioneGinocchio < 135.0) return null // Sotto i 135 è un Attitude, non Dégagé.

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) leftHip else rightHip
        val ginocchio = if (isLeftLifted) leftKnee else rightKnee
        val caviglia = if (isLeftLifted) leftAnkle else rightAnkle
        val supportHip = if (isAppoggioLeft) leftHip else rightHip

        val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)

        // ==========================================
        // 4. FIREWALL DIREZIONALE
        // ==========================================
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Nel Dégagé la gamba è avanti o lato, NON dietro!
        val isBackward = if (isFacingRight) caviglia.x() < supportHip.x() - (scalaCorpo * 0.3f) else caviglia.x() > supportHip.x() + (scalaCorpo * 0.3f)
        if (isBackward) return null

        // Se in piedi
        if (altezzaGamba > 130.0) return null

        // ==========================================
        // 5. RESTITUZIONE PUNTEGGIO E PENALITÀ
        // ==========================================
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20
        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Penalità altezza gamba invariate
        if (altezzaGamba <= 95.0) penalita += 0.0
        else if (altezzaGamba in 95.1..105.0) penalita += 0.10
        else if (altezzaGamba in 105.1..120.0) penalita += 0.30
        else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa " }

        // Penalità ginocchio RILASSATE:
        // 160+ Perfetto | 145-159 Lieve | 135-144 Grave | <135 Fallito
        if (isValida) {
            if (tensioneGinocchio >= 160.0) penalita += 0.0
            else if (tensioneGinocchio in 145.0..159.9) penalita += 0.10
            else if (tensioneGinocchio in 135.0..144.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio troppo piegato " }
        }

        if (valoreBase <= 0.0) { isValida = false; msgErrore += "(In Pianta)" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
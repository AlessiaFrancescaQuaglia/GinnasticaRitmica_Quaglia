package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri // Assicurati di importarlo in alto!

class EquilibrioPasse : Difficolta {
    override val nome = "EQUILIBRIO PASSÉ"

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


        if (landmarks.size <= 32) return null

        // SCUDO ANTI-ROTAZIONE: Se la ginnasta sta girando, non può essere un equilibrio!
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }


        if (!isLeftPlanted && !isRightPlanted) {
            return null // Non è un equilibrio, spegni la classe e lascia fare ai salti!
        }
        // ==========================================

        // Se entrambe le ginocchia sono tese, non può essere un Passé
        if (leftKneeAngle > 120.0 && rightKneeAngle > 120.0) return null

        val isLeftBent = leftKneeAngle < rightKneeAngle
        val isAppoggioLeft = !isLeftBent

        val supportAngle = if (isAppoggioLeft) leftKneeAngle else rightKneeAngle
        if (supportAngle < 160.0) return null

        // --- FIREWALL ALLARGATO (Da 0.18f a 0.25f) ---
        // Diamo più respiro a MediaPipe per capire dove si trova il piede!
        val puntaPiegata = if (isLeftBent) landmarks[31] else landmarks[32]
        val ginocchioTerra = if (isAppoggioLeft) leftKnee else rightKnee
        val distanzaPuntaGinocchio = PoseAnalyzer.calculateDistance3D(puntaPiegata, ginocchioTerra)

        if (distanzaPuntaGinocchio > 0.25f) {
            return null
        }
        // ----------------------------------------------

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)

        val valoreBase = 0.10
        var penalita = 0.0

        // Se non è in relevé diamo una penalità, ma NON azzeriamo il valoreBase
        // così l'app lo stampa comunque a schermo facendoti vedere che lo ha riconosciuto!
        if (!inReleve) {
            penalita += 0.10
        }

        val spallaPiegata = if (isLeftBent) landmarks[11] else landmarks[12]
        val ancaPiegata = if (isLeftBent) leftHip else rightHip
        val ginocchioPiegato = if (isLeftBent) leftKnee else rightKnee

        val altezzaGinocchio = PoseAnalyzer.calculateAngle3D(spallaPiegata, ancaPiegata, ginocchioPiegato)

        var isValida = true
        var msgErrore = ""

        if (altezzaGinocchio <= 100.0) penalita += 0.0
        else if (altezzaGinocchio in 100.1..110.0) penalita += 0.10
        else if (altezzaGinocchio in 110.1..125.0) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Ginocchio troppo basso"
        }

        // Se è in pianta ma la posa è corretta, mostriamo l'errore senza nascondere l'equilibrio
        if (!inReleve && isValida) {
            msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, valoreBase, penalita, inReleve, msgErrore)
    }
}
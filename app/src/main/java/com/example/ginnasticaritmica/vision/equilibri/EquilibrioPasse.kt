package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioPasse : Difficolta {
    override val nome = "PASSÉ"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val angleLeft = PoseAnalyzer.calculateAngle3D(landmarks[23], landmarks[25], landmarks[27])
        val angleRight = PoseAnalyzer.calculateAngle3D(landmarks[24], landmarks[26], landmarks[28])

        if (angleLeft > 120.0 && angleRight > 120.0) return null

        val isLeftBent = angleLeft < angleRight
        val isAppoggioLeft = !isLeftBent

        val supportAngle = if (isAppoggioLeft) angleLeft else angleRight
        if (supportAngle < 160.0) return null

        // --- FIREWALL ALLARGATO (Da 0.18f a 0.25f) ---
        // Diamo più respiro a MediaPipe per capire dove si trova il piede!
        val puntaPiegata = if (isLeftBent) landmarks[31] else landmarks[32]
        val ginocchioTerra = if (isAppoggioLeft) landmarks[25] else landmarks[26]
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
        val ancaPiegata = if (isLeftBent) landmarks[23] else landmarks[24]
        val ginocchioPiegato = if (isLeftBent) landmarks[25] else landmarks[26]

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
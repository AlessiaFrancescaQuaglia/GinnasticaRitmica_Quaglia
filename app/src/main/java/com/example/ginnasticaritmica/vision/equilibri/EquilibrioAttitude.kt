package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioAttitude : Difficolta {
    override var nome = "ATTITUDE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. Identificazione gamba sollevata (Quella con la Y minore è più in alto)
        val isLeftLifted = landmarks[27].y() < landmarks[28].y()
        val isAppoggioLeft = !isLeftLifted

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) landmarks[23] else landmarks[24]
        val ginocchioSollevato = if (isLeftLifted) landmarks[25] else landmarks[26]
        val cavigliaSollevata = if (isLeftLifted) landmarks[27] else landmarks[28]
        val puntaLibera = if (isLeftLifted) landmarks[31] else landmarks[32] // Punta del piede

        val ancaAppoggio = if (isAppoggioLeft) landmarks[23] else landmarks[24]
        val ginocchioAppoggio = if (isAppoggioLeft) landmarks[25] else landmarks[26]
        val cavigliaAppoggio = if (isAppoggioLeft) landmarks[27] else landmarks[28]

        // --- 2. FILTRO ANTI-PASSÉ (Il Firewall) ---
        // Se la punta del piede sollevato è attaccata al ginocchio di terra, NON è un'Attitude!
        val distanzaPuntaGinocchio = PoseAnalyzer.calculateDistance3D(puntaLibera, ginocchioAppoggio)
        if (distanzaPuntaGinocchio < 0.20f) {
            return null // Blocchiamo tutto: se ne occuperà la classe EquilibrioPasse!
        }

        // 3. Controllo Ginocchio di Appoggio (Deve essere teso)
        val angoloAppoggio = PoseAnalyzer.calculateAngle3D(ancaAppoggio, ginocchioAppoggio, cavigliaAppoggio)
        if (angoloAppoggio < 150.0) return null

        // 4. LA REGOLA DELL'ATTITUDE: Il ginocchio sollevato DEVE essere piegato!
        val angoloSollevato = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchioSollevato) // Altezza
        val flessioneGinocchio = PoseAnalyzer.calculateAngle3D(anca, ginocchioSollevato, cavigliaSollevata)

        // Se la gamba è tesa (>140°), è un'Arabesque o un Dégagé. Scartiamo.
        if (flessioneGinocchio > 140.0) return null

        // Se il ginocchio è troppo basso (sta solo camminando), scartiamo.
        if (angoloSollevato > 120.0) return null

        // 5. Direzione (Avanti, Lato, Indietro)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Per il nome, guardiamo se il ginocchio va in avanti o indietro
        val isBackward = if (isFacingRight) ginocchioSollevato.x() < ancaAppoggio.x() else ginocchioSollevato.x() > ancaAppoggio.x()

        nome = if (isBackward) "ATTITUDE INDIETRO" else "ATTITUDE AVANTI/LATO"

        // 6. Controllo Relevé
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20 // Valore base classico per l'Attitude
        if (!inReleve) valoreBase -= 0.10

        // 7. Calcolo Penalità sull'altezza del ginocchio
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (angoloSollevato <= 95.0) penalita = 0.0
        else if (angoloSollevato in 95.1..105.0) penalita = 0.10
        else if (angoloSollevato in 105.1..115.0) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Ginocchio troppo basso"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "(In Pianta)"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
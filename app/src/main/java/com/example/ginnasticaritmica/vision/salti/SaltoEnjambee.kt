package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoEnjambee : Difficolta {
    override var nome = "SALTO ENJAMBÉE"

    // --- SENSORE DI ROTAZIONE (Memoria a breve termine) ---
    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // =======================================================
        // FASE 1: TRACCIAMENTO DELLA ROTAZIONE (En Tournant)
        // =======================================================
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()

        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            // Cambio di fronte! La ginnasta sta ruotando.
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // =======================================================
        // FASE 2: RILEVAMENTO DELLA FORMA (Spaccata con gamba tesa)
        // =======================================================
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]
        val leftHeel = landmarks[29]
        val rightHeel = landmarks[30]
        val isFacingRight = (leftToe.x() + rightToe.x()) > (leftHeel.x() + rightHeel.x())

        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) landmarks[23] else landmarks[24]
        val frontKnee = if (isLeftFront) landmarks[25] else landmarks[26]
        val frontAnkle = if (isLeftFront) landmarks[27] else landmarks[28]

        val backHip = if (isLeftFront) landmarks[24] else landmarks[23]
        val backKnee = if (isLeftFront) landmarks[26] else landmarks[25]
        val backAnkle = if (isLeftFront) landmarks[28] else landmarks[27]
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        // FIREWALL: La gamba avanti DEVE essere tesa
        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle < 150.0) return null

        // AMPIEZZA SPACCATA
        val centroBacinoX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val centroBacinoY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)

        val angoloSpaccata = PoseAnalyzer.calculateAngle3D(frontAnkle, centroBacino, backAnkle)

        if (angoloSpaccata < 130.0) return null

        // =======================================================
        // FASE 3: ASSEGNAZIONE PUNTEGGI (Dritto o En Tournant?)
        // =======================================================
        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L // Sensore attivo se ha girato negli ultimi 1.5s

        var valoreBase = 0.30
        val nose = landmarks[0]
        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val isTorsoBentBack = if (isFacingRight) spallaCentroX < centroBacinoX - 0.1f else spallaCentroX > centroBacinoX + 0.1f

        if (backKneeAngle <= 130.0 && distanzaTestaPiede < 0.35f) {
            // Raccogliamo qui sia il Jeté en Tournant Boucle (0.60) che l'Agiurgiuculese (0.50)
            nome = if (isEnTournant) "JETÉ TOURNANT BOUCLE / AGIURG." else "ENJAMBÉE CON BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (backKneeAngle > 140.0 && (isTorsoBentBack || distanzaTestaPiede < 0.40f)) {
            nome = if (isEnTournant) "JETÉ EN TOURNANT + FLESSIONE" else "ENJAMBÉE CON FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else {
            // Salto base o Jeté En Tournant puro (1.3504)
            nome = if (isEnTournant) "JETÉ EN TOURNANT" else "SALTO ENJAMBÉE"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

        // =======================================================
        // FASE 4: CALCOLO PENALITÀ SPACCATA
        // =======================================================
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (angoloSpaccata >= 170.0) penalita += 0.0
        else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
        else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Apertura spaccata insufficiente"
        }

        // Se valido, azzeriamo il sensore di rotazione
        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCarpiato : Difficolta {
    override val nome = "SALTO CARPIATO"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. Entrambe le gambe DEVONO essere tese
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(landmarks[23], landmarks[25], landmarks[27])
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(landmarks[24], landmarks[26], landmarks[28])

        if (leftKneeAngle < 150.0 || rightKneeAngle < 150.0) return null // Gambe piegate = scartato

        // 2. Le gambe devono essere sollevate orizzontalmente in avanti
        val spallaDx = landmarks[12]
        val ancaDx = landmarks[24]
        val ginocchioDx = landmarks[26]

        val altezzaGambe = PoseAnalyzer.calculateAngle3D(spallaDx, ancaDx, ginocchioDx)
        if (altezzaGambe > 120.0) return null // Gambe puntate a terra

        // 3. Flessione del Busto e Tocco Mani-Piedi
        val polsoSx = landmarks[15]
        val polsoDx = landmarks[16]
        val puntaSx = landmarks[31]
        val puntaDx = landmarks[32]

        val distPolsoPuntaSx = PoseAnalyzer.calculateDistance3D(polsoSx, puntaSx)
        val distPolsoPuntaDx = PoseAnalyzer.calculateDistance3D(polsoDx, puntaDx)

        // Se le mani sono lontane dai piedi, il busto non è chiuso a carpiato.
        if (distPolsoPuntaSx > 0.35f && distPolsoPuntaDx > 0.35f) return null

        // 4. Valutazione e Penalità (Da Codice dei Punteggi)
        var valoreBase = 0.30 // Valore da tabella per il Carpiato
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Valutiamo l'altezza delle gambe rispetto al bacino (deviazioni di forma)
        if (altezzaGambe <= 90.0) {
            penalita += 0.0 // Gambe perfette all'orizzontale o più su
        } else if (altezzaGambe in 90.1..105.0) {
            penalita += 0.10 // Piccola deviazione
        } else if (altezzaGambe in 105.1..115.0) {
            penalita += 0.30 // Media deviazione
        } else {
            penalita += 0.50
            isValida = false
            msgErrore = "Gambe troppo basse"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
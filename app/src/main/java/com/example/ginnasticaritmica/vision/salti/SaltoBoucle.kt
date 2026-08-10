package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoBoucle : Difficolta {
    override var nome = "SALTO BOUCLE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val nose = landmarks[0]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]

        val distLeft = PoseAnalyzer.calculateDistance3D(nose, leftToe)
        val distRight = PoseAnalyzer.calculateDistance3D(nose, rightToe)

        // 1. IL FIREWALL: Almeno un piede deve essere vicino alla testa (< 0.35f)
        if (distLeft > 0.35f && distRight > 0.35f) return null

        // 2. Controllo Altezza (Garantisce che l'atleta sia in volo e non stia facendo un equilibrio a terra)
        val spallaY = (landmarks[11].y() + landmarks[12].y()) / 2f
        // Se entrambi i piedi sono molto più bassi delle spalle, scartiamo. L'anello deve tirare su i piedi.
        if (leftAnkle.y() > spallaY + 0.2f && rightAnkle.y() > spallaY + 0.2f) return null

        // 3. CAPIRNE IL TIPO: 1 o 2 Gambe
        // Se la distanza tra le due caviglie è piccola (sono unite) E sono entrambe piegate verso la testa
        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)
        var valoreBase = 0.0

        if (distLeft < 0.35f && distRight < 0.35f && distanzaCaviglie < 0.15f) {
            nome = "SALTO BOUCLE (2 Gambe)"
            valoreBase = 0.30
        } else {
            nome = "SALTO BOUCLE (1 Gamba)"
            valoreBase = 0.20
        }

        // 4. PENALITÀ ESECUZIONE (Chiusura dell'anello)
        // Prendiamo la distanza del piede più vicino alla testa
        val bestDist = kotlin.math.min(distLeft, distRight)

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (bestDist <= 0.20f) {
            penalita += 0.0 // Anello perfettamente chiuso
        } else if (bestDist in 0.201f..0.28f) {
            penalita += 0.10
        } else if (bestDist in 0.281f..0.35f) {
            penalita += 0.30
        } else {
            // Questo blocco teoricamente non viene mai raggiunto grazie al firewall iniziale,
            // ma lo mettiamo per sicurezza architetturale.
            penalita += 0.50
            isValida = false
            msgErrore = "Anello non chiuso"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
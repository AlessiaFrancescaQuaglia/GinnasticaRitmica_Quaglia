package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class SaltoButterfly : Difficolta {
    override var nome = "SALTO BUTTERFLY"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        // ==========================================
        // FIREWALL DEFINITIVO ANTI-EQUILIBRIO (Anatomico)
        // ==========================================
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val altezzaTronco = kotlin.math.abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Una gamba è piantata a terra se:
        // 1. Scende sotto il bacino di una misura proporzionale al busto (tracciamento perfetto)
        // OPPURE
        // 2. Scende anche di poco, ma il ginocchio è dritto a sorreggere il peso (tracciamento rotto/schermo piatto)

        val isLeftPlanted = (leftAnkle.y() > bacinoY + (altezzaTronco * 0.6f)) ||
                (leftAnkle.y() > bacinoY + 0.05f && leftKneeAngle > 150.0)

        val isRightPlanted = (rightAnkle.y() > bacinoY + (altezzaTronco * 0.6f)) ||
                (rightAnkle.y() > bacinoY + 0.05f && rightKneeAngle > 150.0)

        if (isLeftPlanted || isRightPlanted) {
            return null // Piede a terra! Spegni immediatamente la classe dei salti.
        }
        // ==========================================
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

        // 1. FIREWALL GAMBE: Devono essere dritte
        if (leftKneeAngle < 140.0 || rightKneeAngle < 140.0) return null

        // 2. FIREWALL ALTEZZA: È un salto in volo
        val centroBacinoY = (leftHip.y() + rightHip.y()) / 2f
        // In MediaPipe Y è 0 in alto e 1 in basso. Se le caviglie sono sotto al bacino, non è un Butterfly.
        if (leftAnkle.y() > centroBacinoY + 0.1f || rightAnkle.y() > centroBacinoY + 0.1f) return null

        // 3. CALCOLO BUSTO: Il busto deve essere abbassato (flesso in avanti orizzontalmente)
        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f
        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f

        val dx = spallaCentroX - centroBacinoX
        val dy = centroBacinoY - spallaCentroY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        // Accettiamo un'inclinazione che va dai 50° ai 130° (intorno ai 90° è orizzontale)
        if (gradiBusto !in 50.0..130.0) return null

        // 4. CALCOLO APERTURA (Divaricata)
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)
        val angoloSpaccata = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)

        if (angoloSpaccata < 120.0) return null // Niente spaccata/divaricata

        // 5. ASSEGNAZIONE PUNTEGGIO E PENALITÀ
        val valoreBase = 0.50 // Codice 1.3705
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Penalità per deviazioni dalla spaccata frontale perfetta
        if (angoloSpaccata >= 170.0) penalita += 0.0
        else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
        else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Apertura divaricata insufficiente"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
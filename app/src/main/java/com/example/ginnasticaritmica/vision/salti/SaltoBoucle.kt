package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoBoucle : Difficolta {
    override var nome = "SALTO BOUCLE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. SCUDO ANTI-GIRO: Se un giro è in corso, non è un salto!
        if (com.example.ginnasticaritmica.vision.giri.LucchettoGiri.giroAttivo != null) {
            return null
        }
        val nose = landmarks[0]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Usiamo la lunghezza del femore come unità di misura invariabile (immune agli inarcamenti)
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Una gamba è piantata a terra SOLO se scende in profondità (più del femore)
        // ED è sufficientemente tesa per sostenere il peso (angolo > 135°).
        // Nel Biche la gamba scende ma è piegata a 90°, quindi il Firewall la lascerà passare!
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Sicurezza extra: se una caviglia è paurosamente in basso (vicino al fondo dello schermo)
        // e lontana dal bacino, spegniamo i salti a prescindere dall'angolo.
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null // Piede a terra confermato, passa agli equilibri!
        }



        // ==========================================
        // FIREWALL ANTI-BICHE (Protegge il Biche Boucle)
        // ==========================================
        // Troviamo la gamba anteriore usando l'asse X assoluto rispetto al bacino!
        // La gamba anteriore è quella il cui ginocchio o caviglia va più "avanti" rispetto al centro.
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val nasoX = landmarks[0].x()

        val isFacingRight = nasoX > bacinoCenterX
        val isLeftFront = if (isFacingRight) leftKnee.x() > rightKnee.x() else leftKnee.x() < rightKnee.x()

        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)

        // Se la gamba avanti è piegata a biche (tra 35° e 120°), non è un Boucle semplice!
        if (frontKneeAngle in 35.0..120.0) {
            return null
        }
        // ==========================================


        // ==========================================
        // FIREWALL 1: FORMA DEL GINOCCHIO
        // ==========================================
        val isLeftHigh = leftAnkle.y() < bacinoY - 0.1f
        val isRightHigh = rightAnkle.y() < bacinoY - 0.1f

        if (!isLeftHigh && !isRightHigh) return null

        var hasValidBoucleLeg = false
        if (isLeftHigh && leftKneeAngle < 135.0) hasValidBoucleLeg = true
        if (isRightHigh && rightKneeAngle < 135.0) hasValidBoucleLeg = true

        if (!hasValidBoucleLeg) return null

        // ==========================================
        // FIREWALL 2: ANTI-SPACCATA
        // ==========================================
        val centroBacino = NormalizedLandmark.create(
            (leftHip.x() + rightHip.x()) / 2f,
            (leftHip.y() + rightHip.y()) / 2f,
            0f
        )
        val angoloCosce = PoseAnalyzer.calculateAngle3D(leftKnee, centroBacino, rightKnee)

        if (angoloCosce > 110.0) return null

        // ==========================================
        // VALUTAZIONE DEL SALTO (Distanza Rigida 0.28f)
        // ==========================================
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]
        val distLeft = PoseAnalyzer.calculateDistance3D(nose, leftToe)
        val distRight = PoseAnalyzer.calculateDistance3D(nose, rightToe)

        // Se la testa è lontana dal piede, non è un boucle (Spegnimento totale)
        if (distLeft > 0.28f && distRight > 0.28f) return null

        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)
        var valoreBase = 0.0

        if (distLeft < 0.28f && distRight < 0.28f && distanzaCaviglie < 0.20f) {
            nome = "SALTO BOUCLE (2 Gambe)"
            valoreBase = 0.30
        } else {
            nome = "SALTO BOUCLE (1 Gamba)"
            valoreBase = 0.20
        }

        val bestDist = kotlin.math.min(distLeft, distRight)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (bestDist <= 0.15f) penalita += 0.0
        else if (bestDist in 0.151f..0.20f) penalita += 0.10
        else if (bestDist in 0.201f..0.25f) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Anello non chiuso"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
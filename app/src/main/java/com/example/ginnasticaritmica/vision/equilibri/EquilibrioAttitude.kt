package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

class EquilibrioAttitude : Difficolta {
    override var nome = "ATTITUDE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // SCUDO ANTI-ROTAZIONE
        if (LucchettoGiri.giroAttivo != null) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        // ==========================================
        // 1. FIREWALL ANTI-VOLO
        // ==========================================
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY < bacinoY + (scalaCorpo * 0.9f)) return null

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        if (!isLeftPlanted && !isRightPlanted) return null

        // ==========================================
        // 2. IDENTIFICAZIONE GAMBA E DIREZIONE
        // ==========================================
        val isLeftLifted = leftAnkle.y() < rightAnkle.y()
        val isAppoggioLeft = !isLeftLifted

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) leftHip else rightHip
        val ginocchioSollevato = if (isLeftLifted) leftKnee else rightKnee
        val cavigliaSollevata = if (isLeftLifted) leftAnkle else rightAnkle
        val puntaLibera = if (isLeftLifted) landmarks[31] else landmarks[32]

        val ancaAppoggio = if (isAppoggioLeft) leftHip else rightHip
        val ginocchioAppoggio = if (isAppoggioLeft) leftKnee else rightKnee

        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        val isBackward = if (isFacingRight) ginocchioSollevato.x() < ancaAppoggio.x() else ginocchioSollevato.x() > ancaAppoggio.x()

        // ==========================================
        // 3. FIREWALL ANTI-PENCHÉ E BUSTO
        // ==========================================
        // Più permissivo: scatta solo se il busto è molto schiacciato in giù
        val isBustoFlesso = spallaY > bacinoY - (scalaCorpo * 0.15f)
        val isFlessioneDorsale = isBustoFlesso && cavigliaSollevata.y() < spallaY + (scalaCorpo * 0.3f)

        if (isBustoFlesso && isBackward && !isFlessioneDorsale) {
            return null // Questo è un Arabesque o un Penché!
        }

        // ==========================================
        // 4. FILTRO ANTI-PASSÉ INTELLIGENTE
        // ==========================================
        val distanzaPuntaGinocchio = PoseAnalyzer.calculateDistance3D(puntaLibera, ginocchioAppoggio)

        // IL SEGRETO: Se la gamba è indietro, NON PUÒ essere un Passé.
        // Lo scartiamo solo se è avanti/lato E il piede tocca il ginocchio!
        if (!isBackward && distanzaPuntaGinocchio < (scalaCorpo * 0.4f)) {
            return null
        }

        // 5. Controllo Ginocchio di Appoggio
        val angoloAppoggio = PoseAnalyzer.calculateAngle3D(ancaAppoggio, ginocchioAppoggio, if (isAppoggioLeft) leftAnkle else rightAnkle)
        if (angoloAppoggio < 140.0) return null

        // ==========================================
        // 6. CALCOLO GINOCCHIO SOLLEVATO E LIMITI
        // ==========================================
        val angoloSollevato = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchioSollevato)

        val dxAnca = anca.x() - ginocchioSollevato.x()
        val dyAnca = anca.y() - ginocchioSollevato.y()
        val dxCav = cavigliaSollevata.x() - ginocchioSollevato.x()
        val dyCav = cavigliaSollevata.y() - ginocchioSollevato.y()

        val dotGinocchio = (dxAnca * dxCav) + (dyAnca * dyCav)
        val magAnca = kotlin.math.sqrt((dxAnca * dxAnca + dyAnca * dyAnca).toDouble())
        val magCav = kotlin.math.sqrt((dxCav * dxCav + dyCav * dyCav).toDouble())
        var flessioneGinocchio2D = Math.toDegrees(kotlin.math.acos(dotGinocchio / (magAnca * magCav)))
        if (flessioneGinocchio2D.isNaN()) flessioneGinocchio2D = 0.0

        val flessioneGinocchio3D = PoseAnalyzer.calculateAngle3D(anca, ginocchioSollevato, cavigliaSollevata)
        val flessioneGinocchio = kotlin.math.max(flessioneGinocchio2D, flessioneGinocchio3D)

        // Un Attitude deve essere flesso, se è sopra 155 è una gamba tesa (Arabesque)
        if (flessioneGinocchio > 155.0) return null

        // Se la gamba cade totalmente giù
        if (angoloSollevato > 145.0) return null

        // ==========================================
        // 7. RESTITUZIONE PUNTEGGIO
        // ==========================================
        nome = if (isBackward) "EQUILIBRIO ATTITUDE INDIETRO" else "EQUILIBRIO ATTITUDE AVANTI/LATO"

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20
        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Penalità ricalibrate per le proiezioni 2D
        if (angoloSollevato <= 100.0) penalita = 0.0
        else if (angoloSollevato in 100.1..115.0) penalita = 0.10
        else if (angoloSollevato in 115.1..130.0) penalita = 0.30
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

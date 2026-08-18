package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class EquilibrioArabesque : Difficolta {
    override var nome = "ARABESQUE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // ==========================================
        // 1. FIREWALL ANTI-VOLO (UNIVERSALE PER EQUILIBRI)
        // ==========================================
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        if (!isLeftPlanted && !isRightPlanted) {
            return null
        }

        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val spalla = if (isAppoggioLeft) landmarks[12] else landmarks[11]
        val anca = if (isAppoggioLeft) rightHip else leftHip
        val ginocchio = if (isAppoggioLeft) rightKnee else leftKnee
        val caviglia = if (isAppoggioLeft) rightAnkle else leftAnkle

        val supportHip = if (isAppoggioLeft) leftHip else rightHip
        val supportKnee = if (isAppoggioLeft) leftKnee else rightKnee
        val supportAnkle = if (isAppoggioLeft) leftAnkle else rightAnkle

        // ==========================================
        // 2. FIREWALL ANTI-FALSO POSITIVO (In piedi)
        // ==========================================
        // In un Arabesque la gamba DEVE essere sollevata.
        // Se la caviglia libera è più bassa del ginocchio d'appoggio, l'atleta è palesemente in piedi.
        if (caviglia.y() > supportKnee.y()) return null

        // Controllo base sulle ginocchia in 3D (Entrambe devono essere tendenzialmente tese)
        if (leftKneeAngle < 130.0 || rightKneeAngle < 130.0) return null

        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // ==========================================
        // 3. ANALISI BUSTO E DIREZIONE
        // ==========================================
        // La gamba sollevata deve essere estesa all'indietro
        val isBackwardExtended = if (isFacingRight) caviglia.x() < supportHip.x() else caviglia.x() > supportHip.x()
        if (!isBackwardExtended) return null

        val shoulderCenterX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderCenterY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipCenterX = (leftHip.x() + rightHip.x()) / 2f

        val dx = shoulderCenterX - hipCenterX
        val dy = bacinoY - shoulderCenterY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        val isTorsoForward = if (isFacingRight) dx > 0 else dx < 0
        val isTorsoBackward = if (isFacingRight) dx < 0 else dx > 0

        val nose = landmarks[0]
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, caviglia)
        val isTouching = distanzaTestaPiede < 0.25f

        var valoreBase = 0.20
        var isPlanche = false

        // ==========================================
        // 4. CLASSIFICAZIONE E PUNTEGGI
        // ==========================================
        if (isTorsoForward && gradiBusto in 55.0..130.0) {
            nome = "PLANCHE"
            valoreBase = 0.30
            isPlanche = true
        } else if (isTorsoBackward && isTouching) {
            nome = "ARABESQUE (Flessione indietro con contatto)"
            valoreBase = 0.40
        } else if (isTorsoBackward && gradiBusto in 70.0..110.0) {
            nome = "ARABESQUE (Flesso indietro orizz.)"
            valoreBase = 0.30
        } else if (isTorsoForward) {
            return null // Posa transitoria o non chiara
        } else {
            nome = "ARABESQUE"
            valoreBase = 0.20
        }

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            valoreBase -= 0.10
        }

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (isPlanche) {
            val cadutaGambaY = caviglia.y() - anca.y()

            if (cadutaGambaY <= 0.02f) {
                penalita += 0.0
            } else if (cadutaGambaY in 0.021f..0.06f) {
                penalita += 0.10
            } else if (cadutaGambaY in 0.061f..0.12f) {
                penalita += 0.30
            } else {
                penalita += 0.50
                isValida = false
                msgErrore = "Gamba troppo bassa (Sotto l'orizzontale)"
            }
        } else {
            val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)
            if (altezzaGamba <= 100.0) penalita += 0.0
            else if (altezzaGamba in 100.1..115.0) penalita += 0.10
            else if (altezzaGamba in 115.1..130.0) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa" }
        }

        val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(anca, ginocchio, caviglia)
        if (isValida) {
            if (tensioneGinocchio >= 165.0) penalita += 0.0
            else if (tensioneGinocchio in 155.0..164.9) penalita += 0.10
            else if (tensioneGinocchio in 140.0..154.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio piegato" }
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta (Valore nullo)"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCosacco : Difficolta {
    override var nome = "SALTO COSACCO"

    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val nose = landmarks[0]

        val bacinoX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (spallaSx.y() + spallaDx.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // ==========================================
        // FIREWALL ANTI-EQUILIBRIO
        // ==========================================
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null
        }

        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // ==========================================
        // IDENTIFICAZIONE GAMBE (Cosacco = 1 tesa, 1 piegata)
        // MAGLIE ALLARGATE PER LA VELOCITÀ DI VOLO
        // ==========================================
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 135.0
        val isRightTucked = rightKneeAngle < 135.0

        if (!((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked))) {
            return null
        }

        // ==========================================
        // FIREWALL ANTI-ENJAMBÉE / ANTI-BOUCLE
        // ==========================================
        val isFacingRight = nose.x() > bacinoX
        val tuckedKnee = if (isLeftStraight) rightKnee else leftKnee
        val straightKnee = if (isLeftStraight) leftKnee else rightKnee

        // In un salto sagittale (Enjambée), la gamba tesa va avanti e la gamba piegata va indietro (Boucle).
        // In un Cosacco, ENTRAMBE le ginocchia devono stare davanti (o sotto) al bacino!
        val isTuckedBackward = if (isFacingRight) tuckedKnee.x() < bacinoX else tuckedKnee.x() > bacinoX
        val isStraightForward = if (isFacingRight) straightKnee.x() > bacinoX else straightKnee.x() < bacinoX

        if (isTuckedBackward && isStraightForward) {
            return null // È un salto con spaccata sagittale! Spegni il Cosacco e cedi il posto all'Enjambée.
        }
        // ==========================================

        val straightAnkle = if (isLeftStraight) leftAnkle else rightAnkle
        val straightToe = if (isLeftStraight) landmarks[31] else landmarks[32]

        if (straightAnkle.y() > bacinoY + 0.15f) return null

        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val distTestaPiede = PoseAnalyzer.calculateDistance3D(nose, straightToe)
        val isGambaAlta = straightAnkle.y() < spallaY + 0.1f || distTestaPiede < 0.30f

        val distLeftWrist = PoseAnalyzer.calculateDistance3D(leftWrist, straightAnkle)
        val distRightWrist = PoseAnalyzer.calculateDistance3D(rightWrist, straightAnkle)
        val minWristDist = kotlin.math.min(distLeftWrist, distRightWrist)

        val isConPresa = isGambaAlta && minWristDist <= 0.18f

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        var valoreBase = 0.0

        if (isConPresa) {
            nome = if (isEnTournant) "COSACCO TOURNANT CON PRESA" else "COSACCO CON PRESA"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else if (isGambaAlta) {
            nome = if (isEnTournant) "COSACCO TOURNANT SENZA PRESA" else "COSACCO ALTO SENZA PRESA"
            valoreBase = if (isEnTournant) 0.50 else 0.40
        } else {
            nome = if (isEnTournant) "COSACCO EN TOURNANT" else "SALTO COSACCO CLASSICO"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (straightAnkle.y() < spallaY + 0.1f) {
            penalita += 0.0
        } else if (straightAnkle.y() < bacinoY - 0.05f) {
            penalita += 0.10
        } else if (straightAnkle.y() <= bacinoY + 0.15f) {
            penalita += if (isGambaAlta) 0.30 else 0.10
        } else {
            penalita += 0.50
            isValida = false
            msgErrore = "Gamba avanti caduta/troppo bassa"
        }

        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
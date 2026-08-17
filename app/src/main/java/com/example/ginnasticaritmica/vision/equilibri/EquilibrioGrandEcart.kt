package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class EquilibrioGrandEcart : Difficolta {
    override var nome = "GRAND ÉCART AVANTI"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Mappatura di tutti i landmark necessari
        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // ==========================================
        // FIREWALL 1: ANTI-VOLO INTELLIGENTE
        // ==========================================
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val altezzaTronco = kotlin.math.abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val lowestAnkleY = if (isLeftLowest) leftAnkle.y() else rightAnkle.y()
        val lowestKneeAngle = if (isLeftLowest) leftKneeAngle else rightKneeAngle

        // Se la caviglia scende, MA il ginocchio è piegato (come in un salto cosacco),
        // è fisicamente impossibile che stia sorreggendo un Grand Ecart. È in volo!
        if (lowestKneeAngle < 135.0) {
            return null
        }

        // Se il piede più basso è troppo in alto rispetto al bacino, è in salto
        if (lowestAnkleY < bacinoY + (altezzaTronco * 0.7f)) {
            return null
        }
        // ==========================================

        // ==========================================
        // FIREWALL 2: ANTI-COSACCO (Requisito Spaccata)
        // ==========================================
        // Un "Grand Écart" richiede che ENTRAMBE le gambe siano tese.
        // Se una è tesa e l'altra è rannicchiata, spegniamo l'equilibrio!
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 120.0
        val isRightTucked = rightKneeAngle < 120.0

        if ((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked)) {
            return null
        }
        // ==========================================

        // 1. Identificazione Gamba di Appoggio
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val supportHip = if (isLeftLowest) leftHip else rightHip
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        // 2. DIREZIONE INFALLIBILE (Tallone vs Punta: immune al relevé)
        val supportHeel = if (isLeftLowest) landmarks[29] else landmarks[30]
        val supportToe = if (isLeftLowest) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // 3. CONTROLLO DIREZIONE GAMBA (Il ginocchio DEVE andare avanti)
        val isForwardExtended = if (isFacingRight) liftedKnee.x() > supportHip.x() else liftedKnee.x() < supportHip.x()
        if (!isForwardExtended) return null

        // 4. FIREWALL ANTI-PENCHÉ (Distanza 3D Naso-Caviglia)
        // Se la caviglia è vicinissima al naso (< 30cm in 3D), è un Penché/Anello! Scartiamo.
        val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
        if (distNasoCaviglia < 0.30f) return null

        // 5. Calcolo Inclinazione Busto (Solo flessione indietro)
        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f

        val dx = shoulderCenterX - midHipX
        val dy = bacinoY - spallaY
        val isTorsoBackward = if (isFacingRight) dx < 0 else dx > 0

        var gradiBustoIndietro = 0.0
        if (isTorsoBackward) {
            gradiBustoIndietro = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))
        }

        // 6. Apertura Spaccata Vettoriale in 3D (Calcolo perfetto anche di sbieco)
        val midHipLandmark = NormalizedLandmark.create(midHipX,
            bacinoY, (leftHip.z() + rightHip.z()) / 2f)
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 130.0) return null

        // 7. Controllo Relevé e Aiuto (Mani-Piede in 3D)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isLeftLowest)

        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.12f || distPolsoDx < 0.12f)

        // 8. Assegnazione Dinamica (Codice dei Punteggi)
        var valoreBase = 0.0

        if (gradiBustoIndietro > 110.0) {
            nome = "GRAND ÉCART AVANTI (Sotto orizz.)"
            valoreBase = 0.60
        } else if (gradiBustoIndietro in 70.0..110.0) {
            if (conAiuto) {
                nome = "GRAND ÉCART AVANTI (Busto orizz. con aiuto)"
                valoreBase = 0.40
            } else {
                nome = "GRAND ÉCART AVANTI (Busto orizz. senza aiuto)"
                valoreBase = 0.60
            }
        } else {
            nome = if (conAiuto) "GRAND ÉCART AVANTI (Con aiuto)" else "GRAND ÉCART AVANTI (Senza aiuto)"
            valoreBase = if (conAiuto) 0.30 else 0.50
        }

        if (!inReleve) {
            valoreBase -= 0.10 // Penalità fissa per esecuzione in pianta
        }

        // 9. Calcolo Penalità Esecutive sull'ampiezza (con salvaguardia Oversplit)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata

        if (deficitAmpiezza <= 10.0) penalita = 0.0
        else if (deficitAmpiezza in 10.1..25.0) penalita = 0.10
        else if (deficitAmpiezza in 25.1..45.0) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Apertura insufficiente"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(
            isValida = isValida,
            valoreFinale = if (valoreBase > 0) valoreBase else 0.0,
            penalita = penalita,
            inReleve = inReleve,
            messaggioErrore = msgErrore
        )
    }
}
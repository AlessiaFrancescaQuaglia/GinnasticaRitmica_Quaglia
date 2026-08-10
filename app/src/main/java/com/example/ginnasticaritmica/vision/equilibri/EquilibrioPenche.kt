package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class EquilibrioPenche : Difficolta {
    override var nome = "GRAND ÉCART IND. (Penché)"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // 1. Identificazione Gamba di Appoggio
        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isAppoggioLeft) leftAnkle else rightAnkle
        val liftedAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle
        val supportHip = if (isAppoggioLeft) leftHip else rightHip
        val liftedKnee = if (isAppoggioLeft) rightKnee else leftKnee

        // 2. DIREZIONE INFALLIBILE (Tallone vs Punta: immune al relevé)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // 3. CONTROLLO GAMBA INDIETRO & FIREWALL 3D
        val isKneeBack = if (isFacingRight) liftedKnee.x() < supportHip.x() else liftedKnee.x() > supportHip.x()
        val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)

        // Se il ginocchio sta andando in avanti lo scartiamo. MA se il piede in 3D è attaccato alla testa (< 25cm),
        // è un Penché o un anello estremo, quindi lo accettiamo forzatamente!
        if (!isKneeBack && distNasoCaviglia > 0.25f) return null

        // 4. SENSORE PIEDE SOPRA LA TESTA (Marchio di fabbrica)
        if (liftedAnkle.y() > nose.y() + 0.15f) return null

        // 5. Calcolo Inclinazione Busto (Avanti)
        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val midHipY = (leftHip.y() + rightHip.y()) / 2f
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val shoulderCenterY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val dx = shoulderCenterX - midHipX
        val dy = midHipY - shoulderCenterY
        val isTorsoForward = if (isFacingRight) dx > 0 else dx < 0

        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))
        val bustoOrizzontale = isTorsoForward && (gradiBusto in 60.0..120.0)

        // 6. Apertura Spaccata Vettoriale in 3D
        val midHipLandmark = NormalizedLandmark.create(midHipX, midHipY, (leftHip.z() + rightHip.z()) / 2f)
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 115.0) return null

        // 7. Controllo Aiuto (Mani-Piede in 3D) e Relevé
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.12f || distPolsoDx < 0.12f)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)

        // 8. Assegnazione Valori
        var valoreBase = 0.0
        if (bustoOrizzontale && conAiuto) {
            nome = "PENCHÉ (Busto avanti, aiuto)"
            valoreBase = 0.40
        } else if (bustoOrizzontale && !conAiuto) {
            nome = if (gradiBusto > 110.0) "PENCHÉ (Busto sotto orizz., no aiuto)" else "PENCHÉ (Busto avanti orizz., no aiuto)"
            valoreBase = 0.60
        } else if (conAiuto) {
            nome = "PENCHÉ (Piede su testa, aiuto)"
            valoreBase = 0.30
        } else {
            nome = "PENCHÉ (Piede su testa, senza aiuto)"
            valoreBase = 0.50
        }

        if (!inReleve) valoreBase -= 0.10

        // 9. Penalità (Oversplit protetto)
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

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
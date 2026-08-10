package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioCosacco : Difficolta {
    override var nome = "COSACCO"

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

        // 1. Identifichiamo la gamba in appoggio e quella libera tramite Angoli 3D
        // Usiamo il 3D per non farci ingannare dalla prospettiva quando è accovacciata
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isAppoggioLeft: Boolean
        if (leftKneeAngle < 90.0 && rightKneeAngle > 140.0) {
            isAppoggioLeft = true
        } else if (rightKneeAngle < 90.0 && leftKneeAngle > 140.0) {
            isAppoggioLeft = false
        } else {
            return null // Non c'è una gamba in accosciata e l'altra tesa
        }

        val supportHip = if (isAppoggioLeft) leftHip else rightHip
        val freeAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle
        val freeKnee = if (isAppoggioLeft) rightKnee else leftKnee
        val freeHip = if (isAppoggioLeft) rightHip else leftHip

        // 2. Controllo Direzione Infallibile (Tallone vs Punta)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        val isForwardExtended = if (isFacingRight) freeAnkle.x() > supportHip.x() else freeAnkle.x() < supportHip.x()
        if (!isForwardExtended) return null

        // 3. Controllo "Con Aiuto" (Mani che tengono il piede calcolate in 3D)
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], freeAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], freeAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        // 4. Assegnazione Valore (Items 2.1301 e 2.1402)
        var valoreBase = 0.0

        // Verifica se il piede è più alto della testa (Y minore del naso)
        val isPiedeAlto = freeAnkle.y() < nose.y() + 0.05f

        if (isPiedeAlto && conAiuto) {
            nome = "COSACCO (Piede alto, aiuto)" // 2.1402
            valoreBase = 0.20
        } else {
            nome = "COSACCO (Gamba avanti orizz.)" // 2.1301
            valoreBase = 0.10
        }

        // 5. Controllo Relevé (Sul piede in accosciata)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) valoreBase -= 0.10

        // 6. Penalità esecutive sulla gamba libera misurata in 3D (così non penalizza se vista di sbieco)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val freeLegTension = PoseAnalyzer.calculateAngle3D(freeHip, freeKnee, freeAnkle)
        if (freeLegTension >= 170.0) penalita = 0.0
        else if (freeLegTension in 160.0..169.9) penalita = 0.10
        else if (freeLegTension in 150.0..159.9) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Gamba libera piegata"
        }

        // Penalità di altezza se la gamba cade troppo in basso
        if (!isPiedeAlto && freeAnkle.y() > supportHip.y() + 0.15f) {
            isValida = false
            msgErrore = "Gamba sotto l'orizzontale"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
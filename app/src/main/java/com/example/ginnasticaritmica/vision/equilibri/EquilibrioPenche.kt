package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioPenche : Difficolta {
    override var nome = "GRAND ÉCART IND. (Penché)"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        // 1. Il piede portante deve essere a terra
        if (supportAnkle.y() < bacinoY + 0.20f) return null

        // 2. FIREWALL BUSTO: Riconosce la flessione dorsale (schiena inarcata all'indietro come nella foto)
        val isFlessioneDorsale = spallaY < bacinoY - 0.05f && liftedAnkle.y() < spallaY

        if (spallaY < bacinoY - 0.05f) {
            // Se le spalle sono alte, lascia passare la posa SOLO se il piede libero è più in alto della testa
            if (!isFlessioneDorsale) {
                return null
            }
        }

        // 3. La gamba sollevata DEVE superare in altezza il bacino
        if (liftedAnkle.y() > bacinoY && liftedKnee.y() > bacinoY) return null

        val isBoucle = liftedKneeAngle < 135.0

        val midHipLandmark = NormalizedLandmark.create(bacinoCenterX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)

        if (!isBoucle && aperturaSpaccata < 95.0) return null

        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isLeftLowest)

        // ==========================================
        // AGGIORNAMENTO VALORI UFFICIALI (Come da tabella)
        // ==========================================
        var valoreBase = 0.0
        if (isBoucle) {
            nome = if (conAiuto) "PENCHÉ CON BOUCLE (con aiuto)" else "PENCHÉ CON BOUCLE (libero)"
            // Aggiornato a 0.60 per la versione libera (Rif. 2.1106.3)
            valoreBase = if (conAiuto) 0.50 else 0.60
        } else {
            val isBustoProfondo = spallaY > bacinoY + 0.10f

            if (isFlessioneDorsale) {
                nome = if (conAiuto) "PENCHÉ (Flessione dorsale, con aiuto)" else "PENCHÉ (Flessione dorsale)"
                valoreBase = if (conAiuto) 0.50 else 0.60
            } else if (!isBustoProfondo && conAiuto) {
                nome = "PENCHÉ (Orizzontale con aiuto)"
                valoreBase = 0.40
            } else if (!isBustoProfondo && !conAiuto) {
                // Aggiornato da 0.50 a 0.60 (Rif. 2.1106.1)
                nome = "PENCHÉ (Orizzontale libero)"
                valoreBase = 0.60
            } else if (isBustoProfondo && conAiuto) {
                nome = "PENCHÉ (Sotto l'orizz. con aiuto)"
                valoreBase = 0.50
            } else {
                // Già corretto a 0.60 (Rif. 2.1106.2)
                nome = "PENCHÉ (Sotto l'orizz. libero)"
                valoreBase = 0.60
            }
        }

        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (isBoucle) 0.0 else (if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata)

        if (!isBoucle) {
            if (deficitAmpiezza <= 15.0) penalita = 0.0
            else if (deficitAmpiezza in 15.1..30.0) penalita = 0.10
            else if (deficitAmpiezza in 30.1..50.0) penalita = 0.30
            else {
                penalita = 0.50
                isValida = false
                msgErrore = "Apertura insufficiente"
            }
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
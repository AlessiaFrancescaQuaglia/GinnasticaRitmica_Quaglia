package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class EquilibrioTilt : Difficolta {
    override var nome = "GRAND ÉCART LATERALE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isAppoggioLeft) leftAnkle else rightAnkle
        val liftedAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle

        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val midHipY = (leftHip.y() + rightHip.y()) / 2f
        val midHipLandmark = NormalizedLandmark.create(midHipX, midHipY, (leftHip.z() + rightHip.z()) / 2f)

        // 1. Apertura Spaccata 3D
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 115.0) return null

        // 2. Controllo orientamento frontale (il tuo filtro spalle)
        val shoulderWidth = abs(leftShoulder.x() - rightShoulder.x())
        if (shoulderWidth < 0.05) return null // È girata di profilo

        // 3. Controllo Aiuto 3D
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f) // Tolleranza leggermente alzata per il 3D

        // 4. Calcolo Flessione Laterale (Tilt)
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val shoulderCenterY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val dx = shoulderCenterX - midHipX
        val dy = midHipY - shoulderCenterY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        var valoreBase = 0.0
        if (gradiBusto in 50.0..130.0) {
            if (conAiuto) {
                nome = "GRAND ÉCART LAT. (Tilt, con aiuto)" // 2.604
                valoreBase = 0.40
            } else {
                nome = "GRAND ÉCART LAT. (Tilt, senza aiuto)" // 2.806
                valoreBase = 0.60
            }
        } else {
            nome = if (conAiuto) "GRAND ÉCART LATERALE (Con aiuto)" else "GRAND ÉCART LATERALE (Senza aiuto)"
            valoreBase = if (conAiuto) 0.30 else 0.50
        }

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) valoreBase -= 0.10

        // 5. Penalità (Oversplit protetto)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata
        if (deficitAmpiezza <= 10.0) penalita = 0.0
        else if (deficitAmpiezza in 10.1..25.0) penalita = 0.10
        else if (deficitAmpiezza in 25.1..45.0) penalita = 0.30
        else { penalita = 0.50; isValida = false; msgErrore = "Apertura laterale insufficiente" }

        if (valoreBase <= 0.0) { isValida = false; if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
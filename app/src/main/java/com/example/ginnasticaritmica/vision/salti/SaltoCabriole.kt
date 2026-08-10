package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

class SaltoCabriole : Difficolta {
    override var nome = "CABRIOLE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val nose = landmarks[0]

        // 1. Controllo di base: le ginocchia devono essere tese durante il salto
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        if (leftKneeAngle < 140.0 || rightKneeAngle < 140.0) return null

        // 2. Direzione del corpo (Tallone vs Punta o Naso/Spalle)
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val isFacingRight = nose.x() > shoulderCenterX

        // 3. Riconoscimento della battuta (Nel Cabriole le caviglie si avvicinano molto o si incrociano in volo)
        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)

        // Se le caviglie sono molto vicine (battuta in aria < 12cm in 3D)
        val isBattutaInAria = distanzaCaviglie < 0.05f

        // Distinguiamo la direzione del salto in base alla posizione delle gambe rispetto al bacino
        val isAvanti = if (isFacingRight) rightAnkle.x() > rightHip.x() else rightAnkle.x() < rightHip.x()
        val isIndietro = if (isFacingRight) rightAnkle.x() < rightHip.x() else rightAnkle.x() > rightHip.x()

        // Assegnazione del nome specifico in base al Codice dei Punteggi (Valore 0.10)
        var valoreBase = 0.10
        if (isAvanti) {
            nome = "CABRIOLE AVANTI (1.901)"
        } else if (isIndietro) {
            nome = "CABRIOLE INDIETRO (1.1101)"
        } else {
            nome = "CABRIOLE LATERALE (1.1001)"
        }

        // Se non c'è evidenza di stacco e chiusura ravvicinata degli arti, scartiamo
        if (!isBattutaInAria) return null

        val isValida = true
        val penalita = 0.0
        val msgErrore = ""

        return RisultatoDifficolta(
            isValida = isValida,
            valoreFinale = valoreBase,
            penalita = penalita,
            inReleve = false,
            messaggioErrore = msgErrore
        )
    }
}
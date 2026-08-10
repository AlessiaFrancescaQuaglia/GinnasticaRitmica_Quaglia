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

        val isAppoggioLeft = landmarks[27].y() > landmarks[28].y()
        val spalla = if (isAppoggioLeft) landmarks[12] else landmarks[11]
        val anca = if (isAppoggioLeft) landmarks[24] else landmarks[23]
        val ginocchio = if (isAppoggioLeft) landmarks[26] else landmarks[25]
        val caviglia = if (isAppoggioLeft) landmarks[28] else landmarks[27]

        val supportHip = if (isAppoggioLeft) landmarks[23] else landmarks[24]
        val supportKnee = if (isAppoggioLeft) landmarks[25] else landmarks[26]
        val supportAnkle = if (isAppoggioLeft) landmarks[27] else landmarks[28]

        // Controllo base sulle ginocchia in 3D (Entrambe devono essere tese)
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(landmarks[23], landmarks[25], landmarks[27])
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(landmarks[24], landmarks[26], landmarks[28])
        if (leftKneeAngle < 130.0 || rightKneeAngle < 130.0) return null

        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // La gamba sollevata deve essere estesa all'indietro
        val isBackwardExtended = if (isFacingRight) caviglia.x() < supportHip.x() else caviglia.x() > supportHip.x()
        if (!isBackwardExtended) return null

        val shoulderCenterX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderCenterY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipCenterX = (landmarks[23].x() + landmarks[24].x()) / 2f
        val hipCenterY = (landmarks[23].y() + landmarks[24].y()) / 2f

        val dx = shoulderCenterX - hipCenterX
        val dy = hipCenterY - shoulderCenterY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        val isTorsoForward = if (isFacingRight) dx > 0 else dx < 0
        val isTorsoBackward = if (isFacingRight) dx < 0 else dx > 0

        val nose = landmarks[0]
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, caviglia)
        val isTouching = distanzaTestaPiede < 0.25f

        var valoreBase = 0.20
        var isPlanche = false

        // --- TOLLERANZA BUSTO PLANCHE ---
        // 90° è perfettamente orizzontale.
        // Ora accettiamo il busto anche se è sollevato a 55° o se scende molto fino a 130°.
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

        // Controllo Relevé (La penalità di 0.10 si toglie dal valore base)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            valoreBase -= 0.10
        }

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (isPlanche) {
            // --- NUOVO SENSORE GRAVITAZIONALE PLANCHE ---
            // Se (caviglia.y - anca.y) è negativo, la gamba è più in alto del bacino (OTTIMO!).
            // Se è positivo, misuriamo di quanto la gamba sta scendendo verso il pavimento.
            val cadutaGambaY = caviglia.y() - anca.y()

            if (cadutaGambaY <= 0.02f) {
                // Gamba altissima o perfettamente orizzontale (tolleranza 2%) -> 0 Penalità!
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
            // ARABESQUE CLASSICA: Valutiamo l'altezza rispetto al busto eretto (calcolo ad angolo)
            val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)
            if (altezzaGamba <= 100.0) penalita += 0.0
            else if (altezzaGamba in 100.1..115.0) penalita += 0.10
            else if (altezzaGamba in 115.1..130.0) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa" }
        }

        // Penalità comune a Planche e Arabesque: Il ginocchio libero deve essere tesissimo
        val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(anca, ginocchio, caviglia)
        if (isValida) {
            // Abbassato leggermente il requisito perfetto da 170° a 165° per non essere esageratamente severi
            if (tensioneGinocchio >= 165.0) penalita += 0.0
            else if (tensioneGinocchio in 155.0..164.9) penalita += 0.10
            else if (tensioneGinocchio in 140.0..154.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio piegato" }
        }

        // Invalida l'equilibrio se il valore è nullo (es. Planche in pianta)
        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta (Valore nullo)"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
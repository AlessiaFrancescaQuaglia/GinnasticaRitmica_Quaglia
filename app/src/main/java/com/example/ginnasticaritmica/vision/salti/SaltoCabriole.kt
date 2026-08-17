package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCabriole : Difficolta {
    override var nome = "CABRIOLE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val nose = landmarks[0]
        // ==========================================
        // FIREWALL DEFINITIVO ANTI-EQUILIBRIO (Anatomico)
        // ==========================================
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val altezzaTronco = kotlin.math.abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Una gamba è piantata a terra se:
        // 1. Scende sotto il bacino di una misura proporzionale al busto (tracciamento perfetto)
        // OPPURE
        // 2. Scende anche di poco, ma il ginocchio è dritto a sorreggere il peso (tracciamento rotto/schermo piatto)

        val isLeftPlanted = (leftAnkle.y() > bacinoY + (altezzaTronco * 0.6f)) ||
                (leftAnkle.y() > bacinoY + 0.05f && leftKneeAngle > 150.0)

        val isRightPlanted = (rightAnkle.y() > bacinoY + (altezzaTronco * 0.6f)) ||
                (rightAnkle.y() > bacinoY + 0.05f && rightKneeAngle > 150.0)

        if (isLeftPlanted || isRightPlanted) {
            return null // Piede a terra! Spegni immediatamente la classe dei salti.
        }
        // ==========================================
        // 1. Controllo di base: le ginocchia devono essere tese durante il salto
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
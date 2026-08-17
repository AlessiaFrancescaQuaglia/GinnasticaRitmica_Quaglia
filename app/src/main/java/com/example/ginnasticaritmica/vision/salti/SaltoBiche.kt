package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoBiche : Difficolta {
    override var nome = "SALTO BICHE"

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

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // ==========================================
        // NUOVO FIREWALL ANTI-EQUILIBRIO (Basato sul Femore)
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
        // ==========================================

        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

        // ==========================================
        // DIREZIONE INFALLIBILE (Basata su X, non su Z)
        // ==========================================
        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val nose = landmarks[0]
        val isFacingRight = nose.x() > centroBacinoX

        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // In un Biche, la gamba davanti è definita dal ginocchio che va più in avanti rispetto all'asse X
        val isLeftFront = if (isFacingRight) leftKnee.x() > rightKnee.x() else leftKnee.x() < rightKnee.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)

        // Requisito fondamentale del Biche: gamba davanti piegata a "cervo"
        if (frontKneeAngle > 115.0 || frontKneeAngle < 35.0) return null

        // ==========================================
        // FIREWALL ANTI-COSACCO (Sviluppo Orizzontale)
        // ==========================================
        // In un salto Biche, la spaccata si sviluppa orizzontalmente (sagittale).
        // La gamba posteriore (backKnee) va ALL'INDIETRO.
        // Se il ginocchio posteriore punta dritto verso il soffitto (è più in alto del bacino),
        // si tratta di un salto a sviluppo verticale (es. Salto Cosacco Alto)!
        if (backKnee.y() < bacinoY - 0.05f) {
            return null // Non è un Biche, spegni la classe e cedi il passo al Cosacco.
        }
        // ==========================================

        val centroBacinoY = bacinoY
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)

        // CALCOLO SPACCATA IBRIDO
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontKnee, centroBacino, backAnkle)

        val v1x = frontKnee.x() - centroBacinoX
        val v1y = frontKnee.y() - centroBacinoY
        val v2x = backAnkle.x() - centroBacinoX
        val v2y = backAnkle.y() - centroBacinoY
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
        val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dotProduct / (mag1 * mag2)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val angoloSpaccata = kotlin.math.max(angoloSpaccata3D, angoloSpaccata2D)

        if (angoloSpaccata < 130.0) return null

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

        // ==========================================
        // SENSORE DI FLESSIONE E BOUCLE (Nuova Logica)
        // ==========================================
        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f
        val spostamentoSpalleDietro = if (isFacingRight) centroBacinoX - spallaCentroX else spallaCentroX - centroBacinoX
        val schiacciamentoTroncoY = bacinoY - spallaCentroY
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isFlessioneEvidente = distNasoBacino < 0.25f ||
                spostamentoSpalleDietro > 0.25f ||
                (schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f)

        // Il Boucle ha la precedenza assoluta. Se il piede tocca la testa, è un anello!
        val isBoucle = backKneeAngle <= 135.0 && distanzaTestaPiede <= 0.25f

        // Flessione base se c'è l'inarcamento ma non c'è l'anello
        val isFlessione = !isBoucle && (isFlessioneEvidente || (backKneeAngle > 135.0 && distanzaTestaPiede <= 0.20f))

        var valoreBase = 0.20

        if (isBoucle) {
            nome = if (isEnTournant) "BICHE 180° (o Tournant) + BOUCLE" else "SALTO BICHE + BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (isFlessione) {
            nome = if (isEnTournant) "BICHE 180° (o Tournant) + FLESS." else "SALTO BICHE + FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else {
            nome = if (isEnTournant) "BICHE ROTAZIONE 180° (o Tournant)" else "SALTO BICHE"
            valoreBase = if (isEnTournant) 0.40 else 0.20
        }

        // PENALITÀ
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (angoloSpaccata >= 170.0) penalita += 0.0
        else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
        else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Apertura spaccata insufficiente"
        }

        if (!nome.contains("BOUCLE") && isValida && backKneeAngle < 150.0 && !isFlessioneEvidente) {
            penalita += 0.30
            if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
        }

        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
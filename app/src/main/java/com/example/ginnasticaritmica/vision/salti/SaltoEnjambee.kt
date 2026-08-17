package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoEnjambee : Difficolta {
    override var nome = "SALTO ENJAMBÉE"

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

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null // Piede a terra confermato, passa agli equilibri!
        }

        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]

        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val isFacingRight = landmarks[0].x() > centroBacinoX

        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        // Tolleranza in volo per la gamba anteriore
        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle < 130.0) return null

        val centroBacinoY = (leftHip.y() + rightHip.y()) / 2f
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)

        // ==========================================
        // CALCOLO SPACCATA (SENSORE OVERSPLIT)
        // ==========================================
        // Usiamo i vettori direzionali della gamba anteriore (tesa) e del femore posteriore
        val vFrontX = frontAnkle.x() - frontHip.x()
        val vFrontY = frontAnkle.y() - frontHip.y()
        val vBackX = backKnee.x() - backHip.x()
        val vBackY = backKnee.y() - backHip.y()

        val dot = (vFrontX * vBackX) + (vFrontY * vBackY)
        val magF = kotlin.math.sqrt((vFrontX * vFrontX + vFrontY * vFrontY).toDouble())
        val magB = kotlin.math.sqrt((vBackX * vBackX + vBackY * vBackY).toDouble())

        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magF * magB)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        // IL SALVATAGGIO DELL'OVERSPLIT: Se la caviglia davanti e il ginocchio dietro
        // vanno in direzioni orizzontali opposte, E sono entrambi altissimi (vicino o sopra il bacino),
        // è matematicamente una spaccata perfetta o in oversplit, ignoriamo l'angolo a "V"!
        val isOppositeHorizontal = (vFrontX > 0 && vBackX < 0) || (vFrontX < 0 && vBackX > 0)
        if (isOppositeHorizontal) {
            if (frontAnkle.y() <= bacinoY + 0.05f && backKnee.y() <= bacinoY + 0.05f) {
                angoloSpaccata2D = 180.0 // Oversplit perfetto! 0 Penalità.
            } else if (frontAnkle.y() <= bacinoY + 0.15f && backKnee.y() <= bacinoY + 0.15f) {
                angoloSpaccata2D = kotlin.math.max(angoloSpaccata2D, 165.0) // Ottimo salto.
            }
        }

        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontAnkle, centroBacino, backKnee)
        val angoloSpaccata = kotlin.math.max(angoloSpaccata2D, angoloSpaccata3D)

        if (angoloSpaccata < 130.0) return null

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        var valoreBase = 0.30
        val nose = landmarks[0]
        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

        // ==========================================
        // SENSORE DI FLESSIONE E BOUCLE
        // ==========================================
        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f
        val spostamentoSpalleDietro = if (isFacingRight) centroBacinoX - spallaCentroX else spallaCentroX - centroBacinoX

        val schiacciamentoTroncoY = bacinoY - spallaCentroY
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isFlessioneEvidente = distNasoBacino < 0.25f ||
                spostamentoSpalleDietro > 0.25f ||
                (schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f)

        val isBoucle = backKneeAngle <= 135.0 && distanzaTestaPiede <= 0.25f

        val isFlessione = !isBoucle && (isFlessioneEvidente || (backKneeAngle > 135.0 && distanzaTestaPiede <= 0.20f))

        if (isBoucle) {
            nome = if (isEnTournant) "JETÉ TOURNANT BOUCLE / AGIURG." else "ENJAMBÉE CON BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (isFlessione) {
            nome = if (isEnTournant) "JETÉ EN TOURNANT + FLESSIONE" else "ENJAMBÉE CON FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else {
            nome = if (isEnTournant) "JETÉ EN TOURNANT" else "SALTO ENJAMBÉE"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

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

        if (!nome.contains("BOUCLE") && isValida && !isFlessioneEvidente) {
            if (backKneeAngle < 150.0) {
                penalita += 0.30
                if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
            }
        }

        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
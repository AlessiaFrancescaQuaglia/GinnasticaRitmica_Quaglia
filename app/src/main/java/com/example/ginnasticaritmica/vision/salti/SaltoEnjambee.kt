package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

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
        val bacinoX = (leftHip.x() + rightHip.x()) / 2f

        // ==========================================
        // 1. FIREWALL ANTI-EQUILIBRIO (FILTRO PILASTRO)
        // ==========================================
        // Calcoliamo se la gamba è un "Pilastro" (perfettamente verticale).
        // Se la caviglia è esattamente sotto l'anca (distanza X minima, distanza Y ampia),
        // la ginnasta è palesemente in appoggio sul pavimento (In piedi o in Penché).
        val dxLeft = abs(leftAnkle.x() - leftHip.x())
        val dyLeft = leftAnkle.y() - leftHip.y()
        val isLeftPillar = dyLeft > 0.20f && dxLeft < 0.20f

        val dxRight = abs(rightAnkle.x() - rightHip.x())
        val dyRight = rightAnkle.y() - rightHip.y()
        val isRightPillar = dyRight > 0.20f && dxRight < 0.20f

        if (isLeftPillar || isRightPillar) {
            return null // Blocca il salto: c'è un piede piantato a terra a fare da pilastro!
        }

        // ==========================================
        // 2. ANALISI DIREZIONE E TOURNANT
        // ==========================================
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f

        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        val isFacingRight = landmarks[0].x() > bacinoX
        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle < 130.0) return null

        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)

        // ==========================================
        // 3. CALCOLO SPACCATA
        // ==========================================
        val vFrontX = frontAnkle.x() - frontHip.x()
        val vFrontY = frontAnkle.y() - frontHip.y()
        val vBackX = backKnee.x() - backHip.x()
        val vBackY = backKnee.y() - backHip.y()

        val dot = (vFrontX * vBackX) + (vFrontY * vBackY)
        val magF = kotlin.math.sqrt((vFrontX * vFrontX + vFrontY * vFrontY).toDouble())
        val magB = kotlin.math.sqrt((vBackX * vBackX + vBackY * vBackY).toDouble())

        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magF * magB)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val isOppositeHorizontal = (vFrontX > 0 && vBackX < 0) || (vFrontX < 0 && vBackX > 0)
        if (isOppositeHorizontal) {
            if (frontAnkle.y() <= bacinoY + 0.05f && backKnee.y() <= bacinoY + 0.05f) {
                angoloSpaccata2D = 180.0
            } else if (frontAnkle.y() <= bacinoY + 0.15f && backKnee.y() <= bacinoY + 0.15f) {
                angoloSpaccata2D = kotlin.math.max(angoloSpaccata2D, 165.0)
            }
        }

        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontAnkle, centroBacino, backKnee)
        val angoloSpaccata = kotlin.math.max(angoloSpaccata2D, angoloSpaccata3D)

        if (angoloSpaccata < 130.0) return null

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        // ==========================================
        // 4. CLASSIFICAZIONE (BOUCLE / FLESSIONI)
        // ==========================================
        var valoreBase = 0.30
        val nose = landmarks[0]
        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spostamentoSpalleDietro = if (isFacingRight) bacinoX - spallaCentroX else spallaCentroX - bacinoX

        val schiacciamentoTroncoY = bacinoY - spallaCentroY
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isFlessioneEvidente = distNasoBacino < 0.25f ||
                spostamentoSpalleDietro > 0.25f ||
                (schiacciamentoTroncoY > 0.0f && schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f) ||
                (schiacciamentoTroncoY < 0.0f)

        val isBoucle = backKneeAngle <= 135.0 && distanzaTestaPiede <= 0.25f

        val isFlessione = !isBoucle && (isFlessioneEvidente || (backKneeAngle > 135.0 && distanzaTestaPiede <= 0.20f))

        if (isBoucle) {
            nome = if (isEnTournant) "SALTO JETÉ TOURNANT BOUCLE / AGIURG." else "SALTO ENJAMBÉE CON BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (isFlessione) {
            nome = if (isEnTournant) "SALTO JETÉ EN TOURNANT + FLESSIONE" else "SALTO ENJAMBÉE CON FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else {
            nome = if (isEnTournant) "SALTO JETÉ EN TOURNANT" else "SALTO ENJAMBÉE"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

        // ==========================================
        // 5. PENALITÀ E RESTITUZIONE
        // ==========================================
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
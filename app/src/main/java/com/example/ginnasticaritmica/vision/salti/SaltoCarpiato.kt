package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCarpiato : Difficolta {
    override var nome = "SALTO CARPIATO"

    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. SCUDO ANTI-GIRO: Se un giro è in corso, non è un salto!
        if (com.example.ginnasticaritmica.vision.giri.LucchettoGiri.giroAttivo != null) {
            return null
        }
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

        // ==========================================
        // FIREWALL ANTI-EQUILIBRIO (Basato sul Femore)
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
        // FIREWALL ANTI-ENJAMBÉE E PROSPETTIVA
        // ==========================================
        val distSpalleX = kotlin.math.abs(landmarks[11].x() - landmarks[12].x())
        val distCaviglieX = kotlin.math.abs(leftAnkle.x() - rightAnkle.x())

        // 1. Se la ginnasta è vista di PROFILO (spalle quasi sovrapposte su asse X),
        // è impossibile valutare un carpiato. Sicuramente è un salto sagittale (Enjambée).
        if (distSpalleX < 0.08f) {
            return null
        }

        // 2. Se è vista di FRONTE, ma le caviglie non sono sufficientemente aperte di lato
        // (sono larghe appena quanto le spalle), significa che una gamba va avanti e una indietro!
        if (distCaviglieX < distSpalleX * 1.2f) {
            return null
        }
        // ==========================================

        // ==========================================
        // REQUISITI CARPIATO (Maglie Flessibili)
        // ==========================================
        if (leftKneeAngle < 120.0 || rightKneeAngle < 120.0) return null

        if (leftAnkle.y() > bacinoY + 0.20f || rightAnkle.y() > bacinoY + 0.20f) return null

        val centroBacino = NormalizedLandmark.create((leftHip.x() + rightHip.x()) / 2f, bacinoY, 0f)

        val angoloDivaricata = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)
        if (angoloDivaricata < 120.0) return null

        // Tracciamento Rotazione
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()

        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // ==========================================
        // SENSORE DI FLESSIONE (Carpiato Profondo)
        // ==========================================
        val nose = landmarks[0]
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isCarpiatoFlesso = distNasoBacino < 0.25f || altezzaTronco < 0.15f

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        var valoreBase = 0.0

        if (isCarpiatoFlesso) {
            nome = if (isEnTournant) "SALTO CARPIATO TOURNANT + FLESSIONE" else "SALTO CARPIATO PROFONDO"
            valoreBase = if (isEnTournant) 0.50 else 0.40
        } else {
            nome = if (isEnTournant) "SALTO CARPIATO EN TOURNANT" else "SALTO CARPIATO"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

        // ==========================================
        // PENALITÀ SULL'APERTURA
        // ==========================================
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val avgAnkleY = (leftAnkle.y() + rightAnkle.y()) / 2f

        if (avgAnkleY <= bacinoY + 0.05f) {
            penalita += 0.0
        } else if (avgAnkleY <= bacinoY + 0.12f) {
            penalita += 0.10
        } else if (avgAnkleY <= bacinoY + 0.20f) {
            penalita += 0.30
        } else {
            penalita += 0.50
            isValida = false
            msgErrore = "Elevazione gambe e divaricata insufficiente"
        }

        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
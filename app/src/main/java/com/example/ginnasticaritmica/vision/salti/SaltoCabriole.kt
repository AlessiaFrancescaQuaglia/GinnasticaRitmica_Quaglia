package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class SaltoCabriole : Difficolta {
    override var nome = "SALTO CABRIOLE"

    companion object {
        private var tempoUltimaBattuta: Long = 0L
        private var bestElevazione = 180.0
        private var nomeSalvato = "SALTO CABRIOLE"
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
        val bacinoX = (leftHip.x() + rightHip.x()) / 2f
        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        // ==========================================
        // 1. TASTO DI RESET (Atterraggio)
        // ==========================================
        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            tempoUltimaBattuta = 0L
            bestElevazione = 180.0
            return null
        }

        // ==========================================
        // 2. CALCOLO APERTURA IBRIDA (2D + 3D)
        // ==========================================
        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)

        val vLeftX = leftAnkle.x() - bacinoX
        val vLeftY = leftAnkle.y() - bacinoY
        val vRightX = rightAnkle.x() - bacinoX
        val vRightY = rightAnkle.y() - bacinoY
        val dot = (vLeftX * vRightX) + (vLeftY * vRightY)
        val magL = kotlin.math.sqrt((vLeftX * vLeftX + vLeftY * vLeftY).toDouble())
        val magR = kotlin.math.sqrt((vRightX * vRightX + vRightY * vRightY).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magL * magR)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        // Prendiamo l'angolo più fedele alla realtà, sfuggendo ai bug di prospettiva
        val aperturaReale = max(angoloSpaccata3D, angoloSpaccata2D)

        // ==========================================
        // 3. IL KILL-SWITCH ANTI-ENJAMBÉE
        // ==========================================
        // In un Cabriole le gambe sbattono insieme. Se le gambe iniziano ad aprirsi a forbice
        // (angolo > 50 gradi) o se la distanza è grande, È IMPOSSIBILE CHE SIA UN CABRIOLE!
        // Autodistruzione immediata della memoria, liberando il salto successivo.
        if (aperturaReale > 50.0 || distanzaCaviglie > 0.25f) {
            tempoUltimaBattuta = 0L
            bestElevazione = 180.0
            return null
        }

        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val spallaCentro = NormalizedLandmark.create((spallaSx.x() + spallaDx.x()) / 2f, (spallaSx.y() + spallaDx.y()) / 2f, 0f)
        val centroCaviglie = NormalizedLandmark.create((leftAnkle.x() + rightAnkle.x()) / 2f, (leftAnkle.y() + rightAnkle.y()) / 2f, 0f)

        val elevazioneGambe = PoseAnalyzer.calculateAngle3D(spallaCentro, centroBacino, centroCaviglie)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2f

        val isLeftHigh = leftAnkle.y() < bacinoY + 0.15f
        val isRightHigh = rightAnkle.y() < bacinoY + 0.15f

        // ==========================================
        // LA TRAPPOLA DEL CABRIOLE
        // ==========================================
        // Scatta solo se l'angolo tra le gambe è strettissimo,
        // le ginocchia sono dritte e le gambe sono alte.
        if (avgKneeAngle > 135.0 && distanzaCaviglie < 0.20f && isLeftHigh && isRightHigh && aperturaReale < 40.0) {

            tempoUltimaBattuta = System.currentTimeMillis()

            if (elevazioneGambe < bestElevazione) {
                bestElevazione = elevazioneGambe
            }

            val nose = landmarks[0]
            val isFacingRight = nose.x() > bacinoX
            val isAvanti = if (isFacingRight) centroCaviglie.x() > bacinoX else centroCaviglie.x() < bacinoX
            nomeSalvato = if (isAvanti) "SALTO CABRIOLE AVANTI" else "SALTO CABRIOLE INDIETRO / LATO"
        }

        // ==========================================
        // FORZATURA DEL RISULTATO
        // ==========================================
        val tempoTrascorso = System.currentTimeMillis() - tempoUltimaBattuta
        val isCabrioleAttivo = tempoTrascorso < 1000L && tempoUltimaBattuta != 0L

        if (isCabrioleAttivo) {
            nome = nomeSalvato
            var penalita = 0.0
            var msgErrore = ""

            if (bestElevazione <= 100.0) penalita += 0.0
            else if (bestElevazione <= 120.0) penalita += 0.10
            else penalita += 0.30

            if (avgKneeAngle < 135.0) {
                penalita += 0.30
                msgErrore = "Ginocchia piegate"
            }

            return RisultatoDifficolta(true, 0.10, penalita, false, msgErrore)
        }

        return null
    }
}
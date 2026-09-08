package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCosacco : Difficolta {
    override var nome = "SALTO COSACCO"

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
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val nose = landmarks[0]

        val bacinoX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (spallaSx.y() + spallaDx.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(spallaSx, leftHip)

        // ==========================================
        // 1. IL FIREWALL ANTI-GRAVITÀ SUPREMO (Anti-Giri / Anti-Penché)
        // ==========================================
        // Un salto esiste solo se si stacca da terra.
        // Se la caviglia più bassa si trova molto al di sotto del bacino
        // (a una distanza tipica di una gamba tesa o di supporto), l'atleta NON è in volo!
        // Usiamo 1.1 volte il busto (scalaCorpo) come lunghezza indicativa di una gamba piantata.
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY > bacinoY + (scalaCorpo * 1.1f)) {
            return null // È a terra (probabilmente sta facendo un Giro Penché o Arabesque!)
        }

        // Ulteriore check: Se il bacino è troppo vicino alla caviglia più bassa,
        // è accovacciata o in equilibrio. Il salto prevede distacco dal suolo.
        val highestAnkleY = kotlin.math.min(leftAnkle.y(), rightAnkle.y())
        if (bacinoY > highestAnkleY) {
            return null
        }

        // ==========================================
        // ROTAZIONE
        // ==========================================
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // ==========================================
        // IDENTIFICAZIONE GAMBE (Regola Cosacco)
        // ==========================================
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 135.0
        val isRightTucked = rightKneeAngle < 135.0

        if (!((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked))) {
            return null
        }

        // ==========================================
        // FIREWALL ANTI-SPACCATA
        // ==========================================
        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)
        val angoloFemorale3D = PoseAnalyzer.calculateAngle3D(leftKnee, centroBacino, rightKnee)

        val vLKx = leftKnee.x() - bacinoX
        val vLKy = leftKnee.y() - bacinoY
        val vRKx = rightKnee.x() - bacinoX
        val vRKy = rightKnee.y() - bacinoY
        val dotFem = (vLKx * vRKx) + (vLKy * vRKy)
        val magLK = kotlin.math.sqrt((vLKx * vLKx + vLKy * vLKy).toDouble())
        val magRK = kotlin.math.sqrt((vRKx * vRKx + vRKy * vRKy).toDouble())
        var angoloFemorale2D = Math.toDegrees(kotlin.math.acos(dotFem / (magLK * magRK)))
        if (angoloFemorale2D.isNaN()) angoloFemorale2D = 0.0

        val angoloFemorale = kotlin.math.max(angoloFemorale3D, angoloFemorale2D)

        if (angoloFemorale > 110.0) {
            return null
        }

        // ==========================================
        // FIREWALL ANTI-INARCAMENTO
        // ==========================================
        // Usa il metro anatomico
        if (spallaY > bacinoY - (scalaCorpo * 0.1f)) {
            return null
        }

        val straightAnkle = if (isLeftStraight) leftAnkle else rightAnkle
        val straightToe = if (isLeftStraight) landmarks[31] else landmarks[32]

        // Usa metro anatomico per l'altezza gamba!
        if (straightAnkle.y() > bacinoY + (scalaCorpo * 0.4f)) return null

        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val distTestaPiede = PoseAnalyzer.calculateDistance3D(nose, straightToe)
        val isGambaAlta = straightAnkle.y() < spallaY + (scalaCorpo * 0.3f) || distTestaPiede < (scalaCorpo * 0.8f)

        val distLeftWrist = PoseAnalyzer.calculateDistance3D(leftWrist, straightAnkle)
        val distRightWrist = PoseAnalyzer.calculateDistance3D(rightWrist, straightAnkle)
        val minWristDist = kotlin.math.min(distLeftWrist, distRightWrist)

        val isConPresa = isGambaAlta && minWristDist <= (scalaCorpo * 0.5f)

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        var valoreBase = 0.0

        if (isConPresa) {
            nome = if (isEnTournant) "SALTO COSACCO TOURNANT CON PRESA" else "SALTO COSACCO CON PRESA"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else if (isGambaAlta) {
            nome = if (isEnTournant) "SALTO COSACCO TOURNANT SENZA PRESA" else "SALTO COSACCO ALTO SENZA PRESA"
            valoreBase = if (isEnTournant) 0.50 else 0.40
        } else {
            nome = if (isEnTournant) "SALTO COSACCO EN TOURNANT" else "SALTO COSACCO CLASSICO"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (straightAnkle.y() < spallaY + (scalaCorpo * 0.3f)) {
            penalita += 0.0
        } else if (straightAnkle.y() < bacinoY - (scalaCorpo * 0.1f)) {
            penalita += 0.10
        } else if (straightAnkle.y() <= bacinoY + (scalaCorpo * 0.4f)) {
            penalita += if (isGambaAlta) 0.30 else 0.10
        } else {
            penalita += 0.50
            isValida = false
            msgErrore = "Gamba avanti caduta/troppo bassa"
        }

        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
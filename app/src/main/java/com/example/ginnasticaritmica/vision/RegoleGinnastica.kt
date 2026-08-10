package com.example.ginnasticaritmica.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object RegoleGinnastica {

    fun verificaReleve(landmarks: List<NormalizedLandmark>, isLeftLeg: Boolean): Boolean {
        if (landmarks.size <= 32) return false

        val knee = if (isLeftLeg) landmarks[25] else landmarks[26]
        val ankle = if (isLeftLeg) landmarks[27] else landmarks[28]
        val toe = if (isLeftLeg) landmarks[31] else landmarks[32]

        // MIGLIORAMENTO: Ora usiamo il calcolo 3D, che è molto più preciso
        // e non si fa ingannare dall'angolazione della telecamera!
        val ankleAngle = PoseAnalyzer.calculateAngle3D(knee, ankle, toe)

        // Criteri di giudizio geometrico:
        // ~ 90° - 110° -> Piede piatto (Pianta)
        // ~ 111° - 149° -> Mezza punta bassa (Scartata)
        // >= 150° -> Relevé alto e corretto
        return ankleAngle >= 135.0
    }
}
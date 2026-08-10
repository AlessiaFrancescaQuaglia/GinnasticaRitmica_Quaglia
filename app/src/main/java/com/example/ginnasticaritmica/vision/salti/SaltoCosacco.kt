package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class SaltoCosacco : Difficolta {
    override var nome = "SALTO COSACCO"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val angleLeft = PoseAnalyzer.calculateAngle3D(landmarks[23], landmarks[25], landmarks[27])
        val angleRight = PoseAnalyzer.calculateAngle3D(landmarks[24], landmarks[26], landmarks[28])

        // 1. Identificazione Gamba Tesa e Gamba Piegata (Cosacco)
        val isLeftStraight = angleLeft > angleRight

        val straightHip = if (isLeftStraight) landmarks[23] else landmarks[24]
        val straightKnee = if (isLeftStraight) landmarks[25] else landmarks[26]
        val straightAnkle = if (isLeftStraight) landmarks[27] else landmarks[28]
        val straightToe = if (isLeftStraight) landmarks[31] else landmarks[32]

        val bentHip = if (isLeftStraight) landmarks[24] else landmarks[23]
        val bentKnee = if (isLeftStraight) landmarks[26] else landmarks[25]
        val bentAnkle = if (isLeftStraight) landmarks[28] else landmarks[27]

        // Controllo base: la gamba tesa deve essere estesa (>150°) e quella piegata raccolta (<130°)
        val straightLegAngle = PoseAnalyzer.calculateAngle3D(straightHip, straightKnee, straightAnkle)
        val bentLegAngle = PoseAnalyzer.calculateAngle3D(bentHip, bentKnee, bentAnkle)
        if (straightLegAngle < 150.0 || bentLegAngle > 130.0) return null

        // 2. CONTROLLO FONDAMENTALE DAL CODICE: Il piede deve essere PIÙ ALTO DELLA TESTA
        val nose = landmarks[0] // Usiamo il naso/testa come riferimento
        // In MediaPipe Y è invertito: valori più bassi (es. 0.2) sono più in alto nello schermo rispetto alla testa (es. 0.4)
        if (straightToe.y() > nose.y()) {
            return null // Se il piede è più in basso della testa, non è questo tipo di salto in alto.
        }

        // 3. DISTINZIONE: Laterale vs Avanti
        val supportHeel = if (isLeftStraight) landmarks[30] else landmarks[29]
        val supportToe = if (isLeftStraight) landmarks[32] else landmarks[31]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Controlliamo se la gamba è tesa lateralmente o in avanti
        // Se la coordinata X della caviglia è molto discosta dall'anca rispetto all'asse, è laterale.
        val isLateral = kotlin.math.abs(straightAnkle.x() - straightHip.x()) > 0.20f

        // 4. CONTROLLO AIUTO (Presa della mano sullo stesso lato del piede teso)
        val polsoStessoLato = if (isLeftStraight) landmarks[15] else landmarks[16]
        val distanzaManoPiede = PoseAnalyzer.calculateDistance3D(polsoStessoLato, straightToe)
        val conAiuto = distanzaManoPiede < 0.20f // Se la mano è vicina al piede

        // 5. ASSEGNAZIONE NOME E VALORE BASE (Da tabelle ufficiali)
        var valoreBase = 0.30
        if (isLateral) {
            if (conAiuto) {
                nome = "COSACCO LATERALE IN ALTO (Con aiuto)"
                valoreBase = 0.20 // Cod. 1.1802
            } else {
                nome = "COSACCO LATERALE IN ALTO (Senza aiuto)"
                valoreBase = 0.30 // Cod. 1.1903
            }
        } else {
            nome = "COSACCO AVANTI IN ALTO"
            valoreBase = 0.30 // Cod. 1.1703
        }

        // 6. CALCOLO PENALITÀ DI ESECUZIONE (Deviazioni sull'altezza del piede)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Differenza di altezza tra testa e punta del piede
        val deltaAltezza = nose.y() - straightToe.y() // Più è positivo, più il piede è sopra la testa

        if (deltaAltezza >= 0.10f) {
            penalita += 0.0 // Piede ampiamente sopra la testa (Perfetto)
        } else if (deltaAltezza in 0.02f..0.099f) {
            penalita += 0.10 // Piccola deviazione (piede appena sopra la testa)
        } else if (deltaAltezza in -0.05f..0.019f) {
            penalita += 0.30 // Deviazione media (piede all'altezza della testa)
        } else {
            penalita += 0.50
            isValida = false
            msgErrore = "Piede sotto la testa"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
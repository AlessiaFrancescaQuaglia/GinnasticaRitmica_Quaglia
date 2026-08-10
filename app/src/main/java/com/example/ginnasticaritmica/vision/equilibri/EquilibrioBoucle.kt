package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class EquilibrioBoucle : Difficolta {
    override var nome = "BOUCLE (Anello)"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // 1. Identificazione Gamba di Appoggio e Gamba Libera
        val isAppoggioLeft = landmarks[27].y() > landmarks[28].y()
        val isLeftLifted = !isAppoggioLeft

        val cavigliaLibera = if (isAppoggioLeft) landmarks[28] else landmarks[27]
        val spallaCentroY = (landmarks[11].y() + landmarks[12].y()) / 2f

        // --- SUPER FILTRO ALTEZZA ---
        if (cavigliaLibera.y() > spallaCentroY) {
            return null
        }

        val ancaAppoggio = if (isAppoggioLeft) landmarks[23] else landmarks[24]
        val ginocchioAppoggio = if (isAppoggioLeft) landmarks[25] else landmarks[26]
        val cavigliaAppoggio = if (isAppoggioLeft) landmarks[27] else landmarks[28]

        val ancaLibera = if (isLeftLifted) landmarks[23] else landmarks[24]
        val ginocchioSollevato = if (isLeftLifted) landmarks[25] else landmarks[26]
        val cavigliaSollevata = if (isLeftLifted) landmarks[27] else landmarks[28]
        val nose = landmarks[0]

        // 2. Controllo Ginocchio di Appoggio (Deve essere teso)
        val angoloAppoggio = PoseAnalyzer.calculateAngle3D(ancaAppoggio, ginocchioAppoggio, cavigliaAppoggio)
        if (angoloAppoggio < 150.0) return null

        // --- 3. NUOVO FIREWALL ANTI-PLANCHE/ARABESQUE ---
        // Se la gamba sollevata è dritta, è fisicamente impossibile che sia un Boucle!
        val flessioneGinocchioLibero = PoseAnalyzer.calculateAngle3D(ancaLibera, ginocchioSollevato, cavigliaSollevata)
        if (flessioneGinocchioLibero > 140.0) {
            return null // Gamba tesa -> Blocca tutto e lascia lavorare la classe Arabesque/Planche!
        }

        // 4. IL FIREWALL 3D DELL'ANELLO (Distanza Naso - Caviglia Sollevata)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, cavigliaSollevata)
        if (distanzaTestaPiede > 0.35f) return null

        // 5. Direzione (La gamba sollevata deve andare all'indietro, non in avanti)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        val isBackward = if (isFacingRight) ginocchioSollevato.x() < ancaAppoggio.x() else ginocchioSollevato.x() > ancaAppoggio.x()
        if (!isBackward) return null

        // 6. Controllo "Con Aiuto" (Verifica se le mani afferrano il piede)
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], cavigliaSollevata)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], cavigliaSollevata)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        // 7. Assegnazione Valore
        var valoreBase = 0.0
        if (conAiuto) {
            nome = "BOUCLE (Con aiuto)"
            valoreBase = 0.30
        } else {
            nome = "BOUCLE (Senza aiuto)"
            valoreBase = 0.50
        }

        // 8. Controllo Relevé (La penalità di 0.10 si toglie direttamente dal valore base)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            valoreBase -= 0.10
        }

        // 9. Calcolo Penalità Esecutive (Ampiezza della chiusura dell'anello)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (distanzaTestaPiede <= 0.20f) penalita += 0.0
        else if (distanzaTestaPiede in 0.201f..0.28f) penalita += 0.10
        else penalita += 0.30

        if (valoreBase <= 0.0) {
            isValida = false
            msgErrore = "Eseguito in Pianta (Valore nullo)"
        } else if (!isValida && msgErrore.isEmpty()) {
            msgErrore = "Anello non chiuso"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Analizzatore per l'Equilibrio "Dégagé" (gamba sollevata tesa in avanti o lateralmente).
 * Questa posa rappresenta la figura "base" a gamba tesa.
 */
class EquilibrioDegage : Difficolta {
    override val nome = "EQUILIBRIO DÉGAGÉ"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Se il thread che monitora i Pivot (rotazioni) è nello stato "Attivo",
        // questo modulo si sospende per evitare collisioni di riconoscimento.
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Derivazione del baricentro e dell'altezza del tronco.
        // "scalaCorpo" è la distanza Euclidea Spalla-Bacino, usata come unità di misura
        // relativa per rendere l'algoritmo indipendente dalla distanza della telecamera.
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        //  FIREWALL BUSTO
        // Nel Dégagé il tronco deve rimanere verticale. Tuttavia, viene applicata una
        // tolleranza euristica (0.20f della scala corpo) per consentire i fisiologici
        // micro-spostamenti che l'atleta esegue per mantenere il baricentro.
        // Se si piega oltre questa soglia, la posa è un'altra (es. Planche) e si abortisce.
        if (spallaY > bacinoY - (scalaCorpo * 0.20f)) {
            return null
        }

        //  FIREWALL ANTI-VOLO
        // Accerta che la coordinata Y (altitudine) della caviglia più bassa sia compatibile
        // con un appoggio sul pavimento. Se è troppo vicina al bacino, l'atleta sta saltando.
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY < bacinoY + (scalaCorpo * 0.9f)) {
            return null
        }

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Assegnazione logica dei vettori operativi (Gamba di terra vs Gamba libera)
        val isLeftLifted = leftAnkle.y() < rightAnkle.y()
        val isAppoggioLeft = !isLeftLifted

        //  CONTROLLO  GINOCCHIA
        val angoloAppoggio = if (isAppoggioLeft) leftKneeAngle else rightKneeAngle
        val tensioneGinocchio = if (isLeftLifted) leftKneeAngle else rightKneeAngle

        // Se il ginocchio libero scende sotto i 135°, la posa passa di competenza alla classe "Attitude".
        // Oltre i 135° resta di competenza del "Dégagé", ma verrà penalizzata nel blocco finale
        // in quanto non perfettamente tesa (180°).
        if (angoloAppoggio < 135.0) return null
        if (tensioneGinocchio < 135.0) return null

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) leftHip else rightHip
        val ginocchio = if (isLeftLifted) leftKnee else rightKnee
        val caviglia = if (isLeftLifted) leftAnkle else rightAnkle
        val supportHip = if (isAppoggioLeft) leftHip else rightHip

        // Calcolo dell'ampiezza dell'alzata tramite Prodotto Scalare 3D
        val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)

        //  FIREWALL DIREZIONALE
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Calcolo della proiezione della caviglia libera rispetto all'anca di appoggio (Asse X).
        // Se il vettore cade nel semispazio posteriore (dietro l'atleta), è fisicamente un Arabesque.
        // Viene aggiunto un buffer di tolleranza (0.3f) per le riprese leggermente in diagonale.
        val isBackward = if (isFacingRight) caviglia.x() < supportHip.x() - (scalaCorpo * 0.3f) else caviglia.x() > supportHip.x() + (scalaCorpo * 0.3f)
        if (isBackward) return null

        // Se l'angolo spalla-anca-ginocchio è quasi piatto (> 130°), l'atleta è semplicemente in piedi.
        if (altezzaGamba > 130.0) return null

        //  RESTITUZIONE PUNTEGGIO E PENALITÀ
        // Il tallone abbassato (in pianta) abbassa il valore della difficoltà
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20
        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Discretizzazione delle penalità di altezza
        if (altezzaGamba <= 95.0) penalita += 0.0
        else if (altezzaGamba in 95.1..105.0) penalita += 0.10
        else if (altezzaGamba in 105.1..120.0) penalita += 0.30
        else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa " }

        // Discretizzazione delle penalità sul ginocchio
        if (isValida) {
            if (tensioneGinocchio >= 160.0) penalita += 0.0
            else if (tensioneGinocchio in 145.0..159.9) penalita += 0.10
            else if (tensioneGinocchio in 135.0..144.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio troppo piegato " }
        }

        // Drop del pacchetto se il valore base si azzera
        if (valoreBase <= 0.0) { isValida = false; msgErrore += "(In Pianta)" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
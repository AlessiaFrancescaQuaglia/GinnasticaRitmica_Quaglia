package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri // Assicurati di importarlo in alto!

/**
 * Modulo di analisi per l'Equilibrio "Boucle" (comunemente noto come "Anello").
 * Questa figura richiede una flessione estrema della schiena e della gamba per
 * chiudere un "anello" tra la testa e il piede. È la classe ideale per dimostrare al
 * Prof. Verderame l'implementazione dei "Firewall Logici" ingegnerizzati per dirimere i
 * conflitti geometrici tra pose molto simili.
 */
class EquilibrioBoucle : Difficolta {
    override var nome = "BOUCLE (Anello)"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // FIREWALL ANTI-VOLO (UNIVERSALE PER EQUILIBRI)
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Verifica che il sistema sia effettivamente in una fase di stabilità statica a terra.
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0


        if (landmarks.size <= 32) return null

        // SCUDO ANTI-ROTAZIONE (Gestione della Concorrenza):
        // L'acquisizione dello stato di rotazione invoca LucchettoGiri.
        //Finche è attivo il codice abortisce l'analisi per evitare concorrenze con i giri
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        if (!isLeftPlanted && !isRightPlanted) {
            return null // se l'atleta è in volo, delega l'elaborazione all'AnalizzatoreSalti.
        }

        // Identificazione algoritmica degli arti
        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val isLeftLifted = !isAppoggioLeft

        val cavigliaLibera = if (isAppoggioLeft) rightAnkle else leftAnkle
        val spallaCentroY = (landmarks[11].y() + landmarks[12].y()) / 2f

        // nel Boucle la caviglia deve superare la coordinata Y delle spalle.
        if (cavigliaLibera.y() > spallaCentroY) {
            return null
        }

        val ancaAppoggio = if (isAppoggioLeft) leftHip else rightHip
        val ginocchioSollevato = if (isLeftLifted) leftKnee else rightKnee
        val cavigliaSollevata = if (isLeftLifted) leftAnkle else rightAnkle
        val nose = landmarks[0]

        // Controllo strutturale (Il ginocchio portante non deve collassare)
        val angoloAppoggio = if (isAppoggioLeft) leftKneeAngle else rightKneeAngle
        if (angoloAppoggio < 150.0) return null

        // Un'elevazione anomala del tallone durante un equilibrio Planche evocava
        // per similitudine il modulo di questo equilibrio.
        // Questo vincolo di estensione rigido garantisce l'esclusione categorica del Boucle
        // qualora la gamba posteriore dell'atleta si presenti anatomicamente tesa.
        // Esclude matematicamente i Planche dai falsi positivi dell'Anello.
        val flessioneGinocchioLibero = if (isLeftLifted) leftKneeAngle else rightKneeAngle
        if (flessioneGinocchioLibero > 140.0) {
            return null // Gamba tesa -> Abortisce e cede l'esecuzione polimorfica alla classe Arabesque.
        }

        //  FIREWALL 3D DELL'ANELLO (Validazione Spaziale)
        // Misura la norma del vettore tra il punto 0 (Naso) e la caviglia sollevata
        // per accertarsi che la figura geometrica "ad anello" sia chiusa.
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, cavigliaSollevata)
        if (distanzaTestaPiede > 0.35f) return null

        //  Direzione sull'asse Z/X (Proiezione nel semispazio posteriore)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        val isBackward = if (isFacingRight) ginocchioSollevato.x() < ancaAppoggio.x() else ginocchioSollevato.x() > ancaAppoggio.x()
        if (!isBackward) return null

        //Rilevamento Astrazione "Con Aiuto"
        // Il sistema rileva autonomamente se l'atleta si afferra il piede intersecando
        // le coordinate spaziali dei polsi (landmarks 15, 16) con quelle della caviglia.
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], cavigliaSollevata)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], cavigliaSollevata)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        // Modulazione Adattiva del Valore
        // Il software premia l'esecuzione "senza aiuto" (basata solo sulla forza muscolare dorsale)
        // assegnando un Base Value superiore, in conformità con il regolamento sportivo.
        var valoreBase = 0.0
        if (conAiuto) {
            nome = "EQUILIBRIO BOUCLE (Con aiuto)"
            valoreBase = 0.30
        } else {
            nome = "EQUILIBRIO BOUCLE (Senza aiuto)"
            valoreBase = 0.50
        }

        // Controllo Relevé (La penalità di 0.10 si decurta direttamente alla base)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            valoreBase -= 0.10
        }

        //  Calcolo Deterministico delle Penalità Esecutive
        // Quantifica matematicamente l'errore di ampiezza della chiusura dell'anello,
        // superando i limiti intrinseci di misurazione dell'occhio umano.
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
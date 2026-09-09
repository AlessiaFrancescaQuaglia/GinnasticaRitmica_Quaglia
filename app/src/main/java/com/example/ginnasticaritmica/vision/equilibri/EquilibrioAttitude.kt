package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Analizzatore dedicato all'Equilibrio "Attitude".
 * L'Attitude, avendo una gamba flessa, rischia costantemente di essere confuso
 * con il Passé (gamba flessa) o con il Penché/Arabesque (gamba dietro).
 */
class EquilibrioAttitude : Difficolta {
    override var nome = "ATTITUDE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // GESTIONE DELLA CONCORRENZA (SCUDO ANTI-ROTAZIONE)
        // Previene Race Conditions con il thread che valuta i Pivot.
        // Se l'atleta sta girando su se stessa, scartiamo il frame a priori.
        if (LucchettoGiri.giroAttivo != null) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)


        // FIREWALL ANTI-VOLO
        // Inibisce l'attivazione del modulo durante le fasi aeree.
        // Richiede che almeno una caviglia si trovi al di sotto del bacino
        // a una distanza compatibile con l'estensione femorale stimata.
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY < bacinoY + (scalaCorpo * 0.9f)) return null

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        if (!isLeftPlanted && !isRightPlanted) return null

        //  IDENTIFICAZIONE DINAMICA DEGLI ARTI
        val isLeftLifted = leftAnkle.y() < rightAnkle.y()
        val isAppoggioLeft = !isLeftLifted

        val spalla = if (isLeftLifted) landmarks[11] else landmarks[12]
        val anca = if (isLeftLifted) leftHip else rightHip
        val ginocchioSollevato = if (isLeftLifted) leftKnee else rightKnee
        val cavigliaSollevata = if (isLeftLifted) leftAnkle else rightAnkle
        val puntaLibera = if (isLeftLifted) landmarks[31] else landmarks[32]

        val ancaAppoggio = if (isAppoggioLeft) leftHip else rightHip
        val ginocchioAppoggio = if (isAppoggioLeft) leftKnee else rightKnee

        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Calcolo vettoriale sull'asse X per determinare in quale semispazio è proiettata la gamba
        val isBackward = if (isFacingRight) ginocchioSollevato.x() < ancaAppoggio.x() else ginocchioSollevato.x() > ancaAppoggio.x()

        //  SCUDO ANTI-CAMBRÉ (Disambiguazione con Penché / Arabesque)
        // L'esecuzione di un Attitude associato a flessione dorsale profonda
        // produce un marcato abbassamento della coordinata Y delle spalle
        // Questo filtro analizza il delta-Y: se le spalle crollano sotto una certa soglia
        // e la gamba è distesa all'indietro, abortisce la logica e cede il passo all'analizzatore del Penché
        val isBustoFlesso = spallaY > bacinoY - (scalaCorpo * 0.15f)
        val isFlessioneDorsale = isBustoFlesso && cavigliaSollevata.y() < spallaY + (scalaCorpo * 0.3f)

        if (isBustoFlesso && isBackward && !isFlessioneDorsale) {
            return null // Non è un Attitude, cede il controllo a Penché o Arabesque
        }

        // FILTRO ANTI-PASSÉ INTELLIGENTE (Disambiguazione Spaziale 3D)
        // Un semplice controllo sull'angolo del ginocchio (<140°) non basta a distinguere
        // un Passé da un Attitude. Viene introdotto un Vincolo di Prossimità Spaziale 3D:
        // L'algoritmo calcola la distanza 3D tra la punta del piede sollevato e il ginocchio di appoggio.
        val distanzaPuntaGinocchio = PoseAnalyzer.calculateDistance3D(puntaLibera, ginocchioAppoggio)

        // Se la gamba NON è dietro, e il piede è  vicinissimo al ginocchio,
        // è matematicamente un Passé. Il frame viene rigettato.
        if (!isBackward && distanzaPuntaGinocchio < (scalaCorpo * 0.4f)) {
            return null
        }

        // Controllo di stabilità del Ginocchio di Appoggio (deve sorreggere il peso)
        val angoloAppoggio = PoseAnalyzer.calculateAngle3D(ancaAppoggio, ginocchioAppoggio, if (isAppoggioLeft) leftAnkle else rightAnkle)
        if (angoloAppoggio < 140.0) return null

        //  CALCOLO FLESSIONE GINOCCHIO SOLLEVATO (Doppio Controllo)
        val angoloSollevato = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchioSollevato)

        // Per massimizzare l'affidabilità di questa posa , il sistema incrocia
        // il calcolo del prodotto scaare planare (2D) con quello spaziale (3D),
        // estraendo il valore peggiore (il più esteso) tra i due.
        val dxAnca = anca.x() - ginocchioSollevato.x()
        val dyAnca = anca.y() - ginocchioSollevato.y()
        val dxCav = cavigliaSollevata.x() - ginocchioSollevato.x()
        val dyCav = cavigliaSollevata.y() - ginocchioSollevato.y()

        val dotGinocchio = (dxAnca * dxCav) + (dyAnca * dyCav)
        val magAnca = kotlin.math.sqrt((dxAnca * dxAnca + dyAnca * dyAnca).toDouble())
        val magCav = kotlin.math.sqrt((dxCav * dxCav + dyCav * dyCav).toDouble())
        var flessioneGinocchio2D = Math.toDegrees(kotlin.math.acos(dotGinocchio / (magAnca * magCav)))
        if (flessioneGinocchio2D.isNaN()) flessioneGinocchio2D = 0.0

        val flessioneGinocchio3D = PoseAnalyzer.calculateAngle3D(anca, ginocchioSollevato, cavigliaSollevata)
        val flessioneGinocchio = kotlin.math.max(flessioneGinocchio2D, flessioneGinocchio3D)

        // Se l'angolo del ginocchio supera i 155°, la gamba è da considerarsi "tesa".
        // Infrange quindi la definizione anatomica di Attitude.
        if (flessioneGinocchio > 155.0) return null

        // Se la coscia cade verticalmente sotto i 145°, l'elemento non sussiste
        if (angoloSollevato > 145.0) return null

        // RESTITUZIONE PUNTEGGIO
        nome = if (isBackward) "EQUILIBRIO ATTITUDE INDIETRO" else "EQUILIBRIO ATTITUDE AVANTI/LATO"

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        var valoreBase = 0.20
        if (!inReleve) valoreBase -= 0.10

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Penalità basate sull'ampiezza dell'alzata della coscia rispetto al suolo
        if (angoloSollevato <= 100.0) penalita = 0.0
        else if (angoloSollevato in 100.1..115.0) penalita = 0.10
        else if (angoloSollevato in 115.1..130.0) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Ginocchio troppo basso"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "(In Pianta)"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
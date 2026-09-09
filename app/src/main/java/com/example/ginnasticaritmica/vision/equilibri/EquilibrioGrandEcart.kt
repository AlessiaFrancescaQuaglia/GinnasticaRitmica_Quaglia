package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Analizzatore per l'Equilibrio "Grand Écart" (Spaccata a 180°).
 * il grand ecart è una forma geometrica comune a molte famiglie di movimenti (Salti, Pivot, altri equilibri).
 * L'algoritmo deve applicare molteplici "Firewall logici" per accertarsi che sia
 * un equilibrio statico e non un'altra figura in transito.
 */
class EquilibrioGrandEcart : Difficolta {
    override var nome = "GRAND ÉCART AVANTI"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Estrazione dei tensori spaziali essenziali
        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]


        // FIREWALL ANTI-VOLO  (Altitudine Relativa)
        // Discrimina un equilibrio da un "Salto in Spaccata" (Grand Jeté).
        // Calcola l'altezza del tronco come unità di misura dinamica per valutare
        // la quota Y della caviglia di appoggio rispetto al bacino.
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val altezzaTronco = kotlin.math.abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val lowestAnkleY = if (isLeftLowest) leftAnkle.y() else rightAnkle.y()
        val lowestKneeAngle = if (isLeftLowest) leftKneeAngle else rightKneeAngle

        // Gestione Concorrenza : inibisce l'analisi se il thread dei Pivot è bloccato.
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        // Se la caviglia scende, MA il ginocchio di supporto è piegato (< 135°),
        // non può sostenere il peso di una spaccata eretta.
        // Si deduce che l'atleta sia in volo (es. Salto Cosacco) e si scarta il frame.
        if (lowestKneeAngle < 135.0) {
            return null
        }

        // Vincolo gravitazionale: se il punto più basso (piede) non dista dal bacino
        // almeno il 70% della lunghezza del tronco, l'atleta non sta toccando terra.
        if (lowestAnkleY < bacinoY + (altezzaTronco * 0.7f)) {
            return null
        }



        // FIREWALL ANTI-COSACCO
        // Un "Grand Écart" impone un vincolo  rigido: ENTRAMBE le gambe tese.
        // Se la rete neurale rileva asimmetria (una tesa, l'altra flessa sotto i 120°),
        // si passa il controllo alla classe EquilibrioCosacco.
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 120.0
        val isRightTucked = rightKneeAngle < 120.0

        if ((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked)) {
            return null
        }


        //  Identificazione Algoritmica degli Arti
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val supportHip = if (isLeftLowest) leftHip else rightHip
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        // Vettore Pianta del Piede
        // Usa il delta tra tallone e punta (anziché i fianchi) per determinare
        // l'orientamento (Destra/Sinistra), rendendo il calcolo immune alle distorsioni ottiche
        // e all'innalzamento in relevé.
        val supportHeel = if (isLeftLowest) landmarks[29] else landmarks[30]
        val supportToe = if (isLeftLowest) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // CONTROLLO DIREZIONE GAMBA (Semispazio Frontale)
        // Assicura che la gamba libera sia proiettata in avanti rispetto all'asse Y del bacino.
        val isForwardExtended = if (isFacingRight) liftedKnee.x() > supportHip.x() else liftedKnee.x() < supportHip.x()
        if (!isForwardExtended) return null

        //  FIREWALL ANTI-PENCHÉ
        // Se l'atleta esegue un Penché (spaccata dorsale in asse con busto in avanti),
        // l'angolo inter-femorale è 180° proprio come qui. Il discriminante geometrico è la distanza
        // Norma 3D tra il Naso e la Caviglia. Se sono vicini (< 30cm), la figura è dorsale.
        val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
        if (distNasoCaviglia < 0.30f) return null

        // 5. Calcolo Inclinazione Busto
        // Misura l'angolo di flessione retrostante tramite Arcotangente
        // applicata ai centri di massa di spalle e bacino
        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f

        val dx = shoulderCenterX - midHipX
        val dy = bacinoY - spallaY
        val isTorsoBackward = if (isFacingRight) dx < 0 else dx > 0

        var gradiBustoIndietro = 0.0
        if (isTorsoBackward) {
            gradiBustoIndietro = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))
        }

        // Apertura Spaccata Vettoriale in 3D (Calcolo Prospettico)
        // Sfrutta il Prodotto Scalare 3D per calcolare i gradi reali dell'apertura coxo-femorale
        // (angolo Caviglia - Centro Bacino - Caviglia), neutralizzando la distorsione ottica .
        val midHipLandmark = NormalizedLandmark.create(midHipX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 130.0) return null

        //  Controllo Relevé e Rilevamento Astrazione "Con Aiuto"
        // Determina autonomamente la presa verificando l'intersezione dei volumi 3D tra polsi e caviglia.
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isLeftLowest)

        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.12f || distPolsoDx < 0.12f)

        //  Assegnazione Dinamica del Valore Tecnico
        var valoreBase = 0.0

        if (gradiBustoIndietro > 110.0) {
            nome = "EQUILIBRIO GRAND ÉCART AVANTI (Sotto orizz.)"
            valoreBase = 0.60
        } else if (gradiBustoIndietro in 70.0..110.0) {
            if (conAiuto) {
                nome = "EQUILIBRIO GRAND ÉCART AVANTI (con aiuto)"
                valoreBase = 0.40
            } else {
                nome = "EQUILIBRIO GRAND ÉCART AVANTI (senza aiuto)"
                valoreBase = 0.60
            }
        } else {
            nome = if (conAiuto) "EQUILIBRIO GRAND ÉCART AVANTI (Con aiuto)" else "EQUILIBRIO GRAND ÉCART AVANTI (Senza aiuto)"
            valoreBase = if (conAiuto) 0.30 else 0.50
        }

        if (!inReleve) {
            valoreBase -= 0.10 // Penalità decurtata a priori per mancato relevé propulsivo
        }

        // Calcolo Deterministico delle Penalità sull'Ampiezza
        // Il sistema gestisce l'Oversplit (spaccate oltre i 180° che non comportano penalità)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata

        if (deficitAmpiezza <= 10.0) penalita = 0.0
        else if (deficitAmpiezza in 10.1..25.0) penalita = 0.10
        else if (deficitAmpiezza in 25.1..45.0) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Apertura insufficiente"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(
            isValida = isValida,
            valoreFinale = if (valoreBase > 0) valoreBase else 0.0,
            penalita = penalita,
            inReleve = inReleve,
            messaggioErrore = msgErrore
        )
    }
}
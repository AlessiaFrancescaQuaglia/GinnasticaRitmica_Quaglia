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
 * Modulo dedicato all'analisi dell'Equilibrio "Arabesque" (e della sua variante "Planche").
 * Questo file traduce le direttive del Codice dei Punteggi (FIG) in regole
 * matematiche rigide (Firewall logici). È un eccellente esempio di come si è
 * passati dalla valutazione di un giudice all'oggettività vettoriale.
 */
class EquilibrioArabesque : Difficolta {
    override var nome = "ARABESQUE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        // Controllo di sicurezza: array completo (33 punti scheletrici)
        if (landmarks.size <= 32) return null

        // Risoluzione della Concorrenza:
        // Se l'atleta sta eseguendo una Rotazione (Giro), l'istanza LucchettoGiri è bloccata.
        // Questo modulo deve annullare l'esecuzione per evitare di danneggiare l'analisi del giro.
        if (LucchettoGiri.giroAttivo != null) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Derivazione dei centri di gravità (Baricentro Bacino e Linea Spalle)
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        // L'altezza dell'atleta viene usata come unità di misura dinamica ("scalaCorpo")
        // per rendere l'algoritmo universale (funziona sia su bambine che adulte)
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)


        // 1. FIREWALL ANTI-VOLO (Prevenzione Falsi Positivi in Aria)
        // Un errore tipico del sistema primordiale era valutare i salti come equilibri.
        // Questo blocco esige che almeno un piede (la caviglia più bassa) sia saldamente
        // ancorato a terra (distante dal bacino per quasi tutta l'altezza della gamba).
        val lowestAnkleY = kotlin.math.max(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY < bacinoY + (scalaCorpo * 0.9f)) return null

        // Le gambe devono essere tese. Se entrambe sono molto piegate, la posa è invalida a priori
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        if (leftKneeAngle < 130.0 || rightKneeAngle < 130.0) return null

        // Deduzione logica della gamba di terra e gamba sollevata (libera)
        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val spalla = if (isAppoggioLeft) landmarks[12] else landmarks[11]
        val anca = if (isAppoggioLeft) rightHip else leftHip
        val ginocchio = if (isAppoggioLeft) rightKnee else leftKnee
        val caviglia = if (isAppoggioLeft) rightAnkle else leftAnkle

        val supportHip = if (isAppoggioLeft) leftHip else rightHip
        val supportKnee = if (isAppoggioLeft) leftKnee else rightKnee


        // 2. FIREWALL ANTI-FALSO POSITIVO (In piedi)
        // La gamba libera deve essere sollevata oltre il ginocchio d'appoggio.
        // Se è abbassata, l'atleta è semplicemente in piedi e interrompiamo l'analisi.
        if (caviglia.y() > supportKnee.y()) return null

        // Identificazione dell'orientamento dell'atleta rispetto alla telecamera (Profilo Dx o Sx)
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()


        // 3. ANALISI BUSTO E DIREZIONE DELLA GAMBA
        // L'Arabesque prevede che la gamba libera sia spinta indietro (posteriormente).
        // Se è lanciata in avanti (es. Dégagé), lo script abortisce per passare il controllo
        // all'analizzatore corretto.
        val isBackwardExtended = if (isFacingRight) caviglia.x() < supportHip.x() else caviglia.x() > supportHip.x()
        if (!isBackwardExtended) return null

        val shoulderCenterX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val shoulderCenterY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipCenterX = (leftHip.x() + rightHip.x()) / 2f

        val dx = shoulderCenterX - hipCenterX
        val dy = bacinoY - shoulderCenterY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        // Determina se il busto è flesso in avanti (Planche) o reclinato all'indietro (Cambré)
        val isTorsoForward = if (isFacingRight) dx > 0 else dx < 0
        val isTorsoBackward = if (isFacingRight) dx < 0 else dx > 0

        val nose = landmarks[0]
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, caviglia)
        val isTouching = distanzaTestaPiede < (scalaCorpo * 0.6f)

        var valoreBase = 0.20
        var isPlanche = false


        // 4. CLASSIFICAZIONE E PUNTEGGI (CODICE F.I.G.)
        // Assegnazione del "Valore Base" (da 0.10 a 0.50) e del nome della difficoltà
        // in base alle combinazioni anatomiche (es. se la testa tocca il piede il valore aumenta).
        if (isTorsoForward && gradiBusto in 55.0..130.0) {
            nome = "EQUILIBRIO PLANCHE"
            valoreBase = 0.30
            isPlanche = true
        } else if (isTorsoBackward && isTouching) {
            nome = "EQUILIBRIO ARABESQUE (Flessione indietro con contatto)"
            valoreBase = 0.40
        } else if (isTorsoBackward && gradiBusto in 70.0..110.0) {
            nome = "EQUILIBRIO ARABESQUE (Flesso indietro orizz.)"
            valoreBase = 0.30
        } else if (isTorsoForward) {
            return null // Posa transitoria (probabilmente si sta tuffando, non è un Arabesque)
        } else {
            nome = "EQUILIBRIO ARABESQUE"
            valoreBase = 0.20
        }

        // Il rilevamento del tallone di supporto sollevato (Relevé) è essenziale.
        // Un equilibrio eseguito col tallone a terra (Pianta) subisce un deprezzamento del valore.
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) {
            valoreBase -= 0.10
        }

        var penalita = 0.0
        var isValida = true
        var msgErrore = ""


        // 5. APPLICAZIONE DELLE PENALITÀ (ERRORI ESECUTIVI)
        // Il giudice umano applica penalità (es. -0.10, -0.30, -0.50) se la forma
        // non rispetta le angolature ideali dettate dal regolamento.

        if (isPlanche) {
            // Nel Planche l'algoritmo valuta la quota Y (Altitudine) della gamba rispetto all'anca,
            // abbandonando l'angolo dorsale per evitare contrazioni geometriche causate da estrema flessibilità.
            val cadutaGambaY = caviglia.y() - anca.y()
            val tolleranza = scalaCorpo * 0.1f

            if (cadutaGambaY <= 0.02f) {
                penalita += 0.0 // Perfetto
            } else if (cadutaGambaY in 0.021f..tolleranza) {
                penalita += 0.10 // Leggero errore
            } else if (cadutaGambaY in tolleranza..(tolleranza*2)) {
                penalita += 0.30 // Errore grave
            } else {
                penalita += 0.50
                isValida = false // Annullamento tecnico
                msgErrore = "Gamba troppo bassa (Sotto l'orizzontale)"
            }
        } else {
            val altezzaGamba = PoseAnalyzer.calculateAngle3D(spalla, anca, ginocchio)
            if (altezzaGamba <= 100.0) penalita += 0.0
            else if (altezzaGamba in 100.1..115.0) penalita += 0.10
            else if (altezzaGamba in 115.1..130.0) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Gamba troppo bassa" }
        }

        // Verifica della tensione del ginocchio (gamba libera tesa e bloccata).
        if (isValida) {
            val tensioneGinocchio = PoseAnalyzer.calculateAngle3D(anca, ginocchio, caviglia)
            if (tensioneGinocchio >= 165.0) penalita += 0.0
            else if (tensioneGinocchio in 155.0..164.9) penalita += 0.10
            else if (tensioneGinocchio in 140.0..154.9) penalita += 0.30
            else { penalita += 0.50; isValida = false; msgErrore = "Ginocchio piegato" }
        }

        // Se a forza di sottrarre il valore base si annulla (o va in negativo),
        // l'intera esecuzione viene invalidata e il punteggio è scartato.
        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta (Valore nullo)"
        }

        // Confezionamento del Data Transfer Object (DTO) destinato al rendering a video
        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
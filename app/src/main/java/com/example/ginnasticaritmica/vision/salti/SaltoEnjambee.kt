package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Modulo per il "Salto Enjambée" (Grand Jeté).
 * Rappresenta la figura di volo fondamentale della ginnastica ritmica: una spaccata sagittale
 * eseguita al culmine della fase aerea, con la gamba anteriore tesa in avanti e la
 * posteriore tesa all'indietro.
 */
class SaltoEnjambee : Difficolta {
    override var nome = "SALTO ENJAMBÉE"


    // MEMORIA DI STATO ROTAZIONALE
    // Segure il movimento preparatorio dell'atleta. L'Enjambée (noto come "Jeté En Tournant"
    // quando eseguito con rotazione) raddoppia quasi il suo valore base se l'atleta gira
    // su se stessa immediatamente prima del decollo.
    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        // SCUDO ANTI-GIRO
        // Disambigua i salti dai Pivot. Se il semaforo "LucchettoGiri" è attivo,
        // l'atleta è ancorata a terra e l'inferenza balistica abortisce.
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


        // FIREWALL ANTI-EQUILIBRIO
        // Calcoliamo la lunghezza del femore (media bilaterale) per stabilire una scala
        // dimensionale dinamica, rendendo l'algoritmo immune allo zoom ottico o digitale della fotocamera.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)
        val femoreMedio = (femoreSx + femoreDx) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val lowestAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val hipOfLowestAnkle = if (isLeftLowest) leftHip else rightHip

        // SPACCATA VERTICALE VS ORIZZONTALE (Differenziale Altitudinale)
        val differenzaYCaviglie = kotlin.math.abs(leftAnkle.y() - rightAnkle.y())

        // Se la differenza di altezza tra le caviglie è grande (> 1.5 femori), significa
        // che un arto sta puntando verso il soffitto e l'altro verso il pavimento.
        // Questo identifica chiaramente un Equilibrio Penché, non un Salto Enjambée (che è orizzontale).
        if (differenzaYCaviglie > femoreMedio * 1.5f) {
            return null // Blocca il salto istantaneamente. Lascia l'inferenza alle classi di Equilibrio.
        }

        // PIEDE A TERRA
        val distAncaCavigliaY = lowestAnkle.y() - hipOfLowestAnkle.y()

        // Se l'arto inferiore più basso scende quasi verticalmente sull'asse Y (occupando lo spazio
        // di una gamba estesa), deduciamo che stia sorreggendo l'atleta.
        if (distAncaCavigliaY > femoreMedio * 1.8f) {
            return null // L'atleta è a terra. Assenza di fase balistica.
        }


        //  ANALISI DIREZIONE E TOURNANT
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f

        // Aggiorna lo storico rotazionale per l'assegnazione del moltiplicatore "En Tournant".
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // Assegnazione degli arti: Usa il naso (Sguardo) per capire la direzione del moto
        // e classifica dinamicamente gli arti in "Front" (Anteriore) e "Back" (Posteriore).
        val isFacingRight = landmarks[0].x() > bacinoX
        val isLeftFront = if (isFacingRight) leftAnkle.x() > rightAnkle.x() else leftAnkle.x() < rightAnkle.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        // Nel Grand Jeté/Enjambée la gamba anteriore deve essere tesa.
        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)
        if (frontKneeAngle < 130.0) return null

        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)


        //CALCOLO SPACCATA
        // Calcola i vettori planari delle cosce per misurare l'angolo geometrico.
        val vFrontX = frontAnkle.x() - frontHip.x()
        val vFrontY = frontAnkle.y() - frontHip.y()
        val vBackX = backKnee.x() - backHip.x()
        val vBackY = backKnee.y() - backHip.y()

        val dot = (vFrontX * vBackX) + (vFrontY * vBackY)
        val magF = kotlin.math.sqrt((vFrontX * vFrontX + vFrontY * vFrontY).toDouble())
        val magB = kotlin.math.sqrt((vBackX * vBackX + vBackY * vBackY).toDouble())

        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magF * magB)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0


        // CORREZIONE ORIZZONTALITÀ
        // Soluzione al problema delle inquadrature frontali.
        // Quando la spaccata sagittale è vista frontalmente, le gambe si "accorciano" visivamente
        // e la rete neurale sbaglia il calcolo dell'angolo Z (profondità).

        // Verifica matematica della divergenza: i vettori X delle gambe hanno segni opposti?
        val isOppositeHorizontal = (vFrontX > 0 && vBackX < 0) || (vFrontX < 0 && vBackX > 0)

        if (isOppositeHorizontal) {
            // Verifica altimetrica: se entrambe le estremità (caviglia avanti, ginocchio dietro)
            // sono allineate orizzontalmente col bacino sull'asse Y
            if (frontAnkle.y() <= bacinoY + 0.05f && backKnee.y() <= bacinoY + 0.05f) {
                // l'algoritmo forza l'angolo a 180°. Ignora la misurazione 3D distorta,
                // affidandosi alla logica euristica: se le gambe sono alte e opposte, la spaccata è corretta.
                angoloSpaccata2D = 180.0

            } else if (frontAnkle.y() <= bacinoY + 0.15f && backKnee.y() <= bacinoY + 0.15f) {
                //Gli arti sono quasi orizzontali, ma con una lievissima caduta di quota.
                // Si estrae il massimo, garantendo d'ufficio almeno 165° per evitare
                // decurtazioni dovute unicamente a un'angolazione di ripresa in parte occlusa.
                angoloSpaccata2D = kotlin.math.max(angoloSpaccata2D, 165.0)
            }
        }

        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontAnkle, centroBacino, backKnee)
        val angoloSpaccata = kotlin.math.max(angoloSpaccata2D, angoloSpaccata3D)

        if (angoloSpaccata < 130.0) return null

        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L


        // CLASSIFICAZIONE VARIANTI (Boucle / Flessione Dorsale)
        var valoreBase = 0.30
        val nose = landmarks[0]
        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)

        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spostamentoSpalleDietro = if (isFacingRight) bacinoX - spallaCentroX else spallaCentroX - bacinoX

        val schiacciamentoTroncoY = bacinoY - spallaCentroY
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        // identifica l'inarcamento misurando lo schiacciamento del busto (Cambré).
        val isFlessioneEvidente = distNasoBacino < 0.25f ||
                spostamentoSpalleDietro > 0.25f ||
                (schiacciamentoTroncoY > 0.0f && schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f) ||
                (schiacciamentoTroncoY < 0.0f) // Schiena piegata al di sotto del bacino.

        // Priorità all'elemento più complesso (Boucle, o Anello chiuso)
        val isBoucle = backKneeAngle <= 135.0 && distanzaTestaPiede <= 0.25f
        val isFlessione = !isBoucle && (isFlessioneEvidente || (backKneeAngle > 135.0 && distanzaTestaPiede <= 0.20f))

        // Generazione del nome formale della difficoltà
        if (isBoucle) {
            nome = if (isEnTournant) "SALTO JETÉ TOURNANT BOUCLE / AGIURG." else "SALTO ENJAMBÉE CON BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (isFlessione) {
            nome = if (isEnTournant) "SALTO JETÉ EN TOURNANT + FLESSIONE" else "SALTO ENJAMBÉE CON FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else {
            nome = if (isEnTournant) "SALTO JETÉ EN TOURNANT" else "SALTO ENJAMBÉE"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }


        //  PENALITÀ
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (angoloSpaccata >= 170.0) penalita += 0.0
        else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
        else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false // Drop Tecnico: Divaricazione insufficiente per configurare un salto
            msgErrore = "Apertura spaccata insufficiente"
        }

        // Controllo Forma Arto Posteriore:
        // Nel Jeté puro (Senza Boucle), la gamba dietro deve essere in totale estensione.
        // Se non vi è flessione del tronco a mascherare (isFlessioneEvidente), il piegamento è un errore.
        if (!nome.contains("BOUCLE") && isValida && !isFlessioneEvidente) {
            if (backKneeAngle < 150.0) {
                penalita += 0.30
                if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
            }
        }

        // Reset del timer rotazionale: la figura è certificata e convalidata.
        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
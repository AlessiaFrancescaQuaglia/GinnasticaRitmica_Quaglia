package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Modulo per il "Salto Biche" (Salto a Cervo).
 * I salti presentano una sfida opposta agli equilibri: non c'è staticità, c'è un fortissimo
 * Motion Blur e l'apice del salto dura spesso un solo fotogramma.
 * Inoltre, l'algoritmo deve mantenere uno storico temporale (Time-Series) per capire
 * se l'atleta ha eseguito una rotazione preparatoria (En Tournant) prima del decollo.
 */
class SaltoBiche : Difficolta {
    override var nome = "SALTO BICHE"

    // MEMORIA DI STATO ROTAZIONALE
    // Mantiene traccia dell'orientamento spaziale del torace nei frame precedenti al volo.
    // Cruciale per identificare la variante "En Tournant" (Salto con rotazione).
    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        // SCUDO ANTI-GIRO
        // Se l'orchestratore globale segnala un Pivot (giro su un piede) in corso,
        // è fisicamente impossibile che l'atleta sia in volo. L'inferenza viene bloccata a monte.
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

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)


        // NUOVO FIREWALL ANTI-EQUILIBRIO
        // Per distinguere un salto da una posa statica a terra, calcoliamo la lunghezza
        // 3D del femore e la usiamo come unità di misura scalare.
        // Se almenio una caviglia si trova vicina al pavimento (Y maggiore) con il ginocchio teso,
        // significa che sta sorreggendo il peso: l'atleta non è in fase aerea balistica.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Sicurezza addizionale per le pose in accosciata profonda (es. Equilibrio Cosacco)
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        // Se scatta anche solo un trigger di ancoraggio a terra, si esce dal valutatore salti.
        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null
        }


        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]


        //Mapping Asse X
        // Calcola l'orientamento frontale/posteriore utilizzando
        // la proiezione del naso rispetto al baricentro.
        val centroBacinoX = (leftHip.x() + rightHip.x()) / 2f
        val nose = landmarks[0]
        val isFacingRight = nose.x() > centroBacinoX

        // Rilevamento dell'Inversione Spalle per aggiornare lo storico rotazionale
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }

        // Assegnazione dinamica degli arti: determina quale gamba è davanti e quale dietro
        // proiettando le coordinate X nella direzione dello sguardo.
        val isLeftFront = if (isFacingRight) leftKnee.x() > rightKnee.x() else leftKnee.x() < rightKnee.x()

        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val backHip = if (isLeftFront) rightHip else leftHip
        val backKnee = if (isLeftFront) rightKnee else leftKnee
        val backAnkle = if (isLeftFront) rightAnkle else leftAnkle
        val backToe = if (isLeftFront) landmarks[32] else landmarks[31]

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)

        // Requisito Base: nel Biche (Salto a Cervo), l'arto anteriore deve essere
        // marcatamente flesso. Se è teso, la figura geometrica non sussiste.
        if (frontKneeAngle > 115.0 || frontKneeAngle < 35.0) return null


        // In un salto Biche, la forma si sviluppa orizzontalmente (sul piano sagittale).
        // La gamba posteriore (backKnee) va allungta all'indietro.
        // Se il ginocchio posteriore punta dritto verso il soffitto (Y del ginocchio minore della Y del bacino),
        // si tratta di un salto a sviluppo verticale (es. Salto Cosacco Alto)
        if (backKnee.y() < bacinoY - 0.05f) {
            return null // Non è un Biche, il thread cede l'esecuzione alla classe SaltoCosacco.
        }


        val centroBacinoY = bacinoY
        val centroBacino = NormalizedLandmark.create(centroBacinoX, centroBacinoY, 0f)


        // Intersechiamo il calcolo 3D con un calcolo planare 2D per estrarre l'ampiezza massima
        // effettiva e non penalizzare ingiustamente l'atleta.
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(frontKnee, centroBacino, backAnkle)

        val v1x = frontKnee.x() - centroBacinoX
        val v1y = frontKnee.y() - centroBacinoY
        val v2x = backAnkle.x() - centroBacinoX
        val v2y = backAnkle.y() - centroBacinoY
        val dotProduct = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
        val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dotProduct / (mag1 * mag2)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val angoloSpaccata = kotlin.math.max(angoloSpaccata3D, angoloSpaccata2D)

        // Scarto immediato di forme non idonee
        if (angoloSpaccata < 130.0) return null

        // Rilevamento Tournant: se l'atleta ha invertito le spalle negli ultimi 1.5 secondi
        // prima di spiccare il volo, il salto riceve il moltiplicatore di rotazione.
        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        val backKneeAngle = PoseAnalyzer.calculateAngle3D(backHip, backKnee, backAnkle)
        val distanzaTestaPiede = PoseAnalyzer.calculateDistance3D(nose, backToe)


        // SENSORE DI FLESSIONE DORSALE E BOUCLE
        // In volo, l'identificazione dell'Inarcamento è complessa per mancanza di un riferimento fisso.
        // Valutiamo lo spostamento del centro delle spalle verso la schiena e lo schiacciamento dell'asse Y (Naso-Bacino).
        val spallaCentroX = (spallaSx.x() + spallaDx.x()) / 2f
        val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f
        val spostamentoSpalleDietro = if (isFacingRight) centroBacinoX - spallaCentroX else spallaCentroX - centroBacinoX
        val schiacciamentoTroncoY = bacinoY - spallaCentroY
        val distNasoBacino = PoseAnalyzer.calculateDistance3D(nose, centroBacino)

        val isFlessioneEvidente = distNasoBacino < 0.25f ||
                spostamentoSpalleDietro > 0.25f ||
                (schiacciamentoTroncoY < 0.10f && spostamentoSpalleDietro > 0.05f)

        // La sottocategoria strutturale più complessa (Boucle) ha priorità di assegnazione.
        // Si verifica quando l'anello posteriore si chiude (Piede vicino alla Testa).
        val isBoucle = backKneeAngle <= 135.0 && distanzaTestaPiede <= 0.25f

        // Classificazione "Flessione Base": sussiste l'inarcamento, ma non vi è chiusura dell'anello.
        val isFlessione = !isBoucle && (isFlessioneEvidente || (backKneeAngle > 135.0 && distanzaTestaPiede <= 0.20f))

        // Albero Decisionale per il Valore Tecnico
        var valoreBase = 0.20

        if (isBoucle) {
            nome = if (isEnTournant) "BICHE 180° (o Tournant) + BOUCLE" else "SALTO BICHE + BOUCLE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else if (isFlessione) {
            nome = if (isEnTournant) "BICHE 180° (o Tournant) + FLESS." else "SALTO BICHE + FLESSIONE"
            valoreBase = if (isEnTournant) 0.60 else 0.40
        } else {
            nome = if (isEnTournant) "BICHE ROTAZIONE 180° (o Tournant)" else "SALTO BICHE"
            valoreBase = if (isEnTournant) 0.40 else 0.20
        }

        // MOTORE DI PENALITÀ
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        // Discretizzazione del deficit di ampiezza spaccata
        if (angoloSpaccata >= 170.0) penalita += 0.0
        else if (angoloSpaccata in 160.0..169.9) penalita += 0.10
        else if (angoloSpaccata in 140.0..159.9) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Apertura spaccata insufficiente"
        }

        // Penalità specifica: nel Biche (non-Boucle), la gamba posteriore DEVE essere tesa.
        // Se è piegata e non è dichiarabile un "Boucle" per mancanza di flessione della schiena, scatta l'errore.
        if (!nome.contains("BOUCLE") && isValida && backKneeAngle < 150.0 && !isFlessioneEvidente) {
            penalita += 0.30
            if (msgErrore.isEmpty()) msgErrore = "Gamba posteriore piegata"
        }

        // Reset del buffer rotazionale a salto validato per prepararsi al prossimo elemento della routine.
        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
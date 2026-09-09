package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Modulo di inferenza balistica per il "Salto Butterfly" (Salto Farfalla).
 * Il butterfly un salto in cui il tronco è tenuto orizzontale (parallelo al suolo)
 * mentre le gambe eseguono una apertura trasversale in volo.
 * È uno dei salti più complessi da identificare a causa
 * dell'estremo ribaltamento degli assi corporei (il corpo forma una "T" vista dall'alto).
 */
class SaltoButterfly : Difficolta {
    override var nome = "SALTO BUTTERFLY"


    // MEMORIA DI STATO E PEAK DETECTION
    // Le variabili globali mantengono vivo l'elemento per 1 secondo,
    // aggiornando continuamente "bestApertura" per registrare l'angolo massimo raggiunto
    // prima dell'atterraggio, garantendo il punteggio più favorevole all'atleta.
    companion object {
        private var tempoUltimoButterfly: Long = 0L
        private var bestApertura = 0.0
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        //SCUDO ANTI-GIRO
        // Se il Semaforo Globale (Lucchetto) è impegnato da un Pivot (rotazione a terra),
        // l'atleta non è in volo.
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

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)


        // FIREWALL ANTI-EQUILIBRIO (Sensore di Fase Aerea)
        // Misurazione scalare basata sul femore per determinare la distanza dal suolo.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Threshold di sicurezza per identificare l'ancoraggio anche in pose accosciate
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        // Inversione logica: il salto esiste solo se entrambi i piedi non sono a terra.
        val inVolo = !(isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted)

        if (!inVolo) {
            // Se l'atleta tocca terra, la memoria del salto si azzera.
            tempoUltimoButterfly = 0L
            bestApertura = 0.0
            return null
        }

        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)


        // CALCOLO IBRIDO DELL'APERTURA (Fusione 2D/3D)
        // Il Butterfly si sviluppa in parallelo al suolo. Se la telecamera guarda dal basso,
        // la spaccata perde profondità (asse Z). Incrociamo i dati dell'angolo 3D con il
        // prodotto scalare sul piano XY (2D) ed estraiamo il valore massimo.
        val angoloSpaccata3D = PoseAnalyzer.calculateAngle3D(leftAnkle, centroBacino, rightAnkle)

        val vLeftX = leftAnkle.x() - bacinoX
        val vLeftY = leftAnkle.y() - bacinoY
        val vRightX = rightAnkle.x() - bacinoX
        val vRightY = rightAnkle.y() - bacinoY
        val dot = (vLeftX * vRightX) + (vLeftY * vRightY)
        val magL = kotlin.math.sqrt((vLeftX * vLeftX + vLeftY * vLeftY).toDouble())
        val magR = kotlin.math.sqrt((vRightX * vRightX + vRightY * vRightY).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (magL * magR)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val aperturaReale = max(angoloSpaccata3D, angoloSpaccata2D)

        // Verifica se l'algoritmo è già nella fase di validazione
        val isButterflyAttivo = (System.currentTimeMillis() - tempoUltimoButterfly < 1000L) && tempoUltimoButterfly != 0L

        if (!isButterflyAttivo) {

            // BOOTSTRAPPING (Validazione Topologica Iniziale)
            val spallaSx = landmarks[11]
            val spallaDx = landmarks[12]
            val spallaCentroY = (spallaSx.y() + spallaDx.y()) / 2f

            //Sensore di Orizzontalità (Corpo Eretto):
            // In un Butterfly, le spalle devono essere vicine al bacino sull'asse Y (Busto piatto).
            // Se le spalle sono molto più alte del bacino, è un salto verticale (scarto).
            if (bacinoY - spallaCentroY > 0.16f) return null

            // Almeno una gamba deve essere lanciata e discretamente tesa per conferire slancio.
            val gambaMigliore = max(leftKneeAngle, rightKneeAngle)
            if (gambaMigliore < 120.0) return null

            // Analisi del Piano di Spaccata (Differenziale XY):
            // L'arcotangente (atan2) calcola l'inclinazione del segmento che unisce le due caviglie.
            // Se supera i 35°, significa che una gamba è molto più alta dell'altra (Piano Verticale -> Grand Jeté).
            // Il Butterfly esige un piano di spaccata orizzontale.
            val dyCaviglie = kotlin.math.abs(leftAnkle.y() - rightAnkle.y())
            val dxCaviglie = kotlin.math.abs(leftAnkle.x() - rightAnkle.x())
            val inclinazioneSpaccata = Math.toDegrees(kotlin.math.atan2(dyCaviglie.toDouble(), dxCaviglie.toDouble()))
            if (inclinazioneSpaccata > 35.0) return null

            //Morfologia a "T" rovesciata:
            // Le caviglie devono sollevarsi superando (o eguagliando) la linea del bacino.
            if (leftAnkle.y() > bacinoY + 0.12f || rightAnkle.y() > bacinoY + 0.12f) return null


            // MISURATORE DELLA SPINA DORSALE
            // Risoluzione di un Falso Positivo severo: un salto verticale con forte inarcamento
            // della schiena all'indietro (Cambré) schiaccia la distanza Y tra spalle e bacino,
            // ingannando il "Sensore di Orizzontalità".
            //Calcoliamo quindi la distanza 3D Naso-Bacino e la confrontiamo con la lunghezza del tronco.
            val spallaCentro = NormalizedLandmark.create(
                (spallaSx.x() + spallaDx.x()) / 2f,
                spallaCentroY,
                (spallaSx.z() + spallaDx.z()) / 2f
            )

            val lunghezzaTronco = PoseAnalyzer.calculateDistance3D(spallaCentro, centroBacino)
            val distNasoBacino = PoseAnalyzer.calculateDistance3D(landmarks[0], centroBacino)

            // Se il naso si avvicina al bacino in modo anomalo (meno di 1.25 volte il tronco),
            // la ginnasta si sta flettendo all'INDIETRO. Il Butterfly richiede busto in avanti/piatto.
            // Il processo di innesco viene soppresso.
            if (distNasoBacino < lunghezzaTronco * 1.25f) return null

            // Innesco del salto se l'apertura iniziale supera una soglia minima (90°)
            if (aperturaReale > 90.0) {
                tempoUltimoButterfly = System.currentTimeMillis()
                bestApertura = aperturaReale
            }

        } else {

            // FASE DI TRACKING (Aggiornamento Apice)
            // Il salto è già validato. Per 1000ms monitoriamo ogni frame e sovrascriviamo
            // "bestApertura" solo se l'atleta espande ulteriormente la spaccata in volo.
            if (aperturaReale > bestApertura) {
                bestApertura = aperturaReale
            }
        }

        val isAttivoOra = (System.currentTimeMillis() - tempoUltimoButterfly < 1000L) && tempoUltimoButterfly != 0L


        // RESTITUZIONE PUNTEGGIO E PENALITÀ
        if (isAttivoOra) {
            val valoreBase = 0.50
            var penalita = 0.0
            var isValida = true
            var msgErrore = ""

            // Usa il valore massimo registrato nel buffer (bestApertura) per applicare le decurtazioni
            if (bestApertura >= 145.0) penalita += 0.0
            else if (bestApertura >= 125.0) penalita += 0.10
            else if (bestApertura >= 100.0) penalita += 0.30
            else {
                penalita += 0.50
                isValida = false
                msgErrore = "Apertura divaricata insufficiente"
            }

            return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
        }

        return null
    }
}
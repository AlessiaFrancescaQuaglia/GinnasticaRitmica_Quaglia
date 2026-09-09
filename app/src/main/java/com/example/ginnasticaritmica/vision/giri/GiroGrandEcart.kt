package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.max

/**
 * Modulo di inferenza per il "Pivot Grand Écart" (Rotazione in spaccata verticale).
 * L'atleta esegue un giro su un piede mantenendo un'apertura delle gambe di 180°.
 * La criticità ingegneristica di questa classe è il filtraggio del rumore: una spaccata in
 * rotazione è altamente soggetta a deformazione prospettica (effetto "strobo" degli arti).
 * Il sistema implementa un robusto pattern "Latch" (Aggancio di Stato) e un filtro Early-Exit
 * per distinguere istantaneamente questa posa da rotazioni affini (come il Giro Penché).
 */
class GiroGrandEcart : Difficolta {
    override var nome = "PIVOT GRAND ÉCART"


    // MACCHINA A STATI FINITI
    // memoria temporale necessaria per valutare un gesto dinamico.
    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrame = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // Memorie bloccate
        // Memorizzano le condizioni iniziali (es. l'uso della mano per sorreggere la gamba)
        // per evitare che vengano perse se le braccia vengono occluse dal corpo durante il giro.
        private var isFormaBloccata = false
        private var haUsatoAiutoLocked = false
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee


        //RESET ISTANTANEO
        // Prevenzione falsi positivi: se i piedi sono alla stessa altezza (delta Y minimo),
        // l'atleta è a terra. Si interrompe l'analisi.
        if (abs(liftedAnkle.y() - supportAnkle.y()) < 0.04f) {
            resetGiro()
            return null
        }

        // Firewall Anti-Penché
        // Nel Grand Écart il busto deve rimanere perfettamente verticale (Spalle sopra il bacino).
        // Se le spalle crollano in avanti, si tratta di un Giro Penché. Abortisce la classe corrente.
        if (spallaY > bacinoY + 0.05f) {
            resetGiro()
            return null
        }


        // Assicura che una sola classe "Giro" sia attiva nel pool di thread di inferenza.
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null


        // VERIFICA POSA E  FORMA

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        // Fusione sensoriale 2D/3D per mitigare l'occlusiuone prospettica della spaccata
        // durante le fasi di profilo verso la telecamera.
        val dx1 = supportAnkle.x() - bacinoCenterX
        val dy1 = supportAnkle.y() - bacinoY
        val dx2 = liftedAnkle.x() - bacinoCenterX
        val dy2 = liftedAnkle.y() - bacinoY
        val dot = (dx1 * dx2) + (dy1 * dy2)
        val mag1 = kotlin.math.sqrt((dx1 * dx1 + dy1 * dy1).toDouble())
        val mag2 = kotlin.math.sqrt((dx2 * dx2 + dy2 * dy2).toDouble())
        var angoloSpaccata2D = Math.toDegrees(kotlin.math.acos(dot / (mag1 * mag2)))
        if (angoloSpaccata2D.isNaN()) angoloSpaccata2D = 0.0

        val midHipLandmark = NormalizedLandmark.create(bacinoCenterX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
        val aperturaSpaccata3D = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)

        // Estrazione del valore massimo tra la misurazione planare e spaziale
        val aperturaSpaccata = max(angoloSpaccata2D, aperturaSpaccata3D)

        if (!isFormaBloccata) {
            // l'algoritmo esige la perfezione biomeccanica per iniziare a contare.
            val isBustoEretto = spallaY < bacinoY - 0.05f
            val isSpaccataAmpia = aperturaSpaccata >= 150.0 // Margine elastico per i 180°
            val isGinocchioTeso = liftedKneeAngle >= 155.0
            val isGambaAltissima = liftedAnkle.y() < spallaY + 0.15f // Il piede deve superare le spalle

            if (!(isBustoEretto && isSpaccataAmpia && isGinocchioTeso && isGambaAltissima)) return null

            // Sensore di Prossimità per Astrazione "Con Aiuto" (Mano-Piede)
            val distPolsoSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            haUsatoAiutoLocked = distPolsoSx < 0.15f || distPolsoDx < 0.15f

            //Congelamento della classificazione iniziale (immunità al rumore )
            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)
            timestampUltimoFrame = System.currentTimeMillis()
        } else {
            // Fase di Mantenimento: tolleranze allargate.
            // A causa della forza centrifuga, la spaccata perde inevitabilmente gradi (fino a 120° ammessi).
            val tolleranzaTempo = if (mezziGiriCompletati >= 2) 1200L else 500L

            // Finché mantiene un po' di spaccata, teniamo vivo il giro
            if (aperturaSpaccata > 120.0) {
                timestampUltimoFrame = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - timestampUltimoFrame > tolleranzaTempo) {
                // Timeout superato: l'atleta ha chiuso definitivamente le gambe.
                resetGiro()
                return null
            }
        }


        //MOTORE A SCAMBIO DI ASSE - Conteggio Rotazione
        // Un'inversione di polarità (Spalla Dx supera Spalla Sx sul piano 2D) equivale a mezzo giro.
        val dxSpalle = rightShoulder.x() - leftShoulder.x()

        val sogliaSwap = 0.015f // Deadzone per prevenire vibrazioni ottiche
        var nuovoStato = statoFronteRetro

        if (dxSpalle > sogliaSwap) {
            nuovoStato = 1
        } else if (dxSpalle < -sogliaSwap) {
            nuovoStato = -1
        }

        if (statoFronteRetro == 0 && nuovoStato != 0) {
            // Inizializzazione Tracking Spaziale
            statoFronteRetro = nuovoStato
            isGiroInCorso = true
            timestampUltimoSwap = System.currentTimeMillis()
        } else if (statoFronteRetro != 0 && nuovoStato != statoFronteRetro) {
            val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap
            // Filtro passa-basso contro letture multiple istantanee
            if (tempoDalUltimoSwap > 200L) {
                mezziGiriCompletati++
                statoFronteRetro = nuovoStato
                timestampUltimoSwap = System.currentTimeMillis()
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0) return null


        // RESTITUZIONE PUNTEGGIO
        // valore differenziato in base alle posizioni (tenuta della gamba con mano o meno).
        val valoreBaseDifficolta = if (haUsatoAiutoLocked) 0.30 else 0.50
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        val etichettaGiro = if (haUsatoAiutoLocked) "CON AIUTO (0.30)" else "LIBERO (0.50)"

        if (giriCompletati >= 1) {
            nome = "PIVOT GRAND ÉCART $etichettaGiro ($giriCompletati Giri)"
        } else {
            nome = "PIVOT GRAND ÉCART $etichettaGiro (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    /**
     * Svuota la memoria a stati e rilascia i lock per abilitare valutazioni future.
     */
    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        haUsatoAiutoLocked = false
        timestampUltimoSwap = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
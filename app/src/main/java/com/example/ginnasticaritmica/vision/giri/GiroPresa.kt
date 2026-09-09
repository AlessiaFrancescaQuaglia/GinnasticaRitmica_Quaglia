package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Modulo dedicato all'inferenza del "Giro Presa" (Pivot boucle con aiuto delle mani).
 * La ginnasta esegue una rotazione sostenendo attivamente la gamba sollevata.
 * Il sistema deve dimostrare l'avvenuto contatto fisico tra le mani (polsi) e l'arto inferiore
 * (caviglia/ginocchio) prima di autorizzare la valutazione della figura.
 */
class GiroPresa : Difficolta {
    override var nome = "GIRO PRESA CON AIUTO"


    // MEMORIA DI STATO DELLA MACCHINA ROTAZIONALE
    companion object {
        // Registri per il tracciamento angolare
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrame = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // Latch di Validazione: bloccano la classificazione sub-strutturale (es. Boucle)
        // isolandola dalle deformazioni durante il giro.
        private var isFormaBloccata = false
        private var isBoucleLocked = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Gestione Concorrenza (Mutex): previene confusione con altre classi di rotazione.
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

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
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee


        // FIREWALL BASE e RESET DINAMICO
        // Utilizza il tronco come unità di misura dinamica per validare l'ancoraggio a terra.
        val scalaCorpoX = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)
        if (supportAnkle.y() < bacinoY + (scalaCorpoX * 0.2f)) {
            resetGiro()
            return null
        }

        // Scudo Posturale: Un giro "Presa" (Dietro) impone un busto eretto o inarcato.
        // Se le spalle crollano in avanti, l'algoritmo invalida immediatamente il frame.
        if (spallaY > bacinoY - 0.15f) {
            resetGiro()
            return null
        }

        // Watchdog Timer per la perdita di quota della gamba.
        val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()

        if (liftedAnkle.y() > ginocchioSupportoY) {
            if (!isFormaBloccata) {
                resetGiro()
                return null
            } else {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis() // Innesco
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    // Tolleranza superata: perdita di equilibrio confermata
                    resetGiro()
                    return null
                }
            }
        } else {
            timestampGambaGiu = 0L // Riarmo
        }


        // VERIFICA PRESENZA AIUTO E FORMA

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        if (!isFormaBloccata) {
            // Sensore di Intersezione 3D (Collision Detection)
            // L'algoritmo calcola la Distanza Euclidea tra le mani (polsi) e i fulcri
            // articolari della gamba sollevata (caviglia e ginocchio).
            val distPolsoCavigliaSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoCavigliaDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            val distPolsoGinocchioSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedKnee)
            val distPolsoGinocchioDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedKnee)

            // Condizione di attivazione (OR logico): almeno una mano deve essere in prossimità della gamba.
            val haPresa = (distPolsoCavigliaSx < 0.20f || distPolsoCavigliaDx < 0.20f ||
                    distPolsoGinocchioSx < 0.20f || distPolsoGinocchioDx < 0.20f)

            if (!haPresa) return null

            val isGambaAlta = liftedAnkle.y() < bacinoY || liftedKnee.y() < bacinoY
            val isBustoErettoOInarcato = spallaY < bacinoY - 0.15f

            if (!(isGambaAlta && isBustoErettoOInarcato)) return null

            // Determina se la presa genera un "Boucle" (gamba piegata)
            // o un "Grand Écart" (gamba tesa), e congela il verdetto nel Latch.
            isBoucleLocked = liftedKneeAngle < 140.0

            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true
            LucchettoGiri.blocca(idGiroFisso)
            timestampUltimoFrame = System.currentTimeMillis()
        } else {
            // Mantenimento
            val tolleranzaTempo = if (mezziGiriCompletati >= 2) 1000L else 500L
            if (liftedAnkle.y() < bacinoY + 0.15f || liftedKnee.y() < bacinoY + 0.15f) {
                timestampUltimoFrame = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - timestampUltimoFrame > tolleranzaTempo) {
                resetGiro()
                return null
            }

            // Sgancio di sicurezza
            if (isGiroInCorso && System.currentTimeMillis() - timestampUltimoSwap > 1500L) {
                resetGiro()
                return null
            }
        }


        //MOTORE  X-SWAP
        // Rilevamento dello Zero-Crossing per il conteggio angolare.
        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.015f, scalaCorpoX * 0.08f)

        val nuovoStato = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else statoFronteRetro

        if (statoFronteRetro == 0 && nuovoStato != 0) {
            statoFronteRetro = nuovoStato
            isGiroInCorso = true
            timestampUltimoSwap = System.currentTimeMillis()
        } else if (statoFronteRetro != 0 && nuovoStato != 0 && nuovoStato != statoFronteRetro) {
            val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap

            // Debouncing: calibrato al ribasso rispetto ad altri giri (150ms)
            // poiché la "Presa" compatta la massa corporea dell'atleta vicino all'asse di rotazione,
            // aumentandola velocità angolare (conservazione del momento angolare).
            if (tempoDalUltimoSwap > 80L) {
                mezziGiriCompletati++
                statoFronteRetro = nuovoStato
                timestampUltimoSwap = System.currentTimeMillis()
                timestampUltimoFrame = System.currentTimeMillis()
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0) return null


        // RESTITUZIONE PUNTEGGIO
        val valoreBaseDifficolta = 0.30
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        // Ricostruzione dinamica dell'identità della figura in base ai Latch acquisiti
        val nomeForma = if (isBoucleLocked) "BOUCLE CON AIUTO" else "GRAND ÉCART INDIETRO C. AIUTO"
        val etichettaFinale = "GIRO $nomeForma (0.30)"

        nome = if (giriCompletati >= 1) {
            "$etichettaFinale ($giriCompletati Giri)"
        } else {
            "$etichettaFinale (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    /**
     * Resetta la Macchina a Stati e libera il Mutex rotazionale.
     */
    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        isBoucleLocked = false
        gambaDiSupportoIsLeft = null
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
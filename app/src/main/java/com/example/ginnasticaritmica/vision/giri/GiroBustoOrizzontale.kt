package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Modulo di analisi per i "Pivot Busto Orizzontale".
 * Analizza rotazioni in cui l'atleta flette il tronco a 90° (parallelo al suolo).
 * Computazionalmente, questa classe gestisce una metrica di punteggio diversa:
 * a differenza degli altri giri (che valgono punti interi ogni 360°), il Codice dei Punteggi
 * qui premia anche i mezzi giri (180°).
 */
class GiroBustoOrizzontale : Difficolta {
    override var nome = "PIVOT BUSTO ORIZZONTALE"


    // MEMORIA DI STATO DELLA MACCHINA ROTAZIONALE
    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0

        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // Registri per il riconoscimento delle sottocategorie di Pivot (Es. Raffaeli)
        private var haUsatoAiuto = false
        private var isGambaFlessaLocked = false
        private var isFormaBloccata = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Gestione Concorrenza
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        // Mappatura vettori anatomici
        val nose = landmarks[0]
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

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)


        // FIREWALL BASE e RESET DINAMICO

        if (!isFormaBloccata) {
            // Anti-Capriola: la caviglia di supporto deve rimanere ancorata al pavimento.
            if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.2f)) {
                resetGiro()
                return null
            }

            // ANTI-PENCHÉ GRAND ECART: Il busto deve essere orizzontale,
            // ma non eccessivamente proiettato verso il basso come in una spaccata verticale completa.
            if (spallaY > bacinoY + (scalaCorpo * 0.6f)) {
                resetGiro()
                return null
            }
        }

        // Controllo e Timer sulla caduta della gamba libera
        val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()
        if (liftedAnkle.y() > ginocchioSupportoY) {
            if (!isFormaBloccata) {
                resetGiro()
                return null
            } else {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    if (!isGiroInCorso) {
                        resetGiro()
                        return null
                    }
                }
            }
        } else {
            timestampGambaGiu = 0L // Reset del timer di caduta
        }



        // VERIFICA POSA E FORMA
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val dxSpalle = rightShoulder.x() - leftShoulder.x()

        // SENSIBILITÀ SULL'ASSE X:
        // Poiché il busto orizzontale rende le spalle meno visibili e spesso parzialmente occluse,
        // la soglia dello swap delle spalle viene calcolata dinamicamente.
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        // Prima Acquisizione
        if (!isFormaBloccata) {
            val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
            val midHipLandmark = NormalizedLandmark.create(bacinoCenterX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)
            val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)

            val isBustoOrizzontale = spallaY > bacinoY - (scalaCorpo * 0.4f)

            // Regola del "Raffaeli" (Giro inventato dalla ginnasta omonima):
            // gamba sopra piegata e inarcamento laterale (distanza naso-caviglia elevata,
            // indicando che non è un Anello chiuso).
            val isRaffaeli = (liftedKneeAngle < 145.0) && (distNasoCaviglia >= (scalaCorpo * 0.6f))

            // Determina la sussistenza dei requisiti altimetrici per la gamba sollevata.
            val isGambaAlta = if (isRaffaeli) {
                liftedKnee.y() < bacinoY + (scalaCorpo * 0.2f) // Criterio per Raffaeli (misura ginocchio)
            } else {
                (aperturaSpaccata >= 120.0) && (liftedAnkle.y() < bacinoY + (scalaCorpo * 0.2f)) // Criterio spaccata tesa
            }

            if (!(isBustoOrizzontale && isGambaAlta)) return null

            // Fissa le variabili biomeccaniche, isolandole dalle alterazioni ottiche provocate dalla rotazione
            isGambaFlessaLocked = isRaffaeli
            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true

            // Acquisisce il blocco del Singleton Mutex
            LucchettoGiri.blocca(idGiroFisso)

            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()

            // L'algoritmo attiva istantaneamente la registrazione
            // del giro per non perdere preziosi frazioni di grado angolare(avendo valore ogni 180°).
            isGiroInCorso = true
        } else {
            // Monitoraggio persistenza del busto (deve rimanere orizzontale)
            val isBustoAncoraGiu = spallaY > bacinoY - (scalaCorpo * 0.7f)
            if (isBustoAncoraGiu) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > 1500L) {
                    resetGiro() // L'atleta si è raddrizzata, fine elemento
                    return null
                }
            }

            // Timeout di sicurezza assoluto
            if (System.currentTimeMillis() - timestampUltimoSwap > 2000L) {
                resetGiro()
                return null
            }
        }


        //sENSORE AIUTO (Sostegno con mano)

        // Verifica una tantum se l'atleta afferra il polpaccio per aiutarsi.
        if (!isFormaBloccata) {
            val distPolsoSx = PoseAnalyzer.calculateDistance3D(leftWrist, liftedAnkle)
            val distPolsoDx = PoseAnalyzer.calculateDistance3D(rightWrist, liftedAnkle)
            if (distPolsoSx < (scalaCorpo * 0.8f) || distPolsoDx < (scalaCorpo * 0.8f)) haUsatoAiuto = true
        }


        // Conteggio Mezzi Giri 180°
        // L'inversione dell'asse X delle spalle segna l'avvenuta rotazione di 180°.
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
            } else if (statoFronteRetro != 0 && statoAttualeSpalle != 0 && statoAttualeSpalle != statoFronteRetro) {
                val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap

                //filtro temporale antirumore
                if (tempoDalUltimoSwap > 150L) {
                    mezziGiriCompletati++
                    statoFronteRetro = statoAttualeSpalle
                    timestampUltimoSwap = System.currentTimeMillis()

                    timestampUltimoFrameBusto = System.currentTimeMillis()
                    timestampGambaGiu = 0L
                }
            }
        }

        if (mezziGiriCompletati == 0 && !isFormaBloccata) return null


        //CALCOLO E RESTITUZIONE PUNTEGGIO
        // Calcolo logaritmico-lineare del punteggio basato sulla frequenza dei mezzi giri,
        val valoreBaseDifficolta = if (haUsatoAiuto) 0.40 else 0.50
        val valoreTotale = if (mezziGiriCompletati >= 1) valoreBaseDifficolta + ((mezziGiriCompletati - 1) * 0.10) else 0.0

        val titoloBase = if (isGambaFlessaLocked) "PIVOT RAFFAELI" else "PIVOT ELKATIB/LAT."
        val etichettaAiuto = if (haUsatoAiuto) "(Con Aiuto)" else "(Libero)"

        nome = if (mezziGiriCompletati >= 1) {
            val gradiTesto = mezziGiriCompletati * 180
            "$titoloBase $etichettaAiuto ($gradiTesto°)"
        } else {
            "$titoloBase $etichettaAiuto (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    /**
     * Ripristino Totale dello Stato.
     * Svuota la memoria e libera il Mutex per i cicli di inferenza successivi.
     */
    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        haUsatoAiuto = false
        isGambaFlessaLocked = false
        isFormaBloccata = false
        gambaDiSupportoIsLeft = null
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
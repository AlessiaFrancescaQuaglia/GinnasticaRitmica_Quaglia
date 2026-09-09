package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/** Modulo dedicato al riconoscimento del giro Arabesque
 * A differenza degli equilibri, l'analisi di un giro richiede il monitoraggio
 * continuo del tensore 3D nel dominio del tempo.
 * L'architettura è basata su una Macchina a Stati Finiti (FSM) accoppiata a un
 * motore di Isteresi per il conteggio dei giri.
 */
class GiroArabesque : Difficolta {
    override var nome = "GIRO ARABESQUE"

    // MEMORIA PERSISTENTE
    // Il blocco companion funge da memoria statica che sopravvive tra i frame.
    // Registra lo storico del movimento rotazionale a 30 FPS.
    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0

        // Watchdog Timers: meccanismi di tolleranza ai guasti.
        // Se il tracker perde temporaneamente un arto a causa del motion blur,
        // questi timer impediscono che l'intero giro venga invalidato istantaneamente.
        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // Blocco di Stato:
        // Registra le caratteristiche biomeccaniche del giro (es. busto flesso)
        // nell'istante del decollo, congelandole in memoria per l'intera durata della rotazione.
        private var isFormaBloccata = false
        private var isAttitudeLocked = false
        private var isBustoOrizzontaleLocked = false
        private var isBustoIndietroLocked = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
        private var dxSpalleIniziale = 0f
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // GESTIONE DELLA CONCORRENZA
        // Interroga il "LucchettoGiri". Se un'altra classe
        // sta già tracciando una rotazione, questo thread abortisce per evitare conflitti.
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoCenterZ = (leftHip.z() + rightHip.z()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val spallaCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val spallaCenterZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        // Variabile di stato primaria per l'algoritmo di Swap
        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0


        // vERIFICA POSA

        if (!isFormaBloccata) {

            if (supportAnkle.y() < bacinoY + (scalaCorpo * 0.2f)) return null

            //SCUDO ANTI-DÉGAGÉ
            //Poiché l'atleta ruota, i classici controlli sull'asse X non funzionano
            // (quando è di spalle, destra e sinistra si invertono).
            // L'algoritmo calcola il piano frontale del torace mediante un Prodotto Vettoriale

            // Vettore Spina Dorsale (dal bacino alle spalle)
            val spineX = spallaCenterX - bacinoCenterX
            val spineY = spallaY - bacinoY
            val spineZ = spallaCenterZ - bacinoCenterZ

            // Vettore Trasversale Fianchi
            val hipX = rightHip.x() - leftHip.x()
            val hipY = rightHip.y() - leftHip.y()
            val hipZ = rightHip.z() - leftHip.z()

            // Prodotto Vettoriale (Spine x Hip) = Vettore Normale Ortogonale.
            // Questo vettore punta in avanti rispetto al petto dell'atleta,
            // indipendentemente dall'orientamento della telecamera.
            val forwardX = spineY * hipZ - spineZ * hipY
            val forwardY = spineZ * hipX - spineX * hipZ
            val forwardZ = spineX * hipY - spineY * hipX

            //Vettore della gamba sollevata (dal bacino al ginocchio)
            val legX = liftedKnee.x() - bacinoCenterX
            val legY = liftedKnee.y() - bacinoY
            val legZ = liftedKnee.z() - bacinoCenterZ

            // Prodotto Scalare
            // Interseca il vettore Normale con il vettore della Gamba.
            // Se il risultato è positivo, la gamba si trova nel semispazio anteriore (Dégagé).
            // Se è negativo, si trova nel semispazio posteriore (Arabesque/Attitude).
            val dotFrontBack = forwardX * legX + forwardY * legY + forwardZ * legZ

            if (dotFrontBack > -0.01f) {
                return null // Rigetta l'ipotesi Arabesque
            }

            // CONTROLLI ARABESQUE STANDARD
            val isKneeAlto = liftedKnee.y() < bacinoY + (scalaCorpo * 0.5f)
            if (!isKneeAlto) return null

            val distNasoCaviglia = PoseAnalyzer.calculateDistance3D(nose, liftedAnkle)
            val distPiedeGinocchio = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)

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
            val aperturaSpaccata = max(angoloSpaccata2D, aperturaSpaccata3D)

            val isBustoInclinato = spallaY > bacinoY - (scalaCorpo * 0.45f)
            val isSpaccataPenche = aperturaSpaccata >= 115.0
            val isBoucle = (liftedKneeAngle < 145.0) && (distNasoCaviglia < (scalaCorpo * 1.5f))

            // Firewall contro collisioni con altre classi di rotazione
            if (isBustoInclinato && (isSpaccataPenche || isBoucle)) return null
            if (spallaY > bacinoY + (scalaCorpo * 0.05f)) return null

            val isGambaFlessa = liftedKneeAngle < 145.0
            val isPiedeMoltoAlto = liftedAnkle.y() < bacinoY - (scalaCorpo * 0.1f)
            if (isGambaFlessa && isPiedeMoltoAlto) return null
            if (liftedKneeAngle < 110.0 && distPiedeGinocchio < (scalaCorpo * 0.9f)) return null


            // L'AI certifica l'inizio del giro e congela le caratteristiche anatomiche base.
            // Durante i giri (a causa della forza centrifuga) la posa subisce modifiche.
            // Questo blocco fissa il punteggio base per non essere penalizzato dalle occlusioni visive.
            isAttitudeLocked = liftedKneeAngle < 145.0
            isBustoOrizzontaleLocked = spallaY > bacinoY - (scalaCorpo * 0.3f)

            val dxSpalleCentrale = spallaCenterX - bacinoCenterX
            val dzSpalleCentrale = spallaCenterZ - bacinoCenterZ
            val dxGamba = liftedAnkle.x() - bacinoCenterX
            val dzGamba = liftedAnkle.z() - bacinoCenterZ
            isBustoIndietroLocked = (dxSpalleCentrale * dxGamba) + (dzSpalleCentrale * dzGamba) > 0.0f

            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true

            // Inizializzazione della rotazione
            dxSpalleIniziale = dxSpalle
            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()
        } else {

            // MANTENIMENTO ROTAZIONE
            // Valuta se l'atleta sta effettivamente acquisendo inerzia
            // (delta sulle spalle > 15% del corpo) per attivare i sistemi di conteggio.
            if (!isGiroInCorso) {
                if (kotlin.math.abs(dxSpalle - dxSpalleIniziale) > scalaCorpo * 0.15f) {
                    isGiroInCorso = true
                }
            }

            // Watchdog Timer Gamba:
            // L'atleta può perdere fisiologicamente quota con la gamba durante il giro.
            // Il sistema implementa un timer elastico (800ms) prima di dichiarare terminato
            // l'elemento, assorbendo eventuali micro-cedimenti
            val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()
            if (liftedAnkle.y() > ginocchioSupportoY) {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    resetGiro()
                    return null
                }
            } else {
                timestampGambaGiu = 0L // Reset contatore se la gamba viene rialzata
            }

            // Watchdog Generale Inattività:
            // Se la rotazione si blocca il sistema sblocca forzatamente
            // il Mutex dopo 1.5 - 2 secondi di staticità.
            val isKneeAncoraSu = liftedKnee.y() < bacinoY + (scalaCorpo * 0.8f)
            if (isKneeAncoraSu) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                val tolleranzaTempo = if (isGiroInCorso) 1500L else 800L
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > tolleranzaTempo) {
                    resetGiro()
                    return null
                }
            }

            // Safety timeout assoluto (2000ms dall'ultimo giro registrato)
            if (isGiroInCorso && System.currentTimeMillis() - timestampUltimoSwap > 2000L) {
                resetGiro()
                return null
            }
        }


        // MOTORE HYSTERESIS (Conteggio Pivot)
        // Ogni volta che la spalla destra e sinistra invertono la loro posizione a video,
        // l'algoritmo registra un 1/2 giro.
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
                isGiroInCorso = true
            } else if (statoFronteRetro != 0 && statoAttualeSpalle != 0 && statoAttualeSpalle != statoFronteRetro) {
                val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap


                // Inserisce un periodo refrattario di 150ms per annullare il rumore biomeccanico
                // e le rapide fluttuazioni ottiche che genererebbero conteggi "falsi" istantanei.
                if (tempoDalUltimoSwap > 150L) {
                    mezziGiriCompletati++
                    statoFronteRetro = statoAttualeSpalle
                    timestampUltimoSwap = System.currentTimeMillis()
                    timestampUltimoFrameBusto = System.currentTimeMillis()
                    timestampGambaGiu = 0L
                    isGiroInCorso = true
                }
            }
        }

        if (!isGiroInCorso) return null

        // Acquisisce permanentemente il blocco per l'intera durata del giro
        LucchettoGiri.blocca(idGiroFisso)

        // Trasforma le transizioni registrate in rotazioni complete (360°)
        val giriCompleti = mezziGiriCompletati / 2

        // Calcolo progressivo del Base Value: aumenta dinamicamente ad ogni rotazione successiva alla prima
        val valoreBaseDifficolta = if (isBustoOrizzontaleLocked && isBustoIndietroLocked) {
            0.50
        } else if (isBustoOrizzontaleLocked && !isBustoIndietroLocked) {
            0.40
        } else {
            0.30
        }

        val valoreTotale = if (mezziGiriCompletati >= 2) valoreBaseDifficolta + ((giriCompleti - 1) * 0.10) else 0.0

        // Assegnazione stringa in base al congelamento dei dati catturati in fase di Bootstrapping
        val nomeBase = if (isAttitudeLocked) {
            if (isBustoOrizzontaleLocked && isBustoIndietroLocked) "BOUCLE S. AIUTO" else "ATTITUDE"
        } else {
            if (isBustoOrizzontaleLocked && isBustoIndietroLocked) "ARABESQUE BUSTO IND." else "ARABESQUE"
        }

        val etichettaBusto = if (isBustoOrizzontaleLocked && !isBustoIndietroLocked) "(Busto Av./Lat.)" else ""
        val etichettaFinale = "GIRO $nomeBase $etichettaBusto"

        // UI Update in Real-Time
        nome = if (giriCompleti >= 1) {
            "$etichettaFinale ($giriCompleti Giri)"
        } else {
            "$etichettaFinale (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    /**
     * Garbage Collector & State Reset
     * Viene invocato automaticamente dai Watchdog quando l'atleta termina l'elemento,
     * rilasciando il Mutex e permettendo all'applicazione di valutare nuove figure.
     */
    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        isAttitudeLocked = false
        isBustoOrizzontaleLocked = false
        isBustoIndietroLocked = false
        gambaDiSupportoIsLeft = null
        dxSpalleIniziale = 0f
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
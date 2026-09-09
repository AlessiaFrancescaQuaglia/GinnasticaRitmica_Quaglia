package com.example.ginnasticaritmica.vision.giri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Modulo dedicato al tracciamento del "Pivot En Passé" (Rotazione con gamba flessa al ginocchio).
 * Rappresenta la figura rotazionale di base. Dal punto di vista della Computer Vision,
 * eredita la problematica di "Occlusione" (piede fuso con il ginocchio) dell'Equilibrio Passé,
 * aggravandola con le auto-occlusioni dinamiche causate dal corpo in rotazione.
 * La classe implementa una Macchina a Stati Finiti (FSM) reattiva, capace di abortire
 * istantaneamente l'analisi tramite interrupt continui (Scudo Anti-Penché).
 */
class GiroPasse : Difficolta {
    override var nome = "PIVOT EN PASSÉ"

    // MEMORIA DI STATO (FSM - Finite State Machine)
    // Variabili statiche che persistono tra i cicli di clock
    // per tracciare l'evoluzione del movimento nel dominio del tempo.
    companion object {
        private var mezziGiriCompletati = 0
        private var statoFronteRetro = 0
        private var timestampUltimoFrameBusto = 0L
        private var timestampUltimoSwap = 0L
        private var isGiroInCorso = false

        // congela l'avvenuto riconoscimento iniziale
        private var isFormaBloccata = false

        private var gambaDiSupportoIsLeft: Boolean? = null
        private var timestampGambaGiu = 0L
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        // Gestione Concorrenza: Se il thread è bloccato da un altro Pivot, esce.
        val idGiroFisso = this.javaClass.simpleName
        if (!LucchettoGiri.possoValutare(idGiroFisso)) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val isLeftLowest = gambaDiSupportoIsLeft ?: (leftAnkle.y() > rightAnkle.y())
        val supportAnkle = if (isLeftLowest) leftAnkle else rightAnkle
        val liftedAnkle = if (isLeftLowest) rightAnkle else leftAnkle
        val supportKnee = if (isLeftLowest) leftKnee else rightKnee
        val liftedKnee = if (isLeftLowest) rightKnee else leftKnee

        val scalaCorpo = PoseAnalyzer.calculateDistance3D(leftShoulder, leftHip)


        // FIREWALL BASE e RESET DINAMICO
        // Verifica l'ancoraggio del piede portante a terra.
        if (supportAnkle.y() < bacinoY + (scalaCorpo * 1.2f)) {
            resetGiro()
            return null
        }


        // LO SCUDO ANTI-PENCHÉ
        // il Passé impone un busto perfettamente verticale. Se l'atleta crolla in avanti (Flessione Y),
        // questo blocco funge da interruttore .
        // Essendo (!isFormaBloccata), viene interrogato a ogni frame. Se scatta, il Mutex globale viene liberato immediatamente.
        if (spallaY > bacinoY - (scalaCorpo * 0.35f)) {
            resetGiro()
            return null
        }

        // Watchdog Timer per la caduta della gamba libera
        val ginocchioSupportoY = if (isLeftLowest) leftKnee.y() else rightKnee.y()
        if (liftedAnkle.y() > ginocchioSupportoY) {
            if (!isFormaBloccata) {
                resetGiro()
                return null
            } else {
                if (timestampGambaGiu == 0L) {
                    timestampGambaGiu = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - timestampGambaGiu > 800L) {
                    // Timeout elastico di 800ms per compensare il fisiologico "Plié"
                    // di assestamento tra un giro e l'altro senza resettare il contatore.
                    if (!isGiroInCorso) {
                        resetGiro()
                        return null
                    }
                }
            }
        } else {
            timestampGambaGiu = 0L // Riarmo del timer
        }


        //VERIFICA POSA E  FORMA
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)
        val liftedKneeAngle = if (isLeftLowest) rightKneeAngle else leftKneeAngle

        val dxSpalle = rightShoulder.x() - leftShoulder.x()
        val sogliaSwap = max(0.005f, scalaCorpo * 0.04f)
        val statoAttualeSpalle = if (dxSpalle > sogliaSwap) 1 else if (dxSpalle < -sogliaSwap) -1 else 0

        if (!isFormaBloccata) {
            // Sensore di Prossimità 3D: Calcola la distanza Norma Euclidea tra piede e ginocchio.
            val distAnkleToKnee = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)

            // Requisiti Anatomici:
            // 1. Coscia sollevata rispetto al bacino
            // 2. Ginocchio rigorosamente chiuso (< 105°)
            val isGinocchioAlto = liftedKnee.y() < bacinoY + (scalaCorpo * 0.3f)
            val isGinocchioChiuso = liftedKneeAngle < 105.0

            // Restrizione Tolleranza Spaziale: per distinguere il Passé da un Attitude, il sensore
            // di prossimità è impostato su soglie stringenti. Il piede deve toccare il ginocchio.
            val isPiedeAlGinocchio = distAnkleToKnee < (scalaCorpo * 0.4f)

            if (!(isGinocchioAlto && isGinocchioChiuso && isPiedeAlGinocchio)) return null

            gambaDiSupportoIsLeft = isLeftLowest
            isFormaBloccata = true

            // Lock del Mutex: la figura è certificata, si inibiscono le altre classi
            LucchettoGiri.blocca(idGiroFisso)

            statoFronteRetro = statoAttualeSpalle
            timestampUltimoFrameBusto = System.currentTimeMillis()
            timestampUltimoSwap = System.currentTimeMillis()

            // Transizione istantanea nello stato "Rotazione"
            isGiroInCorso = true
        } else {
            // Se la gamba si allontana dal ginocchio di terra o cede verso il basso,
            // si attiva un timer di degradamento progressivo
            val distAnkleToKnee = PoseAnalyzer.calculateDistance3D(liftedAnkle, supportKnee)
            val isAncoraInPasse = (liftedKnee.y() < bacinoY + (scalaCorpo * 0.6f)) && (distAnkleToKnee < (scalaCorpo * 0.6f))

            if (isAncoraInPasse) {
                timestampUltimoFrameBusto = System.currentTimeMillis()
            } else {
                val tolleranzaTempo = if (isGiroInCorso) 1500L else 800L
                if (System.currentTimeMillis() - timestampUltimoFrameBusto > tolleranzaTempo) {
                    resetGiro()
                    return null
                }
            }

            if (isGiroInCorso && System.currentTimeMillis() - timestampUltimoSwap > 2000L) {
                resetGiro()
                return null
            }
        }


        // MOTORE X-SWAP
        // Valuta l'attraversamento dello zero dell'asse X delle spalle.
        if (isFormaBloccata) {
            if (statoFronteRetro == 0 && statoAttualeSpalle != 0) {
                statoFronteRetro = statoAttualeSpalle
                timestampUltimoSwap = System.currentTimeMillis()
            } else if (statoFronteRetro != 0 && statoAttualeSpalle != 0 && statoAttualeSpalle != statoFronteRetro) {
                val tempoDalUltimoSwap = System.currentTimeMillis() - timestampUltimoSwap

                // Finestra per inibire i micro-rimbalzi del tensore ottico
                if (tempoDalUltimoSwap > 150L) {
                    mezziGiriCompletati++
                    statoFronteRetro = statoAttualeSpalle
                    timestampUltimoSwap = System.currentTimeMillis()

                    timestampUltimoFrameBusto = System.currentTimeMillis()
                    timestampGambaGiu = 0L
                }
            }
        }

        val giriCompletati = mezziGiriCompletati / 2

        if (giriCompletati == 0 && mezziGiriCompletati == 0 && !isFormaBloccata) return null


        //RESTITUZIONE PUNTEGGIO
        val valoreBaseDifficolta = 0.10 // Valore base FIG per il Passé
        val valoreTotale = if (giriCompletati >= 1) valoreBaseDifficolta + ((giriCompletati - 1) * 0.10) else 0.0

        val etichettaFinale = "PIVOT EN PASSÉ"

        // Renderizza il progresso in tempo reale sul Display
        nome = if (giriCompletati >= 1) {
            "$etichettaFinale ($giriCompletati Giri)"
        } else {
            "$etichettaFinale (In rotazione...)"
        }

        return RisultatoDifficolta(true, valoreTotale, 0.0, true, "")
    }

    /**
     * Svuotamento buffer di memoria e rilascio forzato del Semaforo Mutex.
     */
    private fun resetGiro() {
        mezziGiriCompletati = 0
        statoFronteRetro = 0
        isGiroInCorso = false
        isFormaBloccata = false
        gambaDiSupportoIsLeft = null
        timestampUltimoFrameBusto = 0L
        timestampUltimoSwap = 0L
        timestampGambaGiu = 0L
        LucchettoGiri.sblocca(this.javaClass.simpleName)
    }
}
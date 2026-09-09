package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Modulo per il "Salto Cosacco".
 * L'atleta esegue un balzo elevando una gamba tesa in avanti e flettendo (rannicchiando) l'altra
 * gamba sotto o di fianco al bacino.
 * La criticità di questo elemento risiede nella forte asimmetria bilaterale che rischia di far
 * classificare la posa come un salto fallito (es. un Grand Jeté con una gamba piegata per errore).
 */
class SaltoCosacco : Difficolta {
    override var nome = "SALTO COSACCO"


    // MEMORIA DI STATO ROTAZIONALE
    // Registri persistenti utilizzati per calcolare il delta temporale tra l'inversione
    // delle spalle e la fase di volo, al fine di riconoscere e premiare
    // l'ingresso "En Tournant".
    companion object {
        private var tempoUltimaRotazione: Long = 0L
        private var ultimaDirezioneSpalleDestra: Boolean? = null
    }

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]
        val spallaSx = landmarks[11]
        val spallaDx = landmarks[12]
        val nose = landmarks[0]

        val bacinoX = (leftHip.x() + rightHip.x()) / 2f
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (spallaSx.y() + spallaDx.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // Estrazione del tensore del tronco (Spalla-Bacino) come metrica di scala dinamica
        val scalaCorpo = PoseAnalyzer.calculateDistance3D(spallaSx, leftHip)


        // FIREWALL ANTI-GRAVITÀ
        // Sostituisce il LucchettoGiri con un approccio fisico-matematico rigoroso.
        // Un salto, per definizione cinetica, implica l'assenza di contatto con il suolo.
        // Utilizziamo "1.1 * scalaCorpo" come stima parametrica dell'estensione massima di una gamba.
        // Se la caviglia più bassa (quella col valore Y maggiore) è così distante dal bacino,
        // la rete neurale deduce che l'arto stia sostenendo il peso a terra (Appoggio).
        val lowestAnkleY = kotlin.math.min(leftAnkle.y(), rightAnkle.y())
        if (lowestAnkleY > bacinoY + (scalaCorpo * 1.1f)) {
            return null // L'atleta è a terra. Interruzione analisi balistica (Delegata a Equilibri/Pivot).
        }

        // Filtro Anti-Accosciata: Se il bacino scende al di sotto della caviglia più bassa,
        // l'atleta sta eseguendo una transizione a terra o una rotazione bassa, non un salto.
        val highestAnkleY = kotlin.math.min(leftAnkle.y(), rightAnkle.y())
        if (bacinoY > highestAnkleY) {
            return null
        }


        // TRACCIAMENTO ROTAZIONALE
        // Determina se c'è stata un'inversione di polarità tra le spalle sull'asse X
        // per innescare il contatore del moltiplicatore "En Tournant".
        val isSpalleVersoDestra = spallaSx.x() < spallaDx.x()
        if (ultimaDirezioneSpalleDestra == null) {
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        } else if (ultimaDirezioneSpalleDestra != isSpalleVersoDestra) {
            tempoUltimaRotazione = System.currentTimeMillis()
            ultimaDirezioneSpalleDestra = isSpalleVersoDestra
        }


        // IDENTIFICAZIONE MORFOLOGICA (Vincolo di Asimmetria Bilaterale)
        // Il Cosacco esige una configurazione binaria rigida:
        // Arto A = Esteso (>135°), Arto B = Flesso/Rannicchiato (<135°).
        // L'operatore logico XOR esclusivo assicura la mutua esclusione delle pose simmetriche.
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 135.0
        val isRightTucked = rightKneeAngle < 135.0

        if (!((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked))) {
            return null
        }


        // FIREWALL ANTI-SPACCATA
        // Discrimina un salto Cosacco da una spaccata fallita (es. Biche o Grand Jeté piegato).
        // Nel Cosacco, le cosce rimangono relativamente coassiali in avanti (angolo ristretto).
        // Se la divaricazione femorale supera i 110°, la figura degenera in una spaccata non valida.
        val centroBacino = NormalizedLandmark.create(bacinoX, bacinoY, 0f)

        // Fusione Algoritmica 3D e 2D per neutralizzare gli effetti di schiacciamento della prospettiva Z.
        val angoloFemorale3D = PoseAnalyzer.calculateAngle3D(leftKnee, centroBacino, rightKnee)

        val vLKx = leftKnee.x() - bacinoX
        val vLKy = leftKnee.y() - bacinoY
        val vRKx = rightKnee.x() - bacinoX
        val vRKy = rightKnee.y() - bacinoY
        val dotFem = (vLKx * vRKx) + (vLKy * vRKy)
        val magLK = kotlin.math.sqrt((vLKx * vLKx + vLKy * vLKy).toDouble())
        val magRK = kotlin.math.sqrt((vRKx * vRKx + vRKy * vRKy).toDouble())
        var angoloFemorale2D = Math.toDegrees(kotlin.math.acos(dotFem / (magLK * magRK)))
        if (angoloFemorale2D.isNaN()) angoloFemorale2D = 0.0

        val angoloFemorale = kotlin.math.max(angoloFemorale3D, angoloFemorale2D)

        if (angoloFemorale > 110.0) {
            return null
        }


        // FIREWALL ANTI-INARCAMENTO (Postura del Tronco)
        // Il Codice F.I.G. impone per il Cosacco un tronco eretto.
        // Utilizziamo scalaCorpo per concedere solo un piccolo margine di tolleranza all'indietro.
        if (spallaY > bacinoY - (scalaCorpo * 0.1f)) {
            return null
        }

        val straightAnkle = if (isLeftStraight) leftAnkle else rightAnkle
        val straightToe = if (isLeftStraight) landmarks[31] else landmarks[32]

        // Misurazione altimetrica della gamba di slancio normalizzata sulla scala del tronco
        if (straightAnkle.y() > bacinoY + (scalaCorpo * 0.4f)) return null

        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]


        // SENSORE DI PROSSIMITÀ (Rilevamento Varianti "Alto" e "Con Presa")
        // Valuta l'elevazione estrema della gamba misurando la distanza vettoriale Naso-Piede
        // o il superamento dell'orizzonte delle spalle.
        val distTestaPiede = PoseAnalyzer.calculateDistance3D(nose, straightToe)
        val isGambaAlta = straightAnkle.y() < spallaY + (scalaCorpo * 0.3f) || distTestaPiede < (scalaCorpo * 0.8f)

        // Collision Detection: Rileva l'aiuto muscolare delle braccia (Presa)
        // intersecando i volumi 3D di polsi e caviglia sollevata.
        val distLeftWrist = PoseAnalyzer.calculateDistance3D(leftWrist, straightAnkle)
        val distRightWrist = PoseAnalyzer.calculateDistance3D(rightWrist, straightAnkle)
        val minWristDist = kotlin.math.min(distLeftWrist, distRightWrist)

        val isConPresa = isGambaAlta && minWristDist <= (scalaCorpo * 0.5f)

        // Controllo della finestra di tolleranza per il modificatore di rotazione (1500ms)
        val tempoTrascorsoDaRotazione = System.currentTimeMillis() - tempoUltimaRotazione
        val isEnTournant = tempoTrascorsoDaRotazione < 1500L

        // Albero Decisionale per l'attribuzione Dinamica del Valore Base
        var valoreBase = 0.0

        if (isConPresa) {
            nome = if (isEnTournant) "SALTO COSACCO TOURNANT CON PRESA" else "SALTO COSACCO CON PRESA"
            valoreBase = if (isEnTournant) 0.60 else 0.50
        } else if (isGambaAlta) {
            nome = if (isEnTournant) "SALTO COSACCO TOURNANT SENZA PRESA" else "SALTO COSACCO ALTO SENZA PRESA"
            valoreBase = if (isEnTournant) 0.50 else 0.40
        } else {
            nome = if (isEnTournant) "SALTO COSACCO EN TOURNANT" else "SALTO COSACCO CLASSICO"
            valoreBase = if (isEnTournant) 0.40 else 0.30
        }


        // MOTORE DELLE PENALITÀ
        // Mappatura discreta del deficit di altezza della gamba di slancio,
        // ponderando la tolleranza in base al tipo di variante eseguita (Alto vs Classico).
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (straightAnkle.y() < spallaY + (scalaCorpo * 0.3f)) {
            penalita += 0.0
        } else if (straightAnkle.y() < bacinoY - (scalaCorpo * 0.1f)) {
            penalita += 0.10
        } else if (straightAnkle.y() <= bacinoY + (scalaCorpo * 0.4f)) {
            penalita += if (isGambaAlta) 0.30 else 0.10
        } else {
            penalita += 0.50
            isValida = false // Drop tecnico: l'elevazione è troppo scarsa per configurare il salto
            msgErrore = "Gamba avanti caduta/troppo bassa"
        }

        // Flush della memoria rotazionale a elemento validato
        if (isValida) tempoUltimaRotazione = 0L

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
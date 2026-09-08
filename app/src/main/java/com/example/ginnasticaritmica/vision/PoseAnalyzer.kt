package com.example.ginnasticaritmica.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.sqrt


object PoseAnalyzer {


    // MOTORE DI DEDUZIONE VETTORIALE
    /**
     * Valuta la "Confidence Score" assegnata dalla rete neurale al singolo punto.
     * @param landmark Il punto articolare da verificare.
     * @return True se l'Intelligenza Artificiale è sufficientemente sicura della stima.
     */
    fun isAffidabile(landmark: NormalizedLandmark): Boolean {
        // Estrae il dato di visibilità probabilistica generato da MediaPipe.
        val visibilita = if (landmark.visibility().isPresent) landmark.visibility().get() else 1.0f
        return visibilita > 0.50f // Soglia base (50%) - Scarta i punti ad alto rischio di rumore
    }

    /**
     * Risoluzione avanzata delle "Occlusioni Anatomiche" (es. Boucle o Presa).
     * Quando l'atleta inarca profondamente la schiena e piega la gamba, la testa o il busto nascondono
     * visivamente le estremità posteriori (caviglie/piedi) all'obiettivo della telecamera.
     * Questo metodo "inventa" matematicamente la posizione della caviglia nascosta
     * sfruttando la rigidità intrinseca delle proporzioni anatomiche.
     */
    fun getCavigliaIntelligente(
        hip: NormalizedLandmark,
        knee: NormalizedLandmark,
        ankle: NormalizedLandmark
    ): NormalizedLandmark {

        // Se MediaPipe vede bene la caviglia (non è oscurata dal corpo), la usiamo senza problemi.
        if (isAffidabile(ankle)) {
            return ankle
        }

        // Se la caviglia è invisibile, LA DEDUCIAMO TRAMITE ESTRAPOLAZIONE VETTORIALE
        // 1. Calcoliamo il vettore direzionale 3D del segmento superiore (Anca - Ginocchio).
        val dx = knee.x() - hip.x()
        val dy = knee.y() - hip.y()
        val dz = knee.z() - hip.z()

        // 2. Assumiamo che la tibia abbia una lunghezza proporzionale e allineata rispetto al femore
        // (rapporto standard approssimato a 1.05 per le leve umane).
        // Proiettiamo il punto nello spazio 3D spingendoci oltre il ginocchio, lungo il medesimo vettore.
        val extrapolatedX = knee.x() + (dx * 1.05f)
        val extrapolatedY = knee.y() + (dy * 1.05f)
        val extrapolatedZ = knee.z() + (dz * 1.05f)

        // Restituiamo un "Landmark Virtuale" iniettandolo nel calcolo.
        // Questo stratagemma salva il tracciamento dei Salti con inarcamento profondo (Boucle)
        // isolando il sistema dalle alterazioni indotte dalla mancata visione della tibia reale.
        return NormalizedLandmark.create(extrapolatedX, extrapolatedY, extrapolatedZ)
    }

    // FUNZIONI MATEMATICHE 3D (PRODOTTO SCALARE)
    /**
     * Motore matematico che sostituisce i calcoli planari 2D.
     * Calcola l'ampiezza spaziale reale dell'angolo formato da tre punti nello spazio.
     */
    fun calculateAngle3D(
        firstPoint: NormalizedLandmark, // Es. Caviglia
        midPoint: NormalizedLandmark,   // Es. Bacino (Fulcro dell'angolo)
        lastPoint: NormalizedLandmark   // Es. Spalla
    ): Double {
        // 1. Derivazione dei vettori tridimensionali incidenti nel fulcro
        val ax = firstPoint.x() - midPoint.x()
        val ay = firstPoint.y() - midPoint.y()
        val az = firstPoint.z() - midPoint.z()

        val bx = lastPoint.x() - midPoint.x()
        val by = lastPoint.y() - midPoint.y()
        val bz = lastPoint.z() - midPoint.z()

        // 2. Applicazione del Prodotto Scalare
        val dotProduct = (ax * bx) + (ay * by) + (az * bz)

        // 3. Calcolo delle Norme Euclidee
        val magA = sqrt((ax * ax + ay * ay + az * az).toDouble())
        val magB = sqrt((bx * bx + by * by + bz * bz).toDouble())

        // Prevenzione di divisioni per zero
        if (magA == 0.0 || magB == 0.0) return 0.0

        // Teorema del Coseno. La limitazione compensa gli errori di approssimazione in virgola mobile
        // causati dai processori, evitando che il valore sfori il dominio [-1, 1] e causi crash matematici
        val cosTheta = dotProduct / (magA * magB)
        val validCos = (-1.0).coerceAtLeast(1.0.coerceAtMost(cosTheta))

        // Ritorna il grado angolare effettivo (indipendente dalla parallasse della telecamera)
        return Math.toDegrees(acos(validCos))
    }

    /**
     * Calcola la Distanza Euclidea 3D pura tra due punti (Norma del vettore).
     * Indispensabile per lo "Scudo di Profondità" e per le validazioni di prossimità spaziale
     * (esempio distinguere la vicinanza tra ginocchio e caviglia nel Passé vs. Attitude).
     */
    fun calculateDistance3D(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}
package com.example.ginnasticaritmica.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Modulo di Algebra Lineare e Pre-Processing dei Segnali.
 * Astrae la logica matematica e vettoriale dal livello applicativo, fornendo
 * primitive riutilizzabili per il calcolo spaziale 3D e per la mitigazione
 * degli errori nativi del framework di Computer Vision.
 */
object PoseAnalyzer {


    // MOTORE DI DEDUZIONE VETTORIALE E FILTRAGGIO


    /**
     * Filtro di Confidenza.
     * Valuta la metrica di "Visibility" (probabilità di presenza reale e non occlusa)
     * assegnata dalla rete neurale al singolo tensore.
     * @param landmark Il punto articolare (joint) da verificare.
     * @return True se l'Intelligenza Artificiale ritiene il dato statisticamente affidabile.
     */
    fun isAffidabile(landmark: NormalizedLandmark): Boolean {
        // Estrae il dato probabilistico generato in fase di inferenza da MediaPipe.
        val visibilita = if (landmark.visibility().isPresent) landmark.visibility().get() else 1.0f

        // Scarta letture affette da grave rumore ottico o "Glitches" dovuti a occlusioni parziali.
        return visibilita > 0.50f
    }

    /**
     * Algoritmo di risoluzione avanzata per le "Occlusioni Anatomiche Severe" (es. salti con inarcamento).
     * Quando l'atleta flette profondamente la schiena all'indietro (Cambré), il corpo funge da
     * scudo visivo, nascondendo completamente gli arti posteriori alla telecamera.
     * Invece di perdere il tracking, questa funzione "predice" vettorialmente le coordinate mancanti.
     */
    fun getCavigliaIntelligente(
        hip: NormalizedLandmark,
        knee: NormalizedLandmark,
        ankle: NormalizedLandmark
    ): NormalizedLandmark {

        // Se il sensore ha un fix visivo solido sulla caviglia reale, bypassa la predizione.
        if (isAffidabile(ankle)) {
            return ankle
        }


        // ESTRAPOLAZIONE VETTORIALE RIGIDA
        // Sfrutta i vincoli di rigidità scheletrica del corpo umano.
        // Calcolo del vettore direzionale 3D del segmento superiore noto (Femore: Anca -> Ginocchio).
        val dx = knee.x() - hip.x()
        val dy = knee.y() - hip.y()
        val dz = knee.z() - hip.z()

        // Modello Biomeccanico Approssimato:
        // Assumiamo che, in caso di gamba tesa, la Tibia sia allineata al Femore.
        // Moltiplichiamo il vettore per uno scalare (1.05f) che rappresenta il rapporto
        // anatomico standard tra la lunghezza tibiale e quella femorale nelle leve umane.
        val extrapolatedX = knee.x() + (dx * 1.05f)
        val extrapolatedY = knee.y() + (dy * 1.05f)
        val extrapolatedZ = knee.z() + (dz * 1.05f)

        // Iniezione del "Phantom Landmark" (Terminale Virtuale).
        // Restituisce il punto calcolato permettendo alle classi valutatrici di completare
        // il calcolo degli angoli (es. Spaccate in volo) senza fallire per carenza di input.
        return NormalizedLandmark.create(extrapolatedX, extrapolatedY, extrapolatedZ)
    }


    // LIBRERIA MATEMATICA 3D (TRIGONOMETRIA SPAZIALE)
    /**
     * Motore di estrazione angolare spaziale.
     * Supera i severi limiti della geometria euclidea 2D (distorsione prospettica e parallasse)
     * calcolando l'angolo reale in un volume tridimensionale.
     */
    fun calculateAngle3D(
        firstPoint: NormalizedLandmark, // Vettore A (Terminale 1, es. Caviglia)
        midPoint: NormalizedLandmark,   // Origine degli assi locali (Fulcro articolare)
        lastPoint: NormalizedLandmark   // Vettore B (Terminale 2, es. Spalla)
    ): Double {
        //  Costruzione dei vettori traslati nell'origine (midPoint)
        val ax = firstPoint.x() - midPoint.x()
        val ay = firstPoint.y() - midPoint.y()
        val az = firstPoint.z() - midPoint.z()

        val bx = lastPoint.x() - midPoint.x()
        val by = lastPoint.y() - midPoint.y()
        val bz = lastPoint.z() - midPoint.z()

        //  Calcolo del Prodotto Scalare
        val dotProduct = (ax * bx) + (ay * by) + (az * bz)

        //  Estrazione delle Norme Euclidee
        val magA = sqrt((ax * ax + ay * ay + az * az).toDouble())
        val magB = sqrt((bx * bx + by * by + bz * bz).toDouble())

        // Sicurezza: Prevenzione delle eccezioni matematiche (Division by Zero)
        if (magA == 0.0 || magB == 0.0) return 0.0

        // Teorema del Coseno e Discretizzazione di Precisione.
        // L'arcocoseno accetta solo un dominio [-1, 1].
        // La funzione "coerce" agisce da taglio per prevenire crash applicativi restituiti come NaN.
        val cosTheta = dotProduct / (magA * magB)
        val validCos = (-1.0).coerceAtLeast(1.0.coerceAtMost(cosTheta))

        // Restituisce l'ampiezza in gradi sessagesimali per la valutazione normativa.
        return Math.toDegrees(acos(validCos))
    }

    /**
     * Calcolo Norma L2 (Distanza Euclidea Tridimensionale).
     */
    fun calculateDistance3D(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()

        // Radice quadrata della somma dei quadrati delle componenti (Teorema di Pitagora in 3D)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}
package com.example.ginnasticaritmica.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

object PoseAnalyzer {

    // Calcola l'angolo interno in gradi tra 3 punti
    fun calculateAngle(
        firstPoint: NormalizedLandmark, // Es: Anca
        midPoint: NormalizedLandmark,   // Es: Ginocchio (vertice)
        lastPoint: NormalizedLandmark   // Es: Caviglia
    ): Double {
        val angle = atan2(lastPoint.y() - midPoint.y(), lastPoint.x() - midPoint.x()) -
                atan2(firstPoint.y() - midPoint.y(), firstPoint.x() - midPoint.x())

        var degrees = angle * (180.0 / Math.PI)
        degrees = abs(degrees)

        if (degrees > 180.0) {
            degrees = 360.0 - degrees
        }

        return degrees
    }

    // Calcola la distanza tra due punti (es. per vedere se la mano tocca il piede)
    fun calculateDistance(point1: NormalizedLandmark, point2: NormalizedLandmark): Double {
        return hypot((point2.x() - point1.x()).toDouble(), (point2.y() - point1.y()).toDouble())
    }


    // Aggiungi questa funzione nel tuo PoseAnalyzer
    fun calculateAngle3D(
        firstPoint: NormalizedLandmark,
        midPoint: NormalizedLandmark,
        lastPoint: NormalizedLandmark
    ): Double {
        // Vettore A (da centro a primo punto)
        val ax = firstPoint.x() - midPoint.x()
        val ay = firstPoint.y() - midPoint.y()
        val az = firstPoint.z() - midPoint.z() // Usiamo la profondità!

        // Vettore B (da centro a secondo punto)
        val bx = lastPoint.x() - midPoint.x()
        val by = lastPoint.y() - midPoint.y()
        val bz = lastPoint.z() - midPoint.z()

        // Prodotto scalare
        val dotProduct = (ax * bx) + (ay * by) + (az * bz)

        // Magnitudine (lunghezza 3D dei vettori)
        val magA = sqrt((ax * ax + ay * ay + az * az).toDouble())
        val magB = sqrt((bx * bx + by * by + bz * bz).toDouble())

        if (magA == 0.0 || magB == 0.0) return 0.0

        // Calcolo dell'angolo in radianti e conversione in gradi
        val cosTheta = dotProduct / (magA * magB)
        // Evitiamo errori di arrotondamento fuori da [-1, 1]
        val validCos = (-1.0).coerceAtLeast(1.0.coerceAtMost(cosTheta))

        return Math.toDegrees(acos(validCos))
    }

    // Aggiungi anche questa per la distanza 3D (più precisa)
    fun calculateDistance3D(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}
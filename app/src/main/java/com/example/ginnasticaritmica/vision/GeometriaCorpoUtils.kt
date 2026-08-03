package com.example.ginnasticaritmica.vision

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object GeometriaCorpoUtils {

    /**
     * Calcola l'angolo interno formato da 3 punti (es. Anca, Ginocchio, Caviglia).
     * @param aX, aY Coordinate del primo estremo (es. Anca)
     * @param bX, bY Coordinate del vertice (es. Ginocchio)
     * @param cX, cY Coordinate del secondo estremo (es. Caviglia)
     * @return Angolo in gradi (da 0.0 a 180.0)
     */
    fun calcolaAngolo(aX: Float, aY: Float, bX: Float, bY: Float, cX: Float, cY: Float): Double {
        var angolo = Math.toDegrees(
            atan2((cY - bY).toDouble(), (cX - bX).toDouble()) -
                    atan2((aY - bY).toDouble(), (aX - bX).toDouble())
        )
        angolo = abs(angolo)

        // Vogliamo sempre l'angolo interno, quindi <= 180 gradi
        if (angolo > 180.0) {
            angolo = 360.0 - angolo
        }
        return angolo
    }

    /**
     * Calcola la distanza euclidea tra due punti.
     * Molto utile per capire se il piede tocca il ginocchio opposto nel Passé.
     */
    fun calcolaDistanza(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        return hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
    }

    /**
     * Verifica se la gamba di terra è tesa.
     * Considera valida un'estensione con una leggera tolleranza fisiologica.
     * @param angolo L'angolo calcolato al ginocchio.
     * @param sogliaMinima Tolleranza (es. 170 gradi invece di 180 perfetti).
     */
    fun isGambaTesa(angolo: Double, sogliaMinima: Double = 170.0): Boolean {
        return angolo >= sogliaMinima
    }

    /**
     * Determina quale gamba sta sostenendo il peso (gamba di terra).
     * ATTENZIONE: Nella Computer Vision l'asse Y è invertito (Y=0 è in alto, Y=1 è in basso).
     * Il piede con la Y MAGGIORE è quello più vicino al pavimento.
     * @return True se la gamba di terra è la Destra, False se è la Sinistra.
     */
    fun isGambaDestraATerra(cavigliaDxY: Float, cavigliaSxY: Float): Boolean {
        return cavigliaDxY > cavigliaSxY
    }

    /**
     * Verifica se l'atleta è in relevé (mezza punta).
     * Poiché Y scende verso il basso, il tallone deve avere una Y MINORE della punta
     * per trovarsi fisicamente più in alto rispetto a essa.
     */
    fun isReleve(talloneY: Float, puntaY: Float): Boolean {
        return talloneY < puntaY
    }
}
package com.example.ginnasticaritmica.vision.salti

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Modulo per il "Salto Boucle" (Salto ad Anello).
 * L'atleta esegue un salto verticale flettendo all'indietro una o entrambe le gambe
 * fino a far toccare la punta del piede con la testa (inarcamento del tronco).
 * La sfida ingegneristica primaria di questa classe è la "Risoluzione dei Conflitti di Dominio":
 * l'elemento "Boucle" (Anello) è una sotto-forma presente anche in altri salti (es. Biche, Grand Jeté).
 * L'algoritmo deve applicare rigorosi filtri di esclusione per accertarsi che si tratti
 * di un Salto Boucle puro (verticale) e non di una variante complessa appartenente ad altre classi.
 */
class SaltoBoucle : Difficolta {
    override var nome = "SALTO BOUCLE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null


        //SCUDO ANTI-GIRO
        //se l'atleta sta eseguendo un Pivot, è impossibile che si trovi in fase di volo.
        if (com.example.ginnasticaritmica.vision.giri.LucchettoGiri.giroAttivo != null) {
            return null
        }

        val nose = landmarks[0]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bacinoY = (leftHip.y() + rightHip.y()) / 2f

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        // FIREWALL ANTI-EQUILIBRIO
        // Usiamo la lunghezza del femore come unità di misura scalare invariabile.
        // Questo protegge l'algoritmo dalle distorsioni causate dal forte inarcamento (Cambré),
        // che altrimenti falserebbe l'altezza del tronco usata in altre classi.
        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Una gamba è piantata a terra (supporto del peso) solo se scende in profondità
        // sull'asse Y (più del femore) ed è sufficientemente tesa (> 135°).
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Se una caviglia è eccessivamente in basso,
        // deduciamo l'ancoraggio a terra a prescindere dall'angolazione del ginocchio.
        val isLeftDeepPlanted = leftAnkle.y() > bacinoY + 0.45f
        val isRightDeepPlanted = rightAnkle.y() > bacinoY + 0.45f

        if (isLeftPlanted || isRightPlanted || isLeftDeepPlanted || isRightDeepPlanted) {
            return null // Piede a terra confermato, l'inferenza passa alle classi di Equilibrio.
        }


        // FIREWALL ANTI-BICHE (Disambiguazione Strutturale)
        // Previene la sovrapposizione con il "Salto Biche con Boucle".
        // L'algoritmo proietta i vettori sull'asse X assoluto per determinare quale arto
        // guida il movimento (Gamba Anteriore) in base alla direzione dello sguardo (Naso).
        val bacinoCenterX = (leftHip.x() + rightHip.x()) / 2f
        val nasoX = landmarks[0].x()

        val isFacingRight = nasoX > bacinoCenterX
        val isLeftFront = if (isFacingRight) leftKnee.x() > rightKnee.x() else leftKnee.x() < rightKnee.x()

        val frontKnee = if (isLeftFront) leftKnee else rightKnee
        val frontHip = if (isLeftFront) leftHip else rightHip
        val frontAnkle = if (isLeftFront) leftAnkle else rightAnkle

        val frontKneeAngle = PoseAnalyzer.calculateAngle3D(frontHip, frontKnee, frontAnkle)

        // Se la gamba anteriore è flessa a forma di "Cervo" (tra 35° e 120°),
        // l'elemento non è un Boucle puro. La classe abortisce e delega al SaltoBiche.
        if (frontKneeAngle in 35.0..120.0) {
            return null
        }



        //FORMA DEL GINOCCHIO POSTERIORE
        // Accerta che almeno una delle due caviglie sia lanciata verso l'alto (sopra il bacino)
        // e che il relativo ginocchio sia marcatamente flesso per innescare la forma ad anello.
        val isLeftHigh = leftAnkle.y() < bacinoY - 0.1f
        val isRightHigh = rightAnkle.y() < bacinoY - 0.1f

        if (!isLeftHigh && !isRightHigh) return null

        var hasValidBoucleLeg = false
        if (isLeftHigh && leftKneeAngle < 135.0) hasValidBoucleLeg = true
        if (isRightHigh && rightKneeAngle < 135.0) hasValidBoucleLeg = true

        if (!hasValidBoucleLeg) return null


        // FIREWALL ANTI-SPACCATA
        // Calcola l'angolo di divaricazione coxo-femorale tra le due cosce.
        // Se l'apertura supera i 110°, l'elemento sta transitando verso una spaccata
        // (es. Grand Jeté con Boucle). Questo filtro isola la natura "verticale" del salto in esame.
        val centroBacino = NormalizedLandmark.create(
            (leftHip.x() + rightHip.x()) / 2f,
            (leftHip.y() + rightHip.y()) / 2f,
            0f
        )
        val angoloCosce = PoseAnalyzer.calculateAngle3D(leftKnee, centroBacino, rightKnee)

        if (angoloCosce > 110.0) return null


        // VALUTAZIONE DEL SALTO
        val leftToe = landmarks[31]
        val rightToe = landmarks[32]

        // Calcolo Norma tra il viso e le estremità degli arti inferiori.
        val distLeft = PoseAnalyzer.calculateDistance3D(nose, leftToe)
        val distRight = PoseAnalyzer.calculateDistance3D(nose, rightToe)

        // Se nessun piede si avvicina sufficientemente alla testa, la figura geometrica
        // dell'anello è considerata fallita e l'elemento viene invalidato.
        if (distLeft > 0.28f && distRight > 0.28f) return null

        val distanzaCaviglie = PoseAnalyzer.calculateDistance3D(leftAnkle, rightAnkle)
        var valoreBase = 0.0

        // Sub-classificazione:
        // L'algoritmo riconosce autonomamente se il salto è eseguito inarccando una sola gamba
        // oppure entrambe (Double Boucle), verificando che anche le due caviglie siano ravvicinate tra loro.
        if (distLeft < 0.28f && distRight < 0.28f && distanzaCaviglie < 0.20f) {
            nome = "SALTO BOUCLE (2 Gambe)"
            valoreBase = 0.30
        } else {
            nome = "SALTO BOUCLE (1 Gamba)"
            valoreBase = 0.20
        }

        // Mapping Discreto delle Penalità Esecutive:
        // Traduce la misurazione continua della distanza in scaglioni di penalità standardizzati.
        val bestDist = kotlin.math.min(distLeft, distRight)
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        if (bestDist <= 0.15f) penalita += 0.0
        else if (bestDist in 0.151f..0.20f) penalita += 0.10
        else if (bestDist in 0.201f..0.25f) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Anello non chiuso"
        }

        return RisultatoDifficolta(isValida, if (isValida) valoreBase else 0.0, penalita, false, msgErrore)
    }
}
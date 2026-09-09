package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Modulo dedicato alla classificazione dell'Equilibrio "Passé".
 * Il Passé è la figura base della ginnastica: una gamba tesa a terra,
 * l'altra piegata con la punta del piede appoggiata al ginocchio portante.
 */
class EquilibrioPasse : Difficolta {
    override val nome = "EQUILIBRIO PASSÉ"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]


        // FIREWALL ANTI-VOLO E GESTIONE CONCORRENZA
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Controllo altimetrico: esclude categoricamente che l'atleta sia in fase aerea.
        val isLeftPlanted = leftAnkle.y() > bacinoY + femoreSx && leftKneeAngle > 135.0
        val isRightPlanted = rightAnkle.y() > bacinoY + femoreDx && rightKneeAngle > 135.0

        // Mutex Rotazionale: previene conflitti di classificazione con il "Giro Passé".
        // Se il framework rileva inerzia angolare sull'asse Y, questo thread si sospende.
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        if (!isLeftPlanted && !isRightPlanted) {
            return null
        }


        // Mutua Esclusione: se entrambe le ginocchia sono tese (>120°),
        // è matematicamente impossibile che si tratti di un Passé (che richiede una gamba flessa).
        if (leftKneeAngle > 120.0 && rightKneeAngle > 120.0) return null

        // Identificazione algoritmica dinamica degli arti inferiori
        val isLeftBent = leftKneeAngle < rightKneeAngle
        val isAppoggioLeft = !isLeftBent

        // Il ginocchio portante deve sostenere il peso in modo rigido (quasi 180°).
        val supportAngle = if (isAppoggioLeft) leftKneeAngle else rightKneeAngle
        if (supportAngle < 160.0) return null


        // FIREWALL SPAZIALE 3D (Sensore di prossimità)
        // Per non confondere il Passé con un Attitude, calcoliamo la Distanza Euclidea tra la punta del piede
        // sollevato e il ginocchio della gamba di terra.
        //  La tolleranza è stata allargata a 0.25f (rispetto allo standard 0.18f)
        // per compensare il tremolio fisiologico di MediaPipe nel tracciamento
        // delle estremità sovrapposte (occlusione piede-ginocchio).
        val puntaPiegata = if (isLeftBent) landmarks[31] else landmarks[32]
        val ginocchioTerra = if (isAppoggioLeft) leftKnee else rightKnee
        val distanzaPuntaGinocchio = PoseAnalyzer.calculateDistance3D(puntaPiegata, ginocchioTerra)

        if (distanzaPuntaGinocchio > 0.25f) {
            return null // Piede troppo lontano dal ginocchio: la posa viene scartata.
        }

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)

        val valoreBase = 0.10
        var penalita = 0.0

        // Se l'equilibrio è eseguito con il tallone a terra (in pianta),
        // applichiamo la penalità MA non azzeriamo immediatamente il valore base.
        // Questo permette all'HUD grafico di mostrare al tecnico "Equilibrio rilevato ma eseguito male",
        // invece di non stampare nulla a schermo simulando una mancata rilevazione (Falso Negativo).
        if (!inReleve) {
            penalita += 0.10
        }

        val spallaPiegata = if (isLeftBent) landmarks[11] else landmarks[12]
        val ancaPiegata = if (isLeftBent) leftHip else rightHip
        val ginocchioPiegato = if (isLeftBent) leftKnee else rightKnee

        // Calcolo dell'apertura coxo-femorale tramite prodotto scalare
        val altezzaGinocchio = PoseAnalyzer.calculateAngle3D(spallaPiegata, ancaPiegata, ginocchioPiegato)

        var isValida = true
        var msgErrore = ""

        // Discretizzazione delle penalità esecutive basate sull'angolazione del femore
        if (altezzaGinocchio <= 100.0) penalita += 0.0
        else if (altezzaGinocchio in 100.1..110.0) penalita += 0.10
        else if (altezzaGinocchio in 110.1..125.0) penalita += 0.30
        else {
            penalita += 0.50
            isValida = false
            msgErrore = "Ginocchio troppo basso"
        }

        // Sovrascrive il messaggio di errore se il problema principale è l'assenza di Relevé
        if (!inReleve && isValida) {
            msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, valoreBase, penalita, inReleve, msgErrore)
    }
}
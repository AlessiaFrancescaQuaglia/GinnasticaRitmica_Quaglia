package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Modulo per l'analisi del "Grand Écart Laterale" (noto in gergo sportivo come Tilt).
 * È una spaccata eseguita in piedi sul piano frontale (l'atleta guarda verso l'obiettivo),
 * spesso accompagnata da una forte flessione laterale del tronco.
 */
class EquilibrioTilt : Difficolta {
    override var nome = "GRAND ÉCART LATERALE"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // SCUDO ANTI-ROTAZIONE
        // Se il framework sta processando un'evoluzione rotazionale sull'asse Y (Pivot),
        // questo thread abortisce l'analisi statica per prevenire collisioni logiche.
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }


        // FIREWALL ANTI-PENCHÉ (Analisi del Piano Frontale)
        // Poiché il Tilt (Laterale) e il Penché (Posteriore) condividono la medesima
        // topologia degli arti (una spaccata a 180°), la rete neurale 2D rischia di confonderli.
        // misuriamo la proiezione del Naso sull'asse X rispetto alle Spalle.
        // Se il viso cade all'interno del segmento delineato dalle spalle (con un margine di tolleranza),
        // l'atleta è posizionata frontalmente (Tilt).
        // Se il viso risulta traslato esternamente rispetto alle spalle, l'atleta è di profilo (Penché).
        val minShoulderX = kotlin.math.min(leftShoulder.x(), rightShoulder.x())
        val maxShoulderX = kotlin.math.max(leftShoulder.x(), rightShoulder.x())
        val margin = (maxShoulderX - minShoulderX) * 0.4f

        val isFacingFront = nose.x() >= (minShoulderX - margin) && nose.x() <= (maxShoulderX + margin)
        if (!isFacingFront) {
            return null // L'atleta è di profilo. Si cede l'esecuzione polimorfica alla classe EquilibrioPenche.
        }



        // FIREWALL ANTI-VOLO INTELLIGENTE (Filtro Gravitazionale)
        // Discrimina l'equilibrio statico dalle fasi aeree  (es. Grand Jeté).
        // Utilizza l'altezza del tronco come unità di misura scalare per verificare
        // l'effettivo ancoraggio al suolo del piede portante.
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val spallaY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val altezzaTronco = abs(bacinoY - spallaY)

        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val isLeftLowest = leftAnkle.y() > rightAnkle.y()
        val lowestAnkleY = if (isLeftLowest) leftAnkle.y() else rightAnkle.y()
        val lowestKneeAngle = if (isLeftLowest) leftKneeAngle else rightKneeAngle

        if (lowestKneeAngle < 135.0) return null
        if (lowestAnkleY < bacinoY + (altezzaTronco * 0.7f)) return null


        // FIREWALL ANTI-COSACCO
        // Il Grand Écart esige l'estensione completa (180°) di entrambi gli arti inferiori.
        // Qualsiasi asimmetria strutturale (una gamba tesa e l'altra flessa sotto i 120°)
        // invalida matematicamente l'elemento in esame.
        val isLeftStraight = leftKneeAngle > 135.0
        val isRightStraight = rightKneeAngle > 135.0
        val isLeftTucked = leftKneeAngle < 120.0
        val isRightTucked = rightKneeAngle < 120.0

        if ((isLeftStraight && isRightTucked) || (isRightStraight && isLeftTucked)) {
            return null
        }


        val isAppoggioLeft = leftAnkle.y() > rightAnkle.y()
        val supportAnkle = if (isAppoggioLeft) leftAnkle else rightAnkle
        val liftedAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle

        val midHipX = (leftHip.x() + rightHip.x()) / 2f
        val midHipLandmark = NormalizedLandmark.create(midHipX, bacinoY, (leftHip.z() + rightHip.z()) / 2f)

        //  Apertura Spaccata 3D (Mitigazione Distorsione Ottica)
        // Utilizza il Prodotto Scalare 3D per estrarre l'ampiezza coxo-femorale effettiva,
        // rendendo il calcolo robusto rispetto ad angolazioni di ripresa sub-ottimali.
        val aperturaSpaccata = PoseAnalyzer.calculateAngle3D(supportAnkle, midHipLandmark, liftedAnkle)
        if (aperturaSpaccata < 115.0) return null

        // controllo Sensori di Contatto 3D (Rilevamento Autonomo Prese)
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], liftedAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], liftedAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        //Calcolo Flessione Laterale (Trigonometria del Tilt)
        // Applica l'arcotangente ai vettori del centro di massa (Spalle-Bacino)
        // per calcolare l'esatta inclinazione laterale del tronco richiesta dal Codice
        val shoulderCenterX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val dx = shoulderCenterX - midHipX
        val dy = bacinoY - spallaY
        val gradiBusto = abs(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())))

        // Attribuzione del Valore Base normativo
        var valoreBase = 0.0
        if (gradiBusto in 50.0..130.0) {
            if (conAiuto) {
                nome = "EQUILIBRIO GRAND ÉCART LAT. (Tilt, con aiuto)"
                valoreBase = 0.40
            } else {
                nome = "EQUILIBRIO GRAND ÉCART LAT. (Tilt, senza aiuto)"
                valoreBase = 0.60
            }
        } else {
            nome = if (conAiuto) "EQUILIBRIO GRAND ÉCART LATERALE (Con aiuto)" else "EQUILIBRIO GRAND ÉCART LATERALE (Senza aiuto)"
            valoreBase = if (conAiuto) 0.30 else 0.50
        }

        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) valoreBase -= 0.10

        //  Quantificazione Deterministica delle Penalità Esecutive
        // Discretizza il deficit angolare rispetto ai 180°,
        // gestendo automaticamente l'eventuale Oversplit (angoli > 180°) senza penalizzarlo.
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val deficitAmpiezza = if (aperturaSpaccata >= 180.0) 0.0 else 180.0 - aperturaSpaccata
        if (deficitAmpiezza <= 10.0) penalita = 0.0
        else if (deficitAmpiezza in 10.1..25.0) penalita = 0.10
        else if (deficitAmpiezza in 25.1..45.0) penalita = 0.30
        else { penalita = 0.50; isValida = false; msgErrore = "Apertura laterale insufficiente" }

        // Drop tecnico dell'elemento in caso di esaurimento del valore positivo
        if (valoreBase <= 0.0) { isValida = false; if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta" }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
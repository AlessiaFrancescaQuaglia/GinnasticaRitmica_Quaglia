package com.example.ginnasticaritmica.vision.equilibri

import com.example.ginnasticaritmica.vision.Difficolta
import com.example.ginnasticaritmica.vision.PoseAnalyzer
import com.example.ginnasticaritmica.vision.RegoleGinnastica
import com.example.ginnasticaritmica.vision.RisultatoDifficolta
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.ginnasticaritmica.vision.giri.LucchettoGiri

/**
 * Modulo dedicato alla validazione dell'Equilibrio "Cosacco".
 * Rappresenta un'eccezione fisiologica e algoritmica al Firewall Anti-Volo standard.
 * Mentre quasi tutti gli equilibri esigono una gamba di appoggio tesa, il Cosacco
 * impone un ancoraggio a terra accompagnato da un'elevata flessione articolare (accosciata).
 */
class EquilibrioCosacco : Difficolta {
    override var nome = "COSACCO"

    override fun valuta(landmarks: List<NormalizedLandmark>): RisultatoDifficolta? {
        if (landmarks.size <= 32) return null

        val nose = landmarks[0]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        //  FIREWALL ANTI-VOLO (SPECIFICO PER ACCOSCIATE)
        val bacinoY = (leftHip.y() + rightHip.y()) / 2f
        val leftKneeAngle = PoseAnalyzer.calculateAngle3D(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = PoseAnalyzer.calculateAngle3D(rightHip, rightKnee, rightAnkle)

        val femoreSx = PoseAnalyzer.calculateDistance3D(leftHip, leftKnee)
        val femoreDx = PoseAnalyzer.calculateDistance3D(rightHip, rightKnee)

        // Eccezione geometrica: in un Equilibrio Cosacco, il ginocchio di appoggio
        // è estremamente piegato (θ < 90°). Per distinguerlo da un "Salto Cosacco" in volo
        // (in cui l'atleta rannicchia le gambe al petto in aria), si esige matematicamente
        // che la caviglia portante si trovi significativamente più in basso rispetto al bacino.
        val isLeftSquatPlanted = leftKneeAngle < 90.0 && leftAnkle.y() > bacinoY + (femoreSx * 0.5f)
        val isRightSquatPlanted = rightKneeAngle < 90.0 && rightAnkle.y() > bacinoY + (femoreDx * 0.5f)

        val isAppoggioLeft: Boolean

        // Gestione della Concorrenza: se il Singleton dei giri è bloccato, abortisce
        // preventivamente l'analisi per non sovrapporsi al thread rotazionale.
        if (LucchettoGiri.giroAttivo != null) {
            return null
        }

        // Accerta che una gamba sia in accosciata e l'altra (libera) sia estesa (>135°).
        if (isLeftSquatPlanted && rightKneeAngle > 135.0) {
            isAppoggioLeft = true
        } else if (isRightSquatPlanted && leftKneeAngle > 135.0) {
            isAppoggioLeft = false
        } else {
            return null // Posa transitoria o salto aereo. Il frame viene scartato.
        }

        val supportHip = if (isAppoggioLeft) leftHip else rightHip
        val freeAnkle = if (isAppoggioLeft) rightAnkle else leftAnkle
        val freeKnee = if (isAppoggioLeft) rightKnee else leftKnee
        val freeHip = if (isAppoggioLeft) rightHip else leftHip

        // Controllo Direzione
        // Estrapola il vettore "Tallone-Punta" per determinare l'orientamento spaziale del soggetto.
        val supportHeel = if (isAppoggioLeft) landmarks[29] else landmarks[30]
        val supportToe = if (isAppoggioLeft) landmarks[31] else landmarks[32]
        val isFacingRight = supportToe.x() > supportHeel.x()

        // Verifica che la gamba libera sia proiettata nel semispazio frontale (avanti).
        val isForwardExtended = if (isFacingRight) freeAnkle.x() > supportHip.x() else freeAnkle.x() < supportHip.x()
        if (!isForwardExtended) return null

        // Sensore di Prossimità per Astrazione "Con Aiuto"
        // Interseca le coordinate 3D dei polsi con la caviglia per determinare in automatico
        // se l'atleta sta sostenendo la gamba con l'uso delle mani.
        val distPolsoSx = PoseAnalyzer.calculateDistance3D(landmarks[15], freeAnkle)
        val distPolsoDx = PoseAnalyzer.calculateDistance3D(landmarks[16], freeAnkle)
        val conAiuto = (distPolsoSx < 0.15f || distPolsoDx < 0.15f)

        var valoreBase = 0.0

        // Sensore Gravitazionale (Asse Y): Verifica se la caviglia supera la quota altimetrica del viso.
        val isPiedeAlto = freeAnkle.y() < nose.y() + 0.05f

        if (isPiedeAlto && conAiuto) {
            nome = "EQUILIBRIO COSACCO (Piede alto, aiuto)"
            valoreBase = 0.20
        } else {
            nome = "EQUILIBRIO COSACCO (Gamba avanti orizz.)"
            valoreBase = 0.10
        }

        // Verifica del Relevé (Tallone sollevato da terra)
        val inReleve = RegoleGinnastica.verificaReleve(landmarks, isAppoggioLeft)
        if (!inReleve) valoreBase -= 0.10

        // Applicazione deterministica delle penalità di esecuzione
        var penalita = 0.0
        var isValida = true
        var msgErrore = ""

        val freeLegTension = if (isAppoggioLeft) rightKneeAngle else leftKneeAngle
        if (freeLegTension >= 170.0) penalita = 0.0
        else if (freeLegTension in 160.0..169.9) penalita = 0.10
        else if (freeLegTension in 145.0..159.9) penalita = 0.30
        else {
            penalita = 0.50
            isValida = false
            msgErrore = "Gamba libera piegata"
        }

        // Se la gamba cede sotto l'asse orizzontale, il requisito tecnico decade.
        if (!isPiedeAlto && freeAnkle.y() > supportHip.y() + 0.15f) {
            isValida = false
            msgErrore = "Gamba sotto l'orizzontale"
        }

        if (valoreBase <= 0.0) {
            isValida = false
            if (msgErrore.isEmpty()) msgErrore = "Eseguito in Pianta"
        }

        return RisultatoDifficolta(isValida, if (valoreBase > 0) valoreBase else 0.0, penalita, inReleve, msgErrore)
    }
}
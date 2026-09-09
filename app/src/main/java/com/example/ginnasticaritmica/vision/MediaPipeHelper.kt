package com.example.ginnasticaritmica.vision

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Questa classe agisce da strato di astrazione (Abstraction Layer) tra l'hardware
 * del dispositivo Android e il framework MediaPipe.
 * Gestisce il ciclo di vita del modello (allocazione, esecuzione asincrona e deallocazione),
 * garantendo che l'estrazione topologica avvenga nel rispetto dei rigorosi vincoli di real-time processing.
 */
class MediaPipeHelper(
    var context: Context,

    //
    var minPoseDetectionConfidence: Float = 0.4f,
    var minPosePresenceConfidence: Float = 0.4f,
    var minTrackingConfidence: Float = 0.4f,

    var listener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    /**
     * Garbage Collection / Deallocazione esplicita del tensore.
     * Libera la memoria RAM/VRAM occupata dal modello per prevenire Memory Leaks
     * durante i cambi di Activity o la sospensione dell'applicazione.
     */
    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    /**
     * Bootstrapping della Rete Neurale.
     */
    private fun setupPoseLandmarker() {
        // Utilizzo del modello "Lite": scelta progettuale per dispositivi Edge.
        // Riduce il carico computazionale garantendo un throughput stabile a 30fps, sacrificando la
        // micro-precisione delle dita in favore dell'analisi macro-strutturale degli arti (sufficiente per la ginnastica).
        val modelName = "pose_landmarker_lite.task"


        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .setDelegate(Delegate.CPU)

        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            // Imposta l'engine in modalità asincrona (Pipeline non bloccante)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            // Vincolo di Cardinalità:
            // Istruisce il tensore a tracciare una sola persona. Questo dimezza
            // il carico computazionale e previene errori di classificazione se
            // altre persone (es. allenatori) entrano nell'inquadratura.
            .setNumPoses(1)
            .setResultListener { result, image ->
                listener?.onResults(result, image)
            }
            .setErrorListener { error ->
                listener?.onError(error.message ?: "Errore MediaPipe")
            }

        try {
            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d("MediaPipeHelper", "MediaPipe caricato su CPU con successo in un lampo!")
        } catch (e: Exception) {
            Log.e("MediaPipeHelper", "Errore critico avvio MediaPipe", e)
            listener?.onError("Impossibile avviare IA: ${e.message}")
        }
    }

    /**
     * Iniezione Asincrona dei Frame (Video Ingestion).
     * @param mpImage Il fotogramma corrente catturato dalla telecamera.
     * @param rotationDegrees Compensazione dell'orientamento del sensore CMOS del dispositivo.
     */
    fun detectLiveStream(mpImage: MPImage, rotationDegrees: Int = 0) {
        // Generazione del Timestamp univoco per il frame corrente.
        // Necessario per sincronizzare il flusso asincrono ed evitare l'out-of-order execution
        // dei fotogrammi all'interno della pipeline neurale.
        val frameTime = SystemClock.uptimeMillis()

        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        // Delegazione non bloccante dell'inferenza a un thread in background gestito da MediaPipe.
        poseLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    /**
     * Interfaccia di Callback (Observer Pattern).
     * Utilizzata per restituire il Tensore Topologico (vettori 3D) alla UI o
     * al motore logico (PoseAnalyzer) una volta terminata l'inferenza del frame.
     */
    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: PoseLandmarkerResult, input: MPImage)
    }
}
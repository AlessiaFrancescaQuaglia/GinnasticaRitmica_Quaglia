package com.example.ginnasticaritmica.vision

import android.content.Context
import android.os.SystemClock
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class MediaPipeHelper(
    var context: Context,
    var minPoseDetectionConfidence: Float = 0.5f,
    var minPosePresenceConfidence: Float = 0.5f,
    var minTrackingConfidence: Float = 0.5f,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var listener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    private fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()

        // Usiamo il modello full che hai messo nella cartella assets
        val modelName = "pose_landmarker_full.task"
        baseOptionsBuilder.setModelAssetPath(modelName)

        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setResultListener { result, image ->
                listener?.onResults(result, image)
            }
            .setErrorListener { error ->
                listener?.onError(error.message ?: "Errore sconosciuto in MediaPipe")
            }

        try {
            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener?.onError("Impossibile inizializzare il Pose Landmarker: ${e.message}")
        }
    }

    // Funzione chiamata a ogni fotogramma della fotocamera per rilevare la posa in modo asincrono
    fun detectLiveStream(mpImage: MPImage) {
        val frameTime = SystemClock.uptimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: PoseLandmarkerResult, input: MPImage)
    }

    companion object {
        const val MODEL_POSE_LANDMARKER_FULL = 0
    }
}
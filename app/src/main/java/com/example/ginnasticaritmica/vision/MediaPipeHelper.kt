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

class MediaPipeHelper(
    var context: Context,
    var minPoseDetectionConfidence: Float = 0.4f,
    var minPosePresenceConfidence: Float = 0.4f,
    var minTrackingConfidence: Float = 0.4f,
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
        val modelName = "pose_landmarker_lite.task"

        // ==========================================
        // FIX STARTUP 15 SECONDI
        // Usiamo la CPU di default. È istantanea e regge il modello Lite a 30fps!
        // Niente più blocchi dovuti ai driver grafici del tablet.
        // ==========================================
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .setDelegate(Delegate.CPU)

        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setNumPoses(1) // Cerca solo una ginnasta alla volta (aumenta gli FPS)
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

    fun detectLiveStream(mpImage: MPImage, rotationDegrees: Int = 0) {
        val frameTime = SystemClock.uptimeMillis()

        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotationDegrees)
            .build()

        poseLandmarker?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: PoseLandmarkerResult, input: MPImage)
    }
}
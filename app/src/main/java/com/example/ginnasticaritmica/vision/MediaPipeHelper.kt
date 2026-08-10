package com.example.ginnasticaritmica.vision

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class MediaPipeHelper(
    var context: Context,
    // Abbiamo abbassato i valori a 0.4f. L'IA ora sarà più reattiva
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
        // TORNICAMO AL MODELLO LITE: Velocità massima (niente più scatti o ritardi)
        val modelName = "pose_landmarker_lite.task"

        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .setDelegate(Delegate.GPU)

        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            // ECCO IL FIX: Ora usiamo davvero le variabili, i warning gialli spariranno!
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
        } catch (e: Exception) {
            // FIX anche per il warning "e is never used"
            Log.e("MediaPipeHelper", "Errore GPU, passo alla CPU", e)

            // Fallback CPU
            val cpuOptions = BaseOptions.builder()
                .setModelAssetPath(modelName)
                .setDelegate(Delegate.CPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(cpuOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence) // Applicato anche qui
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setMinTrackingConfidence(minTrackingConfidence)
                .setNumPoses(1)
                .setResultListener { result, image ->
                    listener?.onResults(result, image)
                }
                .setErrorListener { error ->
                    listener?.onError(error.message ?: "Errore MediaPipe")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        }
    }

    fun detectLiveStream(mpImage: MPImage) {
        val frameTime = SystemClock.uptimeMillis()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: PoseLandmarkerResult, input: MPImage)
    }
}
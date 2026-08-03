package com.example.ginnasticaritmica.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 20f
    }

    fun setResults(poseLandmarkerResults: PoseLandmarkerResult, width: Int, height: Int) {
        this.results = poseLandmarkerResults
        this.imageWidth = width
        this.imageHeight = height
        invalidate() // Ridisegna la vista
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val skeleton = results ?: return
        if (skeleton.landmarks().isEmpty()) return

        // Calcoliamo la scala e l'offset per adattarsi allo schermo come fa CameraX (Center Crop)
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)

        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        for (landmarkList in skeleton.landmarks()) {
            for (landmark in landmarkList) {
                // Coordinate mappate con scala e centratura corrette
                val x = landmark.x() * imageWidth * scale + dx
                val y = landmark.y() * imageHeight * scale + dy

                canvas.drawCircle(x, y, 12f, pointPaint)
            }
        }
    }
}
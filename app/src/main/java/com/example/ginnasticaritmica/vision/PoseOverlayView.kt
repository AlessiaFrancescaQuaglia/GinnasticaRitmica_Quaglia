package com.example.ginnasticaritmica.vision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // --- LOGICA DI STABILIZZAZIONE (ANTI-RUMORE) ---
    private var candidateResult: Pair<String, RisultatoDifficolta>? = null
    private var frameCounter: Int = 0
    private var missedFrames: Int = 0
    private val MAX_TOLERANCE_FRAMES = 2

    // --- PERSISTENZA ED "EFFETTO VAR" (2.5 SECONDI) ---
    private var persistedResult: Pair<String, RisultatoDifficolta>? = null
    private var persistedLandmarks: List<NormalizedLandmark>? = null
    private var lastDetectionTime: Long = 0L
    private val PERSISTENCE_DURATION = 2500L // 2.5 secondi

    // Pennello per i puntini dello scheletro
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 20f
    }

    // Pennello per i messaggi piccoli (Penalità)
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 60f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    // Pennello per il verdetto principale
    private val resultPaint = Paint().apply {
        textSize = 70f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    fun setResults(poseLandmarkerResults: PoseLandmarkerResult, width: Int, height: Int) {
        this.results = poseLandmarkerResults
        this.imageWidth = width
        this.imageHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        val skeleton = results
        val liveLandmarks = if (skeleton != null && skeleton.landmarks().isNotEmpty()) {
            skeleton.landmarks()[0]
        } else {
            null
        }

        // 1. ANALISI DEL FOTOGRAMMA CORRENTE
        val currentAnalisi = if (liveLandmarks != null) {
            AnalizzatoreSalti.analizza(liveLandmarks) ?: AnalizzatoreEquilibri.analizza(liveLandmarks)
        } else {
            null
        }

        // 2. MOTORE DI RICONOSCIMENTO
        if (currentAnalisi != null) {
            if (candidateResult?.first == currentAnalisi.first) {
                frameCounter++
                missedFrames = 0
            } else {
                candidateResult = currentAnalisi
                frameCounter = 1
                missedFrames = 0
            }

            // FIX DEL BUG: Capiamo che è un salto leggendo il nome, NON leggendo il relevé!
            val nomePosa = candidateResult?.first?.uppercase() ?: ""
            val isSalto = nomePosa.contains("SALTO") || nomePosa.contains("CABRIOLE") || nomePosa.contains("JETÉ")

            val requiredFrames = if (isSalto) 1 else 2

            if (frameCounter >= requiredFrames) {
                persistedResult = currentAnalisi
                persistedLandmarks = liveLandmarks
                lastDetectionTime = System.currentTimeMillis()
            }
        } else {
            missedFrames++
            if (missedFrames > MAX_TOLERANCE_FRAMES) {
                frameCounter = 0
                candidateResult = null
            }
        }

        // ==========================================
        // 3. DISEGNO DELLO SCHERMO (Modalità Pulita)
        // ==========================================
        if (System.currentTimeMillis() - lastDetectionTime < PERSISTENCE_DURATION) {

            persistedLandmarks?.let { frozenLandmarks ->
                for (landmark in frozenLandmarks) {
                    val x = landmark.x() * imageWidth * scale + dx
                    val y = landmark.y() * imageHeight * scale + dy
                    canvas.drawCircle(x, y, 10f, pointPaint)
                }
            }

            drawResultOnCanvas(canvas, persistedResult)

        } else {
            drawSearching(canvas)
        }
    }

    // --- METODO DI SUPPORTO PER DISEGNARE A SCHERMO ---
    private fun drawResultOnCanvas(canvas: Canvas, analisi: Pair<String, RisultatoDifficolta>?) {
        val (nomeDifficolta, risultato) = analisi ?: return

        // FIX DEL BUG ANCHE NEL TESTO A SCHERMO
        val nomeMaiuscolo = nomeDifficolta.uppercase()
        val isSalto = nomeMaiuscolo.contains("SALTO") || nomeMaiuscolo.contains("CABRIOLE") || nomeMaiuscolo.contains("JETÉ")

        val testoReleve = if (isSalto) {
            "" // Nessun testo per i salti
        } else {
            if (risultato.inReleve) "[RELEVÉ]" else "[PIANTA]" // Tornano le etichette per gli equilibri!
        }

        if (risultato.isValida) {
            resultPaint.color = Color.GREEN
            val stringaValida = "$nomeDifficolta: ${String.format("%.2f", risultato.valoreFinale)}p $testoReleve"
            canvas.drawText(stringaValida, 50f, 100f, resultPaint)

            if (risultato.penalita > 0.0) {
                textPaint.color = Color.YELLOW
                canvas.drawText("(Penalità: -${String.format("%.2f", risultato.penalita)})", 50f, 170f, textPaint)
            }
        } else {
            resultPaint.color = Color.RED
            canvas.drawText("$nomeDifficolta NON VALIDO $testoReleve", 50f, 100f, resultPaint)

            textPaint.color = Color.RED
            canvas.drawText(risultato.messaggioErrore, 50f, 170f, textPaint)
        }
    }

    private fun drawSearching(canvas: Canvas) {
        resultPaint.color = Color.WHITE
        canvas.drawText("IN RICERCA POSA...", 50f, 100f, resultPaint)
    }
}
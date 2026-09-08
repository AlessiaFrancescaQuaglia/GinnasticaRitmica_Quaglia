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
import androidx.core.graphics.toColorInt

/**
 * CustomView responsabile del rendering grafico in sovrimpressione sul video.
 * Oltre a disegnare, gestisce la "Persistenza Visiva" dell'output: agisce come un buffer
 * che stabilizza i risultati prodotti dagli analizzatori AI, evitando che le scritte
 * lampeggino in modo illeggibile a ogni singolo frame processato.
 */
class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // LOGICA DI STABILIZZAZIONE ANTI-RUMORE
    // Una difficoltà viene considerata "candidata" solo se l'AI la riconosce per più frame consecutivi.
    private var candidateResult: Pair<String, RisultatoDifficolta>? = null
    private var frameCounter: Int = 0
    private var missedFrames: Int = 0
    private val MAX_TOLERANCE_FRAMES = 2


    // Una volta che un elemento è confermato, la View ne "congela" il risultato sullo schermo
    // per 2.5 secondi. Questo permette di leggere comodamente il verdetto
    // anche se l'azione dell'atleta in video è durata solo una frazione di secondo (come nei Salti).
    private var persistedResult: Pair<String, RisultatoDifficolta>? = null
    private var persistedLandmarks: List<NormalizedLandmark>? = null
    private var lastDetectionTime: Long = 0L
    private val PERSISTENCE_DURATION = 2500L // 2.5 secondi

    // Pennello grafico per i 33 nodi scheletrici
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 20f
    }

    // Pennello grafico per i messaggi secondari (Penalità / Avvisi)
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 55f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    // Pennello grafico per il verdetto principale
    private val resultPaint = Paint().apply {
        textSize = 65f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    /**
     * Metodo esposto al Fragment (AnalisiPosaFragment) per iniettare i dati grezzi
     * ricevuti dal modello MediaPipe. La chiamata a invalidate() forza il ricalcolo del Canvas.
     */
    fun setResults(poseLandmarkerResults: PoseLandmarkerResult, width: Int, height: Int) {
        this.results = poseLandmarkerResults
        this.imageWidth = width
        this.imageHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calcolo del ridimensionamento dinamico (Scale & Offset)
        // Siccome la fotocamera (es. 480x640) ha proporzioni diverse dallo schermo fisico del tablet,
        // è necessario calcolare un fattore di scala per mappare i vettori AI sulle coordinate fisiche dello schermo.
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        val skeleton = results
        val liveLandmarks = if (skeleton != null && skeleton.landmarks().isNotEmpty()) {
            skeleton.landmarks()[0]
        } else {
            null
        }

        // ORCHESTRAZIONE DEGLI ANALIZZATORI
        // Viene rispettato il pattern "Chain of Responsibility":
        // Prima si controlla se la posa rispetta i vincoli rigidi degli Equilibri.
        // Se l'esito è nullo, si tenta la valutazione di Giri e infine Salti.
        val currentAnalisi = if (liveLandmarks != null) {
            AnalizzatoreEquilibri.analizza(liveLandmarks)
                ?: AnalizzatoreGiri.analizza(liveLandmarks)
                ?: AnalizzatoreSalti.analizza(liveLandmarks)
        } else {
            null
        }

        //  LOGICA DI VALIDAZIONE E CONGELAMENTO FRAME
        if (currentAnalisi != null) {
            if (candidateResult?.first == currentAnalisi.first) {
                // Conferma consecutiva dello stesso elemento
                frameCounter++
                missedFrames = 0
            } else {
                // Nuovo elemento rilevato
                candidateResult = currentAnalisi
                frameCounter = 1
                missedFrames = 0
            }

            val nomePosa = candidateResult?.first?.uppercase() ?: ""
            // I gesti esplosivi (Salti e Giri) sono così rapidi che vengono validati
            // e congelati a video già al primo fotogramma utile (requiredFrames = 1).
            // Le pose statiche richiedono invece la stabilità di almeno 2 fotogrammi.
            val isSalto = nomePosa.contains("SALTO") || nomePosa.contains("CABRIOLE") || nomePosa.contains("JETÉ")
            val isGiro = nomePosa.contains("GIRO") || nomePosa.contains("PIVOT")

            val requiredFrames = if (isSalto || isGiro) 1 else 2

            if (frameCounter >= requiredFrames) {
                // Fotografia del risultato: salvataggio in cache per la visualizzazione prolungata
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


        // 4. RENDERING GRAFICO
        // Se non è ancora trascorso il timer di persistenza (2500ms),viene disegnato l'output a schermo.
        if (System.currentTimeMillis() - lastDetectionTime < PERSISTENCE_DURATION) {

            persistedLandmarks?.let { frozenLandmarks ->
                // Disegno della nuvola di punti articolari sul corpo dell'atleta
                for (landmark in frozenLandmarks) {
                    val x = landmark.x() * imageWidth * scale + dx
                    val y = landmark.y() * imageHeight * scale + dy
                    canvas.drawCircle(x, y, 10f, pointPaint)
                }
            }

            // Richiamo della funzione dedicata per il pannello informativo inferiore
            drawResultOnCanvas(canvas, persistedResult)

        } else {
            drawSearching(canvas)
        }
    }

    /**
     * Disegna il banner  stile "Dashboard" nella parte inferiore dello schermo.
     * Segnala in verde gli elementi convalidati dal regolamento, o in rosso gli errori biomeccanici.
     */
    private fun drawResultOnCanvas(canvas: Canvas, analisi: Pair<String, RisultatoDifficolta>?) {
        val (nomeDifficolta, risultato) = analisi ?: return
        val widthF = canvas.width.toFloat()
        val heightF = canvas.height.toFloat()

        val nomeMaiuscolo = nomeDifficolta.uppercase()
        val isSalto = nomeMaiuscolo.contains("SALTO") || nomeMaiuscolo.contains("CABRIOLE") || nomeMaiuscolo.contains("JETÉ")
        val isGiro = nomeMaiuscolo.contains("GIRO") || nomeMaiuscolo.contains("PIVOT")

        // Nelle pose statiche (Equilibri), il regolamento distingue tra appoggio piatto ("Pianta")
        // e sollevato sulla punta ("Relevé"), assegnando punteggi differenti.
        val testoReleve = if (isSalto || isGiro) {
            ""
        } else {
            if (risultato.inReleve) " [RELEVÉ]" else " [PIANTA]"
        }


        // UI Layout dinamico: il pannello è più alto se deve ospitare anche
        // i messaggi di errore o decurtazione del punteggio, altrimenti è compatto.
        val cardWidth = widthF - 100f // Margine laterale di 50px per lato
        val cardHeight = if (risultato.penalita > 0.0 || !risultato.isValida) 240f else 160f
        val cardLeft = 50f
        val cardTop = heightF - cardHeight - 80f // Posizionato in basso con un po' di margine
        val cornerRadius = 32f

        // Sfondo della Card (Nero semi-trasparente )
        val bgPaint = Paint().apply {
            color = "#CC121212".toColorInt()
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(20f, 0f, 10f, Color.argb(100, 0, 0, 0))
        }
        canvas.drawRoundRect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight, cornerRadius, cornerRadius, bgPaint)

        // 2. Barra laterale indicatrice di stato (Verde = Valido, Rosso = Non valido)
        val indicatorPaint = Paint().apply {
            color = if (risultato.isValida) "#00FF88".toColorInt() else "#FF5252".toColorInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardLeft, cardTop, cardLeft + 16f, cardTop + cardHeight, cornerRadius, cornerRadius, indicatorPaint)

        // 3. Scritta Principale (Nome Elemento + Punteggio Calcolato)
        resultPaint.textSize = 55f
        resultPaint.color = Color.WHITE
        val stringaValida = if (risultato.isValida) {
            "$nomeDifficolta: ${String.format("%.2f", risultato.valoreFinale)}p$testoReleve"
        } else {
            "$nomeDifficolta NON VALIDO$testoReleve"
        }
        canvas.drawText(stringaValida, cardLeft + 45f, cardTop + 65f, resultPaint)

        // 4. Seconda Riga (Penalità per flessibilità insufficiente o Messaggio Errore)
        textPaint.textSize = 45f
        if (risultato.isValida && risultato.penalita > 0.0) {
            textPaint.color = "#FFD54F".toColorInt() // Giallo avviso (Warning)
            canvas.drawText("Penalità applicata: -${String.format("%.2f", risultato.penalita)}", cardLeft + 45f, cardTop + 130f, textPaint)
        } else if (!risultato.isValida) {
            textPaint.color = "#FF5252".toColorInt() // Rosso errore (Error)
            canvas.drawText(risultato.messaggioErrore, cardLeft + 45f, cardTop + 130f, textPaint)
        }
    }

    private fun drawSearching(canvas: Canvas) {
        // Callback vuota intenzionalmente: se la rete non rileva nulla,
        // lo schermo viene lasciato "pulito" in trasparenza per mostrare lo streaming video senza ostacoli.
    }
}
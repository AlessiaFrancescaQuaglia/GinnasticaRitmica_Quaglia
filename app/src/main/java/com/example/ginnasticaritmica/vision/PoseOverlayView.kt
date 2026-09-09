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
 * Modulo HUD - Heads-Up Display.
 * Questa CustomView rappresenta il terminale di output del sistema di Computer Vision.
 * Oltre a occuparsi del rendering grafico (disegno dei tensori scheletrici sul video),
 * implementa un fondamentale strato di Debouncing e Persistenza Visiva.
 * Questa classe agisce come un buffer (Filtro Passa-Basso) che stabilizza
 * i risultati a video, evitando che l'interfaccia utente risulti illeggibile.
 */
class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {


    // BUFFER DEI DATI DI INFERENZA
    private var results: PoseLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1


    // LOGICA DI STABILIZZAZIONE E TOLLERANZA AI GUASTI
    // Macchina a stati per il filtro anti-rumore. Una classificazione prodotta dall'AI
    // viene considerata "Candidata" e promossa a schermo solo se dimostra una certa
    // coerenza temporale (es. mantenuta per X frame consecutivi).
    private var candidateResult: Pair<String, RisultatoDifficolta>? = null
    private var frameCounter: Int = 0
    private var missedFrames: Int = 0
    // Tolleranza per le occlusioni momentanee: se l'AI perde la posa per 1 o 2 frame,
    // il sistema non resetta immediatamente il conteggio, assorbendo il micro-guasto.
    private val MAX_TOLERANCE_FRAMES = 2


    // LATCH DI PERSISTENZA VISIVA
    // Una volta che un elemento è confermato, la View ne "congela" l'identità e il punteggio a
    // schermo per 2.5 secondi. Questo soddisfa un requisito UX: permette al giudice/utente di
    // leggere il verdetto anche se l'azione biomeccanica dell'atleta è durata solo pochi millisecondi.
    private var persistedResult: Pair<String, RisultatoDifficolta>? = null
    private var persistedLandmarks: List<NormalizedLandmark>? = null
    private var lastDetectionTime: Long = 0L
    private val PERSISTENCE_DURATION = 2500L // Timeout di persistenza (2.5 secondi)


    // PIPELINE GRAFICA
    // Pennello grafico per la mappatura dei 33 nodi scheletrici della topologia MediaPipe.
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 20f
    }

    // Pennello grafico per i messaggi secondari (Telemetria, Penalità, Avvisi di Errore)
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 55f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    // Pennello grafico per il verdetto principale (Nome Elemento e Punteggio)
    private val resultPaint = Paint().apply {
        textSize = 65f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    /**
     * Metodo esposto al Fragment (Controller) per iniettare i dati grezzi calcolati dalla rete neurale.
     * La chiamata a `invalidate()` invia un segnale al sistema operativo Android per richiedere un refresh hardware del Canvas.
     */
    fun setResults(poseLandmarkerResults: PoseLandmarkerResult, width: Int, height: Int) {
        this.results = poseLandmarkerResults
        this.imageWidth = width
        this.imageHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        // TRASFORMAZIONE AFFINE DELLE COORDINATE (Scale & Offset)
        // Le matrici CMOS delle fotocamere (es. 640x480) raramente corrispondono all'aspect ratio
        // del display fisico del dispositivo. Questo blocco calcola matematicamente il fattore di scala
        //  necessario per proiettare i punti normalizzati [0,1] della rete neurale sulla superficie del display.
        //Questo consente ai landmark di essere stampati correttamente sugli arti
        val scale = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val dx = (width - imageWidth * scale) / 2f
        val dy = (height - imageHeight * scale) / 2f

        val skeleton = results
        val liveLandmarks = if (skeleton != null && skeleton.landmarks().isNotEmpty()) {
            skeleton.landmarks()[0]
        } else {
            null
        }


        // Chain of Responsibility Pattern
        // Il flusso interroga in cascata i moduli specializzati (Equilibri -> Giri -> Salti).
        // Il primo modulo che riconosce e acquisisce matematicamente la posa interrompe
        // la catena, restituendo il payload (Pair) con il risultato.
        val currentAnalisi = if (liveLandmarks != null) {
            AnalizzatoreEquilibri.analizza(liveLandmarks)
                ?: AnalizzatoreGiri.analizza(liveLandmarks)
                ?: AnalizzatoreSalti.analizza(liveLandmarks)
        } else {
            null
        }


        //MACCHINA A STATI DI DEBOUNCING (Validazione Segnale)
        if (currentAnalisi != null) {
            if (candidateResult?.first == currentAnalisi.first) {
                // Il segnale è coerente: l'AI ha confermato la stessa classificazione nel frame successivo
                frameCounter++
                missedFrames = 0
            } else {
                //reset della macchina a stati e inizio nuovo conteggio
                candidateResult = currentAnalisi
                frameCounter = 1
                missedFrames = 0
            }

            val nomePosa = candidateResult?.first?.uppercase() ?: ""

            // Logica di temporizzazione dinamica:
            // I gesti cinetici esplosivi (Salti e Pivot) attraversano l'apice per frazioni di secondo.
            // Vengono pertanto validati e bloccati in cache già al 1° fotogramma utile.
            // Le pose isometiche (Equilibri) richiedono invece la stabilità confermata
            // su almeno 2 fotogrammi consecutivi per scremare i falsi positivi.
            val isSalto = nomePosa.contains("SALTO") || nomePosa.contains("CABRIOLE") || nomePosa.contains("JETÉ")
            val isGiro = nomePosa.contains("GIRO") || nomePosa.contains("PIVOT")

            val requiredFrames = if (isSalto || isGiro) 1 else 2

            if (frameCounter >= requiredFrames) {
                // Fotografia del risultato (Snapshot): allocazione del payload in memoria persistente
                persistedResult = currentAnalisi
                persistedLandmarks = liveLandmarks
                lastDetectionTime = System.currentTimeMillis()
            }
        } else {
            missedFrames++
            // Se l'AI non rileva l'elemento per più di X frame consecutivi, svuota la cache temporanea
            if (missedFrames > MAX_TOLERANCE_FRAMES) {
                frameCounter = 0
                candidateResult = null
            }
        }


        // RENDERING GRAFICO SUL CANVAS HARDWARE
        // Se il Watchdog di persistenza non è scaduto (< 2500ms), invia i draw-calls alla GPU.
        if (System.currentTimeMillis() - lastDetectionTime < PERSISTENCE_DURATION) {

            persistedLandmarks?.let { frozenLandmarks ->
                // Mappatura e disegno della nuvola di punti articolari sul corpo dell'atleta
                for (landmark in frozenLandmarks) {
                    val x = landmark.x() * imageWidth * scale + dx
                    val y = landmark.y() * imageHeight * scale + dy
                    canvas.drawCircle(x, y, 10f, pointPaint)
                }
            }

            // Richiamo del sub-componente UI dedicato al pannello informativo inferiore
            drawResultOnCanvas(canvas, persistedResult)

        } else {
            drawSearching(canvas)
        }
    }

    /**
     * Generazione Dinamica dell'HUD (Heads-Up Display) a schermo.
     * Costruisce il pannello (Card) in sovrimpressione che comunica l'esito della classificazione,
     * supportando l'accessibilità visiva tramite color-coding (Verde = Valido, Rosso = Non valido/Penalità).
     */
    private fun drawResultOnCanvas(canvas: Canvas, analisi: Pair<String, RisultatoDifficolta>?) {
        val (nomeDifficolta, risultato) = analisi ?: return
        val widthF = canvas.width.toFloat()
        val heightF = canvas.height.toFloat()

        val nomeMaiuscolo = nomeDifficolta.uppercase()
        val isSalto = nomeMaiuscolo.contains("SALTO") || nomeMaiuscolo.contains("CABRIOLE") || nomeMaiuscolo.contains("JETÉ")
        val isGiro = nomeMaiuscolo.contains("GIRO") || nomeMaiuscolo.contains("PIVOT")

        // Nelle pose statiche (Equilibri), il Codice distingue l'appoggio plantare ("Pianta")
        // dall'elevazione sull'avampiede ("Relevé"), assegnando decurtazioni specifiche.
        // Questo blocco estrapola e formatta l'informazione da aggiungere in coda alla stringa.
        val testoReleve = if (isSalto || isGiro) {
            ""
        } else {
            if (risultato.inReleve) " [RELEVÉ]" else " [PIANTA]"
        }


        // UI LAYOUT ENGINE
        // Calcola dinamicamente le dimensioni del Box in base al payload testuale da stampare.
        // Se vi sono penalità algoritmiche o errori esecutivi (Es. gamba piegata), il box
        // incrementa la propria altezza per ospitare la seconda riga di testo.
        val cardWidth = widthF - 100f // Margine laterale fisso
        val cardHeight = if (risultato.penalita > 0.0 || !risultato.isValida) 240f else 160f
        val cardLeft = 50f
        val cardTop = heightF - cardHeight - 80f // Ancoraggio asse Y (Bottom con margine)
        val cornerRadius = 32f

        // Renderizzazione Sfondo della Card
        val bgPaint = Paint().apply {
            color = "#CC121212".toColorInt()
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(20f, 0f, 10f, Color.argb(100, 0, 0, 0))
        }
        canvas.drawRoundRect(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight, cornerRadius, cornerRadius, bgPaint)

        // Barra laterale indicatrice di stato
        val indicatorPaint = Paint().apply {
            color = if (risultato.isValida) "#00FF88".toColorInt() else "#FF5252".toColorInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardLeft, cardTop, cardLeft + 16f, cardTop + cardHeight, cornerRadius, cornerRadius, indicatorPaint)

        //Stampa della Stringa Primaria (Nome Elemento Cinetico + Punteggio Calcolato Integrale)
        resultPaint.textSize = 55f
        resultPaint.color = Color.WHITE
        val stringaValida = if (risultato.isValida) {
            "$nomeDifficolta: ${String.format("%.2f", risultato.valoreFinale)}p$testoReleve"
        } else {
            "$nomeDifficolta NON VALIDO$testoReleve"
        }
        canvas.drawText(stringaValida, cardLeft + 45f, cardTop + 65f, resultPaint)

        // Stampa della Riga Diagnostica (Sub-Text / Error Messaging)
        textPaint.textSize = 45f
        if (risultato.isValida && risultato.penalita > 0.0) {
            // Caso Warning: Figura riconosciuta ma con deficit di ampiezza. Testo giallo
            textPaint.color = "#FFD54F".toColorInt()
            canvas.drawText("Penalità applicata: -${String.format("%.2f", risultato.penalita)}", cardLeft + 45f, cardTop + 130f, textPaint)
        } else if (!risultato.isValida) {
            // Caso Fatal: Figura respinta dai Firewall (es. atterraggio fallito). Testo rosso.
            textPaint.color = "#FF5252".toColorInt()
            canvas.drawText(risultato.messaggioErrore, cardLeft + 45f, cardTop + 130f, textPaint)
        }
    }

    /**
     * Stato di Idle / Inattività.
     */
    private fun drawSearching(canvas: Canvas) {
        // Callback lasciata intenzionalmente vuota.
        // Se la rete neurale è nello stato di "Searching" (nessuna posa rilevata)
        // o se la persistenza è scaduta, lo schermo viene lasciato in completa trasparenza
        // (alpha channel zero) per non occludere l'esperienza di monitoraggio video (Viewfinder).
    }
}
package com.example.ginnasticaritmica.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.vision.MediaPipeHelper
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment responsabile dell'acquisizione video a bassa latenza e dell'orchestrazione di MediaPipe.
 * Funge da ponte tra l'hardware ottico (CameraX) e l'analisi biomeccanica dei movimenti.
 */
class AnalisiPosaFragment : Fragment(), MediaPipeHelper.LandmarkerListener {

    // Thread pool dedicato esclusivamente all'analisi dei frame per non bloccare la UI
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var txtRisultato: TextView

    // CustomView che si occupa di renderizzare lo scheletro vettoriale sopra il video
    private lateinit var poseOverlayView: com.example.ginnasticaritmica.vision.PoseOverlayView

    //mutex logico per prevenire l'accumulo di frame se l'inferenza rallenta
    private var isProcessing = false
    private var mediaPipeHelper: MediaPipeHelper? = null

    // Coordinate dinamiche per mappare il tensore 3D sulle dimensioni reali dello schermo
    private var frameWidth = 1
    private var frameHeight = 1

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted && isAdded) {
                avviaFotocamera()
            } else if (isAdded) {
                Toast.makeText(requireContext(), "Permesso fotocamera necessario!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_analisi_posa, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewFinder = view.findViewById(R.id.viewFinder)
        txtRisultato = view.findViewById(R.id.txtRisultato)
        poseOverlayView = view.findViewById(R.id.poseOverlayView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val ctx = requireContext()
        val listenerRef = this

        // Il caricamento del modello AI (file .task) è un'operazione pesante (I/O).
        // Viene delegata a una Coroutine su un thread separato (Dispatchers.IO)
        // per mantenere la reattività dell'interfaccia utente durante l'avvio.
        lifecycleScope.launch(Dispatchers.IO) {
            mediaPipeHelper = MediaPipeHelper(context = ctx, listener = listenerRef)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (allPermissionsGranted()) {
                    avviaFotocamera()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    /**
     * Configurazione del ciclo di vita e dei flussi (Use Cases) della fotocamera tramite CameraX.
     */
    private fun avviaFotocamera() {
        if (!isAdded) return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            if (!isAdded) return@addListener
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider
            }

            // Selezione esplicita della risoluzione VGA (480x640).
            // È il compromesso ottimale per l'Edge AI: riduce il carico sulla GPU/CPU
            // garantendo comunque dettagli sufficienti per il tracciamento dei landmark corporei.
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()

            // Configurazione cruciale per l'elaborazione Real-Time:
            // STRATEGY_KEEP_ONLY_LATEST scarta i fotogrammi in coda se il modello AI è ancora
            // occupato a processare il frame precedente (Backpressure handling).
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processaFotogramma(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Pre-processamento del buffer video hardware prima dell'iniezione nella rete neurale.
     */
    private fun processaFotogramma(imageProxy: ImageProxy) {
        // Drop del frame se l'inferenza precedente non è terminata (sicurezza aggiuntiva)
        if (isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true

        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Adattamento dinamico della matrice in base all'orientamento del dispositivo.
        // Necessario poiché atleti e allenatori ruotano spesso il tablet durante l'uso.
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            frameWidth = bitmap.height
            frameHeight = bitmap.width
        } else {
            frameWidth = bitmap.width
            frameHeight = bitmap.height
        }

        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()

        // Passaggio dell'immagine formattata al wrapper di MediaPipe per l'inferenza vettoriale
        mediaPipeHelper?.detectLiveStream(mpImage, rotationDegrees)

        // Rilascio immediato del buffer hardware per permettere l'acquisizione del frame successivo
        imageProxy.close()
    }

    /**
     * Callback di ritorno (Asincrona). Viene scatenata da MediaPipe quando l'inferenza è completata.
     * @param result Contiene il tensore 3D con le coordinate dei 33 punti articolari (landmarks).
     */
    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        if (!isAdded) return

        // I risultati arrivano dal thread in background. Il rendering grafico e l'update
        // delle TextView richiedono un Context Switch obbligatorio sul Main Thread.
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread

            if (result.landmarks().isNotEmpty()) {
                // I vettori articolari sono stati estratti con successo.
                // Da questo punto in poi, i dati vengono passati alle Macchine a Stati
                // (descritte nel Capitolo 5 della tesi) per la validazione matematica del gesto.
                txtRisultato.text = "Atleta inquadrata • Analisi attiva"
            } else {
                txtRisultato.text = "Inquadra l'atleta nel campo visivo..."
            }

            // Invio del tensore spaziale all'HUD overlay per il disegno dinamico dello scheletro
            poseOverlayView.setResults(result, frameWidth, frameHeight)

            // Rilascio del lock: il sistema è pronto ad accettare il fotogramma successivo
            isProcessing = false
        }
    }

    override fun onError(error: String) {
        if (!isAdded) return
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            isProcessing = false
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevenzione attiva dei memory leak: pulizia del modello C++ sottostante e chiusura dei thread
        mediaPipeHelper?.clearPoseLandmarker()
        cameraExecutor.shutdown()
    }
}
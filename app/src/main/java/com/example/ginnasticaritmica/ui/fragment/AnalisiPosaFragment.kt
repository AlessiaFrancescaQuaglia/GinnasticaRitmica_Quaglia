package com.example.ginnasticaritmica.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.ginnasticaritmica.R
import com.example.ginnasticaritmica.vision.MediaPipeHelper
import com.example.ginnasticaritmica.vision.PoseOverlayView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

class AnalisiPosaFragment : Fragment(), MediaPipeHelper.LandmarkerListener {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var txtRisultato: TextView
    private lateinit var poseOverlayView: com.example.ginnasticaritmica.vision.PoseOverlayView

    private var mediaPipeHelper: MediaPipeHelper? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                avviaFotocamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permesso fotocamera necessario per l'analisi!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analisi_posa, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewFinder = view.findViewById(R.id.viewFinder)
        txtRisultato = view.findViewById(R.id.txtRisultato)
        poseOverlayView = view.findViewById(R.id.poseOverlayView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Inizializziamo il nostro helper di MediaPipe
        mediaPipeHelper = MediaPipeHelper(
            context = requireContext(),
            listener = this
        )

        if (allPermissionsGranted()) {
            avviaFotocamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun avviaFotocamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Anteprima visiva sullo schermo
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Analisi dei fotogrammi in tempo reale per l'IA
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processaFotogramma(imageProxy)
                    }
                }

            // Usiamo la fotocamera frontale o posteriore (qui mettiamo la posteriore, puoi cambiarla in FRONT)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                // Gestione errore
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private var frameWidth = 1
    private var frameHeight = 1

    private fun processaFotogramma(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = android.graphics.Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        //Saved dimensions for the overlay
        frameWidth = rotatedBitmap.width
        frameHeight = rotatedBitmap.height

        val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(rotatedBitmap).build()
        mediaPipeHelper?.detectLiveStream(mpImage)
        imageProxy.close()
    }

    override fun onResults(result: PoseLandmarkerResult, input: MPImage) {
        activity?.runOnUiThread {
            if (result.landmarks().isNotEmpty()) {
                txtRisultato.text = "Atleta rilevata e tracciata!"
            } else {
                txtRisultato.text = "Inquadra l'atleta nel campo visivo..."
            }
            // Passiamo i risultati insieme alle dimensioni reali del fotogramma ruotato
            poseOverlayView.setResults(result, frameWidth, frameHeight)
        }
    }
    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPipeHelper?.clearPoseLandmarker()
        cameraExecutor.shutdown()
    }
}
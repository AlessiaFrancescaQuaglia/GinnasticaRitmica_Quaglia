package com.example.ginnasticaritmica

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

// 3. IMAGE PROCESSING UTILS: Separa la logica di compressione e codifica dal layer visivo.
object PhotoManager {

    //Prende un'immagine Bitmap, la comprime e la trasforma in una stringa Base64
    fun encodeBitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val data = baos.toByteArray()
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }
}
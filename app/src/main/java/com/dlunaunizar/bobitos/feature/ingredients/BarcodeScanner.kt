package com.dlunaunizar.bobitos.feature.ingredients

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Abre el escáner de códigos de Google (ML Kit vía Play Services): pantalla completa, **sin permiso de
 * cámara** y sin incluir el modelo en el APK. Devuelve el código leído, o null si el usuario cancela o
 * el escáner no está disponible.
 */
// Cancelar el escáner o no tener Play Services lanza excepciones de ML Kit que aquí significan
// simplemente «no hay código»; se resuelven a null (salvo la cancelación de la corrutina).
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun scanBarcode(context: Context): String? {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
    return try {
        GmsBarcodeScanning.getClient(context, options).startScan().await().rawValue
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
}

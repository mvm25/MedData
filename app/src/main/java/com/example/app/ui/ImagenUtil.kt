package com.example.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

// ======================================================
// UTILIDADES DE IMAGEN
//
// El dictamen médico se guarda en Realtime Database como
// texto Base64. Una foto de celular pesa varios MB, así que
// antes de guardarla se reduce (lado mayor 1280 px) y se
// comprime como JPEG: queda en unos 150-400 KB y todavía se
// lee bien el texto del documento.
// ======================================================

object ImagenUtil {

    private const val LADO_MAXIMO = 1280
    private const val CALIDAD_JPEG = 70

    /**
     * Lee la imagen elegida por el usuario, la reduce y la devuelve como
     * texto Base64 (JPEG). Devuelve null si no se pudo leer.
     * Es trabajo pesado: llamarla fuera del hilo principal.
     */
    fun uriABase64(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver

            // 1) Medir la imagen sin cargarla completa en memoria.
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null

            // 2) Cargarla ya reducida (potencias de 2) para no gastar memoria de más.
            var muestreo = 1
            while (medidas.outWidth / muestreo > LADO_MAXIMO * 2 ||
                medidas.outHeight / muestreo > LADO_MAXIMO * 2
            ) {
                muestreo *= 2
            }
            val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
            var bitmap: Bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            // 3) Las fotos del celular suelen venir "acostadas": corregir con la orientación EXIF.
            val orientacion = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val grados = when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados != 0f) {
                val matriz = Matrix().apply { postRotate(grados) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
            }

            // 4) Dejar el lado mayor en LADO_MAXIMO como máximo.
            val ladoMayor = maxOf(bitmap.width, bitmap.height)
            if (ladoMayor > LADO_MAXIMO) {
                val factor = LADO_MAXIMO.toFloat() / ladoMayor
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1),
                    true
                )
            }

            // 5) JPEG no tiene transparencia: poner fondo blanco para que un PNG no quede negro.
            val sobreBlanco = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val lienzo = Canvas(sobreBlanco)
            lienzo.drawColor(Color.WHITE)
            lienzo.drawBitmap(bitmap, 0f, 0f, null)

            // 6) Comprimir y pasar a texto.
            val salida = ByteArrayOutputStream()
            sobreBlanco.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Convierte el texto Base64 guardado en la base de datos otra vez en imagen. */
    fun base64ABitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}

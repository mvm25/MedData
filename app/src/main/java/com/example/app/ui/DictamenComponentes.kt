package com.example.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.AzulOscuro
import com.example.app.Gris
import com.example.app.TextoOscuro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================
// DICTAMEN MÉDICO (imagen adjunta)
//
//  - SelectorDictamen: lo usa el PADRE para adjuntar la
//    imagen. Si es obligatorio y no hay imagen, avisa; el
//    formulario que lo contiene debe dejar el botón de
//    enviar deshabilitado mientras no haya imagen.
//  - VisorDictamen: lo usa el ENCARGADO DE SALUD para ver
//    la imagen al revisar una solicitud (toca para ampliar
//    y hacer zoom con dos dedos).
// ======================================================

@Composable
fun SelectorDictamen(
    dictamenBase64: String?,
    onDictamenElegido: (String) -> Unit,
    obligatorio: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var procesando by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }

    val bitmap = remember(dictamenBase64) {
        dictamenBase64?.let { ImagenUtil.base64ABitmap(it) }
    }

    // GetContent abre la galería/archivos del celular. No necesita permisos.
    val selectorDeImagen = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            procesando = true
            mensajeError = null
            scope.launch {
                // Reducir y comprimir la foto es pesado: se hace fuera del hilo principal.
                val resultado = withContext(Dispatchers.IO) { ImagenUtil.uriABase64(context, uri) }
                procesando = false
                if (resultado == null) {
                    mensajeError = "No se pudo leer esa imagen. Prueba con otra."
                } else {
                    onDictamenElegido(resultado)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = if (obligatorio) "Dictamen médico (obligatorio) *" else "Dictamen médico (opcional)",
            fontWeight = FontWeight.Bold,
            color = TextoOscuro,
            fontSize = 14.sp
        )
        Text(
            text = "Foto o imagen del dictamen emitido por el médico.",
            color = Gris,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(8.dp))

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Dictamen médico adjunto",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { selectorDeImagen.launch("image/*") },
            enabled = !procesando,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (procesando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AzulOscuro
                )
                Spacer(Modifier.width(8.dp))
                Text("Procesando imagen...", color = AzulOscuro)
            } else {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = AzulOscuro)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (bitmap == null) "Adjuntar imagen" else "Cambiar imagen",
                    color = AzulOscuro
                )
            }
        }

        val error = mensajeError
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (obligatorio && dictamenBase64.isNullOrBlank() && !procesando) {
            Text(
                text = "Para poder enviar debes adjuntar la imagen del dictamen.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Muestra el dictamen de una solicitud al encargado de salud.
 * [tieneDictamen] viene de la solicitud; [dictamenBase64] es la imagen ya
 * descargada (o null mientras carga / si falló).
 */
@Composable
fun VisorDictamen(
    tieneDictamen: Boolean,
    dictamenBase64: String?,
    cargando: Boolean
) {
    var ampliada by remember { mutableStateOf(false) }

    val bitmap = remember(dictamenBase64) {
        dictamenBase64?.let { ImagenUtil.base64ABitmap(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Text(
            text = "Dictamen médico",
            fontWeight = FontWeight.Bold,
            color = TextoOscuro,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))

        when {
            !tieneDictamen -> {
                Text(
                    text = "Esta solicitud no trae dictamen adjunto (se envió antes de que fuera obligatorio).",
                    color = Gris,
                    fontSize = 12.sp
                )
            }
            cargando -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = AzulOscuro
                )
            }
            bitmap == null -> {
                Text(
                    text = "No se pudo cargar la imagen del dictamen.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            else -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Dictamen médico. Toca para ampliar.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { ampliada = true }
                )
                Text(
                    text = "Toca la imagen para verla completa y hacer zoom.",
                    color = Gris,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (ampliada && bitmap != null) {
        DialogoImagenCompleta(bitmap = bitmap, onCerrar = { ampliada = false })
    }
}

@Composable
private fun DialogoImagenCompleta(bitmap: Bitmap, onCerrar: () -> Unit) {
    var escala by remember { mutableStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Dictamen médico ampliado",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            escala = (escala * zoom).coerceIn(1f, 5f)
                            desplazamiento = if (escala == 1f) Offset.Zero else desplazamiento + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = escala,
                        scaleY = escala,
                        translationX = desplazamiento.x,
                        translationY = desplazamiento.y
                    )
            )

            IconButton(
                onClick = onCerrar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            Text(
                text = "Pellizca con dos dedos para acercar",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }
}

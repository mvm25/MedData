package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Beige
import com.example.app.Rosado
import com.example.app.TextoOscuro
import com.example.app.model.CampoFormulario
import com.example.app.model.EstadoSolicitud

/**
 * Formulario genérico: recibe una lista de CampoFormulario y dibuja
 * un OutlinedTextField por cada uno. Se usa en tres lugares distintos
 * (padre enviando una solicitud, encargado creando/editando un
 * registro real, encargado corrigiendo los datos de una solicitud
 * antes de aceptarla), así que solo existe una sola versión de este
 * formulario en todo el proyecto.
 */
@Composable
fun FormularioCampos(
    titulo: String,
    campos: List<CampoFormulario>,
    valoresIniciales: Map<String, String> = emptyMap(),
    textoBotonPrincipal: String = "Guardar",
    onCancelar: () -> Unit,
    onGuardar: (Map<String, String>) -> Unit,
    contenidoExtra: (@Composable () -> Unit)? = null
) {
    val valores = remember(campos) {
        val mapa = mutableStateMapOf<String, String>()
        campos.forEach { mapa[it.clave] = valoresIniciales[it.clave].orEmpty() }
        mapa
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = TextoOscuro) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                contenidoExtra?.invoke()

                campos.forEach { campo ->
                    OutlinedTextField(
                        value = valores[campo.clave] ?: "",
                        onValueChange = { valores[campo.clave] = it },
                        label = { Text(campo.etiqueta) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(valores.toMap()) }) {
                Text(textoBotonPrincipal, color = AzulOscuro, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    )
}

/** Chip de color que muestra el estado de una Solicitud. */
@Composable
fun EtiquetaEstadoSolicitud(estado: String) {
    val (texto, color) = when (estado) {
        EstadoSolicitud.PENDIENTE -> "Pendiente" to Beige
        EstadoSolicitud.ACEPTADA -> "Aceptada" to Color(0xFF8FBF9F)
        EstadoSolicitud.RECHAZADA -> "Rechazada" to Rosado
        EstadoSolicitud.DEVUELTA -> "Devuelta: corrige un dato" to Rosado
        else -> estado to Beige
    }

    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(texto, fontSize = 12.sp, color = TextoOscuro, fontWeight = FontWeight.Medium)
    }
}

/** Diálogo simple para pedir un texto corto (motivo de rechazo / devolución). */
@Composable
fun DialogoTexto(
    titulo: String,
    etiqueta: String,
    textoBoton: String,
    onCancelar: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = TextoOscuro) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text(etiqueta) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(texto) }) {
                Text(textoBoton, color = AzulOscuro, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}

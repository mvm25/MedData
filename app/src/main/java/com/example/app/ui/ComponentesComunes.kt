package com.example.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Beige
import com.example.app.Gris
import com.example.app.Rosado
import com.example.app.TextoOscuro
import com.example.app.model.CampoFormulario
import com.example.app.model.EstadoSolicitud
import java.util.Calendar
import java.util.Locale

/**
 * Formulario genérico avanzado: soporta:
 * - Selector de fecha (DatePickerDialog) para cualquier campo de fecha.
 * - Cálculo automático de edad a partir de fecha de nacimiento.
 * - Selector de secciones de secundaria de 7-A hasta 12-F.
 * - Menús desplegables (Dropdowns) para sexo, tipo, estado y opciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioCampos(
    titulo: String,
    campos: List<CampoFormulario>,
    valoresIniciales: Map<String, String> = emptyMap(),
    textoBotonPrincipal: String = "Guardar",
    onCancelar: () -> Unit,
    onGuardar: (Map<String, String>) -> Unit,
    contenidoExtra: (@Composable () -> Unit)? = null,
    contenidoFinal: (@Composable () -> Unit)? = null,
    botonGuardarHabilitado: Boolean = true
) {
    val valores = remember(campos) {
        val mapa = mutableStateMapOf<String, String>()
        campos.forEach { mapa[it.clave] = valoresIniciales[it.clave].orEmpty() }
        mapa
    }

    val context = LocalContext.current

    val listaSecciones = remember {
        val lista = mutableListOf<String>()
        for (grado in 7..12) {
            for (letra in 'A'..'F') {
                lista.add("$grado-$letra")
            }
        }
        lista
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
                    val esFecha = campo.clave.contains("fecha", ignoreCase = true) || campo.clave == "proximaDosis"
                    val esSeccion = campo.clave == "seccion"
                    val esEdad = campo.clave == "edad"
                    val opcionesEfectivas = if (campo.clave == "sexo" || campo.clave == "genero") {
                        if (campo.opciones.isNotEmpty()) campo.opciones else listOf("Hombre", "Mujer")
                    } else {
                        campo.opciones
                    }
                    val tieneOpciones = opcionesEfectivas.isNotEmpty()

                    when {
                        tieneOpciones -> {
                            var menuAbierto by remember { mutableStateOf(false) }
                            val permiteEscribir = campo.clave == "carne"

                            ExposedDropdownMenuBox(
                                expanded = menuAbierto,
                                onExpandedChange = { menuAbierto = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = valores[campo.clave] ?: "",
                                    onValueChange = { valores[campo.clave] = it },
                                    readOnly = !permiteEscribir,
                                    label = { Text(campo.etiqueta) },
                                    placeholder = if (campo.placeholder.isNotBlank()) {
                                        { Text(campo.placeholder, color = Gris) }
                                    } else null,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAbierto) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    singleLine = true
                                )

                                ExposedDropdownMenu(
                                    expanded = menuAbierto,
                                    onDismissRequest = { menuAbierto = false }
                                ) {
                                    opcionesEfectivas.forEach { opcion ->
                                        DropdownMenuItem(
                                            text = { Text(opcion) },
                                            onClick = {
                                                valores[campo.clave] = opcion
                                                menuAbierto = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        esSeccion -> {
                            var menuSeccionAbierto by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = menuSeccionAbierto,
                                onExpandedChange = { menuSeccionAbierto = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = valores[campo.clave] ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(campo.etiqueta) },
                                    placeholder = { Text("Selecciona sección (7-A a 12-F)", color = Gris) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuSeccionAbierto) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    singleLine = true
                                )

                                ExposedDropdownMenu(
                                    expanded = menuSeccionAbierto,
                                    onDismissRequest = { menuSeccionAbierto = false }
                                ) {
                                    listaSecciones.forEach { sec ->
                                        DropdownMenuItem(
                                            text = { Text(sec) },
                                            onClick = {
                                                valores[campo.clave] = sec
                                                menuSeccionAbierto = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        esFecha -> {
                            val calendario = Calendar.getInstance()
                            val datePickerDialog = DatePickerDialog(
                                context,
                                { _, anio, mes, dia ->
                                    val fechaFormateada = String.format(Locale.getDefault(), "%02d/%02d/%04d", dia, mes + 1, anio)
                                    valores[campo.clave] = fechaFormateada

                                    if (campo.clave == "fechaNacimiento") {
                                        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
                                        val edadCalculada = (anioActual - anio).coerceAtLeast(0)
                                        valores["edad"] = edadCalculada.toString()

                                        val gradoSugerido = when {
                                            edadCalculada <= 13 -> 7
                                            edadCalculada == 14 -> 8
                                            edadCalculada == 15 -> 9
                                            edadCalculada == 16 -> 10
                                            edadCalculada == 17 -> 11
                                            else -> 12
                                        }
                                        if (valores["seccion"].isNullOrBlank()) {
                                            valores["seccion"] = "$gradoSugerido-A"
                                        }
                                    }
                                },
                                calendario.get(Calendar.YEAR),
                                calendario.get(Calendar.MONTH),
                                calendario.get(Calendar.DAY_OF_MONTH)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { datePickerDialog.show() }
                            ) {
                                OutlinedTextField(
                                    value = valores[campo.clave] ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = TextoOscuro,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    label = { Text(campo.etiqueta) },
                                    placeholder = { Text(if (campo.placeholder.isNotBlank()) campo.placeholder else "DD/MM/AAAA", color = Gris) },
                                    trailingIcon = {
                                        IconButton(onClick = { datePickerDialog.show() }) {
                                            Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha", tint = AzulOscuro)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    singleLine = true
                                )
                            }
                        }

                        esEdad -> {
                            OutlinedTextField(
                                value = valores[campo.clave] ?: "",
                                onValueChange = { valores[campo.clave] = it },
                                readOnly = true,
                                label = { Text(campo.etiqueta) },
                                placeholder = { Text("Se calcula con la fecha de nacimiento", color = Gris) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                singleLine = true
                            )
                        }

                        else -> {
                            OutlinedTextField(
                                value = valores[campo.clave] ?: "",
                                onValueChange = { valores[campo.clave] = it },
                                label = { Text(campo.etiqueta) },
                                placeholder = if (campo.placeholder.isNotBlank()) {
                                    { Text(campo.placeholder, color = Gris) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                singleLine = true
                            )
                        }
                    }
                }

                contenidoFinal?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGuardar(valores.toMap()) },
                enabled = botonGuardarHabilitado
            ) {
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

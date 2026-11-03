package com.example.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Azul
import com.example.app.Fondo
import com.example.app.Gris
import com.example.app.Rosado
import com.example.app.TextoOscuro
import com.example.app.data.LocalRepo
import com.example.app.model.*

// ======================================================
// PANTALLA DEL PADRE DE FAMILIA
//
// Tres pestañas:
//  0. Mis hijos: solo lectura, ve los datos de sus hijos.
//  1. Enviar información: llena un formulario para pedir que
//     se agregue o corrija algo (queda como Solicitud
//     pendiente, no se guarda directo).
//  2. Mis solicitudes: ve el estado de lo que ha enviado. Si
//     el Encargado de Salud la devolvió, puede corregir y
//     volver a enviarla.
// ======================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadreScreen(
    usuarioActual: Usuario,
    pestanaInicial: Int = 0,
    onVolver: () -> Unit
) {
    var pestana by remember { mutableIntStateOf(pestanaInicial) }
    var hijos by remember { mutableStateOf(listOf<Estudiante>()) }
    var solicitudes by remember { mutableStateOf(listOf<Solicitud>()) }
    var mostrarFormularioNuevaSolicitud by remember { mutableStateOf(false) }
    var solicitudAReenviar by remember { mutableStateOf<Solicitud?>(null) }

    fun recargar() {
        LocalRepo.observarEstudiantesDe(usuarioActual.carnesHijos) { hijos = it }
        LocalRepo.observarSolicitudesDePadre(usuarioActual.idUsuario) { solicitudes = it }
    }

    LaunchedEffect(Unit) { recargar() }

    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = { Text("Mi familia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextoOscuro,
                    navigationIconContentColor = AzulOscuro
                )
            )
        },
        floatingActionButton = {
            if (pestana == 1) {
                FloatingActionButton(
                    onClick = { mostrarFormularioNuevaSolicitud = true },
                    containerColor = Azul
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar información", tint = Color.White)
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pestana,
                containerColor = Color.White,
                contentColor = AzulOscuro
            ) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Mis hijos") })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Enviar información") })
                Tab(selected = pestana == 2, onClick = { pestana = 2 }, text = { Text("Mis solicitudes") })
            }

            when (pestana) {
                0 -> ListaHijos(hijos)
                1 -> PestañaEnviarInformacion(hijos)
                2 -> ListaSolicitudesDelPadre(
                    solicitudes = solicitudes,
                    onCorregir = { solicitudAReenviar = it }
                )
            }
        }
    }

    if (mostrarFormularioNuevaSolicitud) {
        DialogoNuevaSolicitud(
            hijos = hijos,
            usuarioActual = usuarioActual,
            onCancelar = { mostrarFormularioNuevaSolicitud = false },
            onEnviada = {
                mostrarFormularioNuevaSolicitud = false
                recargar()
            }
        )
    }

    solicitudAReenviar?.let { solicitud ->
        // Si el encargado devolvió la solicitud porque el dictamen no se leía,
        // aquí se puede mandar uno nuevo. Si no se toca, se queda el anterior.
        var nuevoDictamen by remember(solicitud.idSolicitud) { mutableStateOf<String?>(null) }

        FormularioCampos(
            titulo = "Corregir y reenviar",
            campos = CatalogoCampos.camposPara(solicitud.tipo),
            valoresIniciales = solicitud.campos,
            textoBotonPrincipal = "Reenviar",
            onCancelar = { solicitudAReenviar = null },
            onGuardar = { campos ->
                LocalRepo.reenviarSolicitud(solicitud.idSolicitud, campos, nuevoDictamen) {
                    solicitudAReenviar = null
                    recargar()
                }
            },
            contenidoFinal = {
                SelectorDictamen(
                    dictamenBase64 = nuevoDictamen,
                    onDictamenElegido = { nuevoDictamen = it },
                    obligatorio = false
                )
            },
            contenidoExtra = {
                if (solicitud.comentarioEncargado.isNotBlank()) {
                    Text(
                        "El encargado de salud pidió corregir: ${solicitud.comentarioEncargado}",
                        color = Rosado,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun ListaHijos(hijos: List<Estudiante>) {
    if (hijos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay estudiantes asociados a esta cuenta todavía.", color = Gris)
        }
        return
    }

    LazyColumn(Modifier.padding(16.dp)) {
        items(hijos) { hijo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AzulOscuro)
                        Spacer(Modifier.width(8.dp))
                        Text(hijo.nombreCompleto, fontWeight = FontWeight.Bold, color = TextoOscuro, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Carné: ${hijo.carne}  ·  Sección: ${hijo.seccion}", color = Gris, fontSize = 13.sp)
                    Text("Sexo: ${hijo.sexo}  ·  Edad: ${hijo.edad}", color = Gris, fontSize = 13.sp)
                    Text("Correo: ${hijo.correo}", color = Gris, fontSize = 13.sp)
                    Text("Teléfono: ${hijo.telefono}", color = Gris, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PestañaEnviarInformacion(hijos: List<Estudiante>) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            if (hijos.isEmpty())
                "No hay ningún estudiante asociado a esta cuenta. Contacta al colegio para que te vinculen con tu hijo/a."
            else
                "Usa el botón de abajo para enviar información nueva o corregir un dato de tu hijo/a.\nUn encargado de salud la va a revisar antes de que quede guardada.",
            color = Gris,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ListaSolicitudesDelPadre(
    solicitudes: List<Solicitud>,
    onCorregir: (Solicitud) -> Unit
) {
    if (solicitudes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Todavía no has enviado ninguna solicitud.", color = Gris)
        }
        return
    }

    LazyColumn(Modifier.padding(16.dp)) {
        items(solicitudes) { solicitud ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            CatalogoCampos.etiquetaTipo(solicitud.tipo),
                            fontWeight = FontWeight.Bold,
                            color = TextoOscuro,
                            fontSize = 15.sp
                        )
                        EtiquetaEstadoSolicitud(solicitud.estado)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Carné: ${solicitud.carneEstudiante}  ·  Enviada: ${solicitud.fechaEnvio}", color = Gris, fontSize = 13.sp)

                    if (solicitud.comentarioEncargado.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Comentario del encargado: ${solicitud.comentarioEncargado}", color = Rosado, fontSize = 13.sp)
                    }

                    if (solicitud.estado == EstadoSolicitud.DEVUELTA) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onCorregir(solicitud) },
                            colors = ButtonDefaults.buttonColors(containerColor = Azul)
                        ) {
                            Text("Corregir y reenviar", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoNuevaSolicitud(
    hijos: List<Estudiante>,
    usuarioActual: Usuario,
    onCancelar: () -> Unit,
    onEnviada: () -> Unit
) {
    var carneElegido by remember { mutableStateOf(hijos.firstOrNull()?.carne.orEmpty()) }
    var tipoElegido by remember { mutableStateOf(TipoSolicitud.PADECIMIENTO) }
    var menuHijoAbierto by remember { mutableStateOf(false) }
    var menuTipoAbierto by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Imagen del dictamen médico (Base64). Mientras sea null no se puede enviar.
    var dictamen by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    FormularioCampos(
        titulo = "Enviar información",
        campos = CatalogoCampos.camposPara(tipoElegido),
        textoBotonPrincipal = if (enviando) "Enviando..." else "Enviar",
        onCancelar = onCancelar,
        botonGuardarHabilitado = dictamen != null && carneElegido.isNotBlank() && !enviando,
        onGuardar = { campos ->
            val imagen = dictamen
            // Doble seguro: aunque el botón ya está deshabilitado, aquí tampoco
            // se envía nada si falta la imagen.
            if (imagen != null && carneElegido.isNotBlank() && !enviando) {
                enviando = true
                LocalRepo.crearSolicitud(
                    Solicitud(
                        carneEstudiante = carneElegido,
                        idPadre = usuarioActual.idUsuario,
                        nombrePadre = usuarioActual.nombre,
                        tipo = tipoElegido,
                        campos = campos
                    ),
                    imagen
                ) { exito ->
                    enviando = false
                    if (exito) {
                        onEnviada()
                    } else {
                        Toast.makeText(
                            context,
                            "No se pudo enviar la solicitud. Revisa tu conexión e inténtalo de nuevo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        },
        contenidoFinal = {
            SelectorDictamen(
                dictamenBase64 = dictamen,
                onDictamenElegido = { dictamen = it },
                obligatorio = true
            )
        },
        contenidoExtra = {
            if (hijos.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = menuHijoAbierto,
                    onExpandedChange = { menuHijoAbierto = it }
                ) {
                    OutlinedTextField(
                        value = hijos.find { it.carne == carneElegido }?.nombreCompleto ?: "Selecciona un hijo/a",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estudiante") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuHijoAbierto) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = menuHijoAbierto, onDismissRequest = { menuHijoAbierto = false }) {
                        hijos.forEach { hijo ->
                            DropdownMenuItem(
                                text = { Text(hijo.nombreCompleto) },
                                onClick = {
                                    carneElegido = hijo.carne
                                    menuHijoAbierto = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            ExposedDropdownMenuBox(
                expanded = menuTipoAbierto,
                onExpandedChange = { menuTipoAbierto = it }
            ) {
                OutlinedTextField(
                    value = CatalogoCampos.etiquetaTipo(tipoElegido),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("¿Qué quieres enviar?") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuTipoAbierto) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = menuTipoAbierto, onDismissRequest = { menuTipoAbierto = false }) {
                    CatalogoCampos.todosLosTipos.forEach { tipo ->
                        DropdownMenuItem(
                            text = { Text(CatalogoCampos.etiquetaTipo(tipo)) },
                            onClick = {
                                tipoElegido = tipo
                                menuTipoAbierto = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    )
}

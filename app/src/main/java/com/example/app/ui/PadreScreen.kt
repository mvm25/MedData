package com.example.app.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Azul
import com.example.app.Beige
import com.example.app.Fondo
import com.example.app.Gris
import com.example.app.R
import com.example.app.Rosado
import com.example.app.TextoOscuro
import com.example.app.data.LocalRepo
import com.example.app.model.*

// ======================================================
// PANTALLA DEL PADRE DE FAMILIA
//
// Tres pestañas:
//  0. Mis hijos: solo lectura, ve los datos de sus hijos.
//  1. Enviar información: tarjetas con los 6 formularios.
//     Dictamen obligatorio solo en padecimientos, antecedentes
//     y vacunas; opcional en el resto.
//  2. Mis solicitudes: ve el estado de lo enviado y puede
//     corregir devueltas.
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
    var tipoSolicitudApertura by remember { mutableStateOf<String?>(null) }
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
                actions = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_icon),
                        contentDescription = "Logo MedData",
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextoOscuro,
                    navigationIconContentColor = AzulOscuro
                )
            )
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
                1 -> PestañaEnviarInformacion(
                    hijos = hijos,
                    onSeleccionarTipo = { tipoSolicitudApertura = it }
                )
                2 -> ListaSolicitudesDelPadre(
                    solicitudes = solicitudes,
                    onCorregir = { solicitudAReenviar = it }
                )
            }
        }
    }

    tipoSolicitudApertura?.let { tipo ->
        DialogoNuevaSolicitud(
            tipoInicial = tipo,
            hijos = hijos,
            usuarioActual = usuarioActual,
            onCancelar = { tipoSolicitudApertura = null },
            onEnviada = {
                tipoSolicitudApertura = null
                recargar()
            }
        )
    }

    solicitudAReenviar?.let { solicitud ->
        var nuevoDictamen by remember(solicitud.idSolicitud) { mutableStateOf<String?>(null) }
        val requiereDictamen = solicitud.tipo in listOf(
            TipoSolicitud.PADECIMIENTO,
            TipoSolicitud.ANTECEDENTE,
            TipoSolicitud.VACUNA
        )

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
                    obligatorio = requiereDictamen && !solicitud.tieneDictamen
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
private fun PestañaEnviarInformacion(
    hijos: List<Estudiante>,
    onSeleccionarTipo: (String) -> Unit
) {
    if (hijos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No hay ningún estudiante asociado a esta cuenta todavía. Contacta al colegio para que te vinculen con tu hijo/a.",
                color = Gris,
                fontSize = 14.sp
            )
        }
        return
    }

    val opcionesFormularios = listOf(
        Triple(TipoSolicitud.PADECIMIENTO, "Padecimiento", "Informa sobre padecimientos, alergias o condiciones médicas."),
        Triple(TipoSolicitud.ANTECEDENTE, "Antecedente médico", "Registra cirugías, fracturas, alergias o antecedentes familiares."),
        Triple(TipoSolicitud.VACUNA, "Vacuna", "Envía comprobante de vacunas aplicadas o dosis pendientes."),
        Triple(TipoSolicitud.CITA_MEDICA, "Cita médica", "Informa sobre citas de medicina general, psicología, nutrición, etc."),
        Triple(TipoSolicitud.AUTORIZACION_MEDICAMENTO, "Autorización de medicamento", "Autoriza la administración de medicamentos en el colegio."),
        Triple(TipoSolicitud.DATOS_GENERALES, "Datos generales del estudiante", "Solicita actualizar teléfono, correo, sección o datos del estudiante.")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "¿Qué deseas enviar?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextoOscuro
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Selecciona el formulario que deseas completar:",
                color = Gris,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(opcionesFormularios) { (tipo, titulo, descripcion) ->
            val requiereDictamen = tipo in listOf(TipoSolicitud.PADECIMIENTO, TipoSolicitud.ANTECEDENTE, TipoSolicitud.VACUNA)

            Card(
                onClick = { onSeleccionarTipo(tipo) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(6.dp)
                            .height(50.dp)
                            .background(
                                when (tipo) {
                                    TipoSolicitud.PADECIMIENTO, TipoSolicitud.ANTECEDENTE, TipoSolicitud.VACUNA -> Azul
                                    else -> Beige
                                },
                                RoundedCornerShape(10.dp)
                            )
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titulo,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextoOscuro
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = descripcion,
                            fontSize = 12.sp,
                            color = Gris
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (requiereDictamen) "Dictamen médico obligatorio" else "Dictamen médico opcional",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (requiereDictamen) AzulOscuro else Rosado
                        )
                    }
                }
            }
        }
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
    tipoInicial: String,
    hijos: List<Estudiante>,
    usuarioActual: Usuario,
    onCancelar: () -> Unit,
    onEnviada: () -> Unit
) {
    var carneElegido by remember(hijos, usuarioActual) {
        mutableStateOf(
            hijos.firstOrNull()?.carne
                ?: usuarioActual.carnesHijos.firstOrNull().orEmpty()
        )
    }
    var tipoElegido by remember(tipoInicial) { mutableStateOf(tipoInicial) }
    var menuHijoAbierto by remember { mutableStateOf(false) }
    var menuTipoAbierto by remember { mutableStateOf(false) }

    LaunchedEffect(hijos, usuarioActual) {
        if (carneElegido.isBlank()) {
            carneElegido = hijos.firstOrNull()?.carne
                ?: usuarioActual.carnesHijos.firstOrNull().orEmpty()
        }
    }

    val context = LocalContext.current

    // El dictamen solo es OBLIGATORIO en Padecimiento, Antecedente y Vacuna.
    val requiereDictamenObligatorio = tipoElegido in listOf(
        TipoSolicitud.PADECIMIENTO,
        TipoSolicitud.ANTECEDENTE,
        TipoSolicitud.VACUNA
    )

    var dictamen by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    FormularioCampos(
        titulo = "Enviar ${CatalogoCampos.etiquetaTipo(tipoElegido).lowercase()}",
        campos = CatalogoCampos.camposPara(tipoElegido),
        textoBotonPrincipal = if (enviando) "Enviando..." else "Enviar",
        onCancelar = onCancelar,
        botonGuardarHabilitado = (!requiereDictamenObligatorio || dictamen != null) && carneElegido.isNotBlank() && !enviando,
        onGuardar = { campos ->
            val imagen = dictamen.orEmpty()
            if ((!requiereDictamenObligatorio || imagen.isNotBlank()) && carneElegido.isNotBlank() && !enviando) {
                enviando = true
                LocalRepo.crearSolicitud(
                    Solicitud(
                        carneEstudiante = carneElegido,
                        idPadre = usuarioActual.idUsuario,
                        nombrePadre = usuarioActual.nombre,
                        tipo = tipoElegido,
                        campos = campos,
                        tieneDictamen = imagen.isNotBlank()
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
                obligatorio = requiereDictamenObligatorio
            )
        },
        contenidoExtra = {
            if (hijos.size > 1) {
                ExposedDropdownMenuBox(
                    expanded = menuHijoAbierto,
                    onExpandedChange = { menuHijoAbierto = it }
                ) {
                    OutlinedTextField(
                        value = hijos.find { it.carne == carneElegido }?.nombreCompleto ?: "Carné: $carneElegido",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estudiante") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuHijoAbierto) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = menuHijoAbierto, onDismissRequest = { menuHijoAbierto = false }) {
                        hijos.forEach { hijo ->
                            DropdownMenuItem(
                                text = { Text("${hijo.nombreCompleto} (${hijo.carne})") },
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
                    label = { Text("Formulario seleccionado") },
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

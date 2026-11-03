package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.app.data.Mapeador
import com.example.app.model.*

// ======================================================
// LISTA DE ESTUDIANTES
// Pantalla que ve el Encargado de Salud: lista completa,
// con CRUD (agregar / editar / eliminar estudiante).
// ======================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstudiantesScreen(
    usuarioActual: Usuario,
    onVolver: () -> Unit,
    onSeleccionarEstudiante: (Estudiante) -> Unit
) {
    var cargando by remember { mutableStateOf(true) }
    var estudiantes by remember { mutableStateOf(listOf<Estudiante>()) }
    var busqueda by remember { mutableStateOf("") }
    var mostrarFormularioNuevo by remember { mutableStateOf(false) }

    fun recargar() {
        LocalRepo.observarEstudiantes { lista ->
            estudiantes = lista
            cargando = false
        }
    }

    LaunchedEffect(Unit) { recargar() }

    val resultados = estudiantes.filter {
        it.nombreCompleto.contains(busqueda, ignoreCase = true) ||
            it.carne.contains(busqueda, ignoreCase = true) ||
            it.seccion.contains(busqueda, ignoreCase = true)
    }

    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = { Text("Estudiantes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
            // Solo el Encargado de Salud puede dar de alta estudiantes.
            if (usuarioActual.esEncargadoSalud) {
                FloatingActionButton(
                    onClick = { mostrarFormularioNuevo = true },
                    containerColor = Azul
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar estudiante", tint = Color.White)
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre, carné o sección") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AzulOscuro) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(12.dp))

            when {
                cargando -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AzulOscuro)
                    }
                }
                resultados.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay estudiantes que coincidan con la búsqueda.", color = Gris)
                    }
                }
                else -> {
                    LazyColumn {
                        items(resultados) { estudiante ->
                            TarjetaEstudiante(
                                estudiante = estudiante,
                                onClick = { onSeleccionarEstudiante(estudiante) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (mostrarFormularioNuevo) {
        FormularioCampos(
            titulo = "Nuevo estudiante",
            campos = listOf(
                CampoFormulario("carne", "Carné"),
                CampoFormulario("nombre", "Nombre"),
                CampoFormulario("apellido", "Apellido"),
                CampoFormulario("correo", "Correo"),
                CampoFormulario("telefono", "Teléfono"),
                CampoFormulario("edad", "Edad"),
                CampoFormulario("sexo", "Sexo"),
                CampoFormulario("seccion", "Sección")
            ),
            onCancelar = { mostrarFormularioNuevo = false },
            onGuardar = { campos ->
                val estudiante = Estudiante(
                    carne = campos["carne"].orEmpty(),
                    nombre = campos["nombre"].orEmpty(),
                    apellido = campos["apellido"].orEmpty(),
                    correo = campos["correo"].orEmpty(),
                    telefono = campos["telefono"].orEmpty(),
                    edad = campos["edad"]?.toIntOrNull() ?: 0,
                    sexo = campos["sexo"].orEmpty(),
                    seccion = campos["seccion"].orEmpty()
                )
                if (estudiante.carne.isNotBlank()) {
                    LocalRepo.guardarEstudiante(estudiante) { recargar() }
                }
                mostrarFormularioNuevo = false
            }
        )
    }
}

@Composable
private fun TarjetaEstudiante(estudiante: Estudiante, onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = AzulOscuro,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(estudiante.nombreCompleto, fontWeight = FontWeight.Bold, color = TextoOscuro, fontSize = 16.sp)
                Text("Carné: ${estudiante.carne}  ·  Sección: ${estudiante.seccion}", color = Gris, fontSize = 13.sp)
            }
        }
    }
}

// ======================================================
// DETALLE / FICHA MÉDICA DE UN ESTUDIANTE
// El Encargado de Salud ve botones de agregar / editar /
// eliminar en cada sección. Un padre nunca llega a esta
// pantalla: él usa PadreScreen para ver a su hijo/a.
// ======================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstudianteDetalleScreen(
    usuarioActual: Usuario,
    carne: String,
    onVolver: () -> Unit
) {
    var cargando by remember { mutableStateOf(true) }
    var ficha by remember { mutableStateOf(FichaMedica()) }

    // Qué formulario mostrar en este momento: null = ninguno.
    var formularioAbierto by remember { mutableStateOf<String?>(null) }
    var registroEnEdicion by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var idRegistroEnEdicion by remember { mutableStateOf("") }

    fun recargar() {
        LocalRepo.obtenerFichaMedica(carne) {
            ficha = it
            cargando = false
        }
    }

    LaunchedEffect(carne) { recargar() }

    val puedeEditar = usuarioActual.esEncargadoSalud

    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = { Text(ficha.estudiante.nombreCompleto.ifBlank { "Ficha médica" }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextoOscuro,
                    navigationIconContentColor = AzulOscuro
                )
            )
        }
    ) { padding ->

        if (cargando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AzulOscuro)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                DatosGenerales(
                    estudiante = ficha.estudiante,
                    puedeEditar = puedeEditar,
                    onEditar = {
                        registroEnEdicion = Mapeador.aMapaDatosGenerales(ficha.estudiante)
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.DATOS_GENERALES
                    }
                )
            }

            item {
                SeccionFicha(
                    titulo = "Padecimientos",
                    puedeEditar = puedeEditar,
                    vacio = ficha.padecimientos.isEmpty(),
                    onAgregar = {
                        registroEnEdicion = emptyMap()
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.PADECIMIENTO
                    }
                ) {
                    ficha.padecimientos.forEach { p ->
                        FilaDato(
                            titulo = p.nombre,
                            detalle = "Tipo: ${p.tipo} · Estado: ${p.estado}\nDiagnóstico: ${p.fechaDiagnostico}\nTratamiento: ${p.tratamiento}",
                            puedeEditar = puedeEditar,
                            onEditar = {
                                registroEnEdicion = Mapeador.aMapa(p)
                                idRegistroEnEdicion = p.idPadecimiento
                                formularioAbierto = TipoSolicitud.PADECIMIENTO
                            },
                            onEliminar = {
                                LocalRepo.eliminarPadecimiento(carne, p.idPadecimiento) { recargar() }
                            }
                        )
                    }
                }
            }

            item {
                SeccionFicha(
                    titulo = "Antecedentes",
                    puedeEditar = puedeEditar,
                    vacio = ficha.antecedentes.isEmpty(),
                    onAgregar = {
                        registroEnEdicion = emptyMap()
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.ANTECEDENTE
                    }
                ) {
                    ficha.antecedentes.forEach { a ->
                        FilaDato(
                            titulo = a.tipo,
                            detalle = "${a.descripcion}\nFecha: ${a.fecha}",
                            puedeEditar = puedeEditar,
                            onEditar = {
                                registroEnEdicion = Mapeador.aMapa(a)
                                idRegistroEnEdicion = a.idAntecedente
                                formularioAbierto = TipoSolicitud.ANTECEDENTE
                            },
                            onEliminar = {
                                LocalRepo.eliminarAntecedente(carne, a.idAntecedente) { recargar() }
                            }
                        )
                    }
                }
            }

            item {
                SeccionFicha(
                    titulo = "Vacunas",
                    puedeEditar = puedeEditar,
                    vacio = ficha.vacunas.isEmpty(),
                    onAgregar = {
                        registroEnEdicion = emptyMap()
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.VACUNA
                    }
                ) {
                    ficha.vacunas.forEach { v ->
                        FilaDato(
                            titulo = v.nombre,
                            detalle = "Enfermedad: ${v.enfermedad}\nAplicada: ${v.fechaAplicacion}  ·  Próxima dosis: ${v.proximaDosis}\nEstado: ${v.estado}",
                            puedeEditar = puedeEditar,
                            onEditar = {
                                registroEnEdicion = Mapeador.aMapa(v)
                                idRegistroEnEdicion = v.idVacuna
                                formularioAbierto = TipoSolicitud.VACUNA
                            },
                            onEliminar = {
                                LocalRepo.eliminarVacuna(carne, v.idVacuna) { recargar() }
                            }
                        )
                    }
                }
            }

            item {
                SeccionFicha(
                    titulo = "Citas médicas",
                    puedeEditar = puedeEditar,
                    vacio = ficha.citas.isEmpty(),
                    onAgregar = {
                        registroEnEdicion = emptyMap()
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.CITA_MEDICA
                    }
                ) {
                    ficha.citas.forEach { c ->
                        FilaDato(
                            titulo = "${c.tipo.replaceFirstChar { it.uppercase() }} · ${c.fecha}",
                            detalle = "Motivo: ${c.motivo}\n${c.observaciones}",
                            puedeEditar = puedeEditar,
                            onEditar = {
                                registroEnEdicion = Mapeador.aMapa(c)
                                idRegistroEnEdicion = c.idCita
                                formularioAbierto = TipoSolicitud.CITA_MEDICA
                            },
                            onEliminar = {
                                LocalRepo.eliminarCita(carne, c.idCita) { recargar() }
                            }
                        )
                    }
                }
            }

            item {
                SeccionFicha(
                    titulo = "Autorización de medicamentos",
                    puedeEditar = puedeEditar,
                    vacio = ficha.autorizaciones.isEmpty(),
                    onAgregar = {
                        registroEnEdicion = emptyMap()
                        idRegistroEnEdicion = ""
                        formularioAbierto = TipoSolicitud.AUTORIZACION_MEDICAMENTO
                    }
                ) {
                    ficha.autorizaciones.forEach { au ->
                        FilaDato(
                            titulo = au.medicamento,
                            detalle = "Motivo: ${au.motivo}\nAutorizado: ${if (au.autorizado) "Sí" else "No"}",
                            puedeEditar = puedeEditar,
                            onEditar = {
                                registroEnEdicion = Mapeador.aMapa(au)
                                idRegistroEnEdicion = au.idAutorizacion
                                formularioAbierto = TipoSolicitud.AUTORIZACION_MEDICAMENTO
                            },
                            onEliminar = {
                                LocalRepo.eliminarAutorizacion(carne, au.idAutorizacion) { recargar() }
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    val tipoAbierto = formularioAbierto
    if (tipoAbierto != null) {
        FormularioCampos(
            titulo = CatalogoCampos.etiquetaTipo(tipoAbierto),
            campos = CatalogoCampos.camposPara(tipoAbierto),
            valoresIniciales = registroEnEdicion,
            onCancelar = { formularioAbierto = null },
            onGuardar = { campos ->
                when (tipoAbierto) {
                    TipoSolicitud.PADECIMIENTO ->
                        LocalRepo.guardarPadecimiento(Mapeador.padecimientoDeMapa(carne, idRegistroEnEdicion, campos)) { recargar() }
                    TipoSolicitud.ANTECEDENTE ->
                        LocalRepo.guardarAntecedente(Mapeador.antecedenteDeMapa(carne, idRegistroEnEdicion, campos)) { recargar() }
                    TipoSolicitud.VACUNA ->
                        LocalRepo.guardarVacuna(Mapeador.vacunaDeMapa(carne, idRegistroEnEdicion, campos)) { recargar() }
                    TipoSolicitud.CITA_MEDICA ->
                        LocalRepo.guardarCita(Mapeador.citaDeMapa(carne, idRegistroEnEdicion, campos)) { recargar() }
                    TipoSolicitud.AUTORIZACION_MEDICAMENTO ->
                        LocalRepo.guardarAutorizacion(
                            Mapeador.autorizacionDeMapa(carne, idRegistroEnEdicion, usuarioActual.idUsuario, campos)
                        ) { recargar() }
                    TipoSolicitud.DATOS_GENERALES ->
                        LocalRepo.actualizarDatosGenerales(Mapeador.aplicarDatosGenerales(ficha.estudiante, campos)) { recargar() }
                }
                formularioAbierto = null
            }
        )
    }
}

@Composable
private fun DatosGenerales(
    estudiante: Estudiante,
    puedeEditar: Boolean,
    onEditar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Azul.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Datos generales", fontWeight = FontWeight.Bold, color = TextoOscuro, fontSize = 17.sp)
                if (puedeEditar) {
                    IconButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar datos generales", tint = Rosado)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Carné: ${estudiante.carne}", color = TextoOscuro)
            Text("Sección: ${estudiante.seccion}", color = TextoOscuro)
            Text("Sexo: ${estudiante.sexo}   ·   Edad: ${estudiante.edad}", color = TextoOscuro)
            Text("Correo: ${estudiante.correo}", color = TextoOscuro)
            Text("Teléfono: ${estudiante.telefono}", color = TextoOscuro)
        }
    }
}

@Composable
private fun SeccionFicha(
    titulo: String,
    puedeEditar: Boolean,
    vacio: Boolean,
    onAgregar: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titulo, fontWeight = FontWeight.Bold, color = TextoOscuro, fontSize = 16.sp)

                // Solo el Encargado de Salud ve el botón de agregar.
                if (puedeEditar) {
                    TextButton(onClick = onAgregar) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Rosado)
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar", color = Rosado)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (vacio) {
                Text("Sin registros.", color = Gris, fontSize = 13.sp)
            } else {
                contenido()
            }
        }
    }
}

@Composable
private fun FilaDato(
    titulo: String,
    detalle: String,
    puedeEditar: Boolean,
    onEditar: () -> Unit,
    onEliminar: () -> Unit
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.Medium, color = TextoOscuro, fontSize = 14.sp)
                Text(detalle, color = Gris, fontSize = 13.sp)
            }

            if (puedeEditar) {
                Row {
                    IconButton(onClick = onEditar, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = AzulOscuro)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onEliminar, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Rosado)
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Gris.copy(alpha = 0.3f))
}

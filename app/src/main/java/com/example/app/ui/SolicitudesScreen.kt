package com.example.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Fondo
import com.example.app.Gris
import com.example.app.Rosado
import com.example.app.TextoOscuro
import com.example.app.data.LocalRepo
import com.example.app.model.CatalogoCampos
import com.example.app.model.EstadoSolicitud
import com.example.app.model.Solicitud
import com.example.app.model.Usuario

// ======================================================
// BANDEJA DE SOLICITUDES (solo Encargado de Salud)
//
// Por cada solicitud que manda un padre, el Encargado puede:
//  - Aceptarla (los datos quedan guardados tal cual, o
//    corregidos si el Encargado cambió algo en el formulario
//    antes de tocar "Aceptar")
//  - Rechazarla (con un motivo)
//  - Devolverla al padre (con un comentario de qué corregir)
// ======================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitudesScreen(
    usuarioActual: Usuario,
    onVolver: () -> Unit
) {
    var pestaña by remember { mutableStateOf(0) } // 0 = pendientes, 1 = historial
    var pendientes by remember { mutableStateOf(listOf<Solicitud>()) }
    var historial by remember { mutableStateOf(listOf<Solicitud>()) }
    var solicitudSeleccionada by remember { mutableStateOf<Solicitud?>(null) }

    fun recargar() {
        LocalRepo.observarSolicitudesPendientes { pendientes = it }
        LocalRepo.observarTodasLasSolicitudes { historial = it }
    }

    LaunchedEffect(Unit) { recargar() }

    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = { Text("Solicitudes", fontWeight = FontWeight.Bold) },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = pestaña,
                containerColor = Color.White,
                contentColor = AzulOscuro
            ) {
                Tab(selected = pestaña == 0, onClick = { pestaña = 0 }, text = { Text("Pendientes (${pendientes.size})") })
                Tab(selected = pestaña == 1, onClick = { pestaña = 1 }, text = { Text("Historial") })
            }

            val lista = if (pestaña == 0) pendientes else historial

            if (lista.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (pestaña == 0) "No hay solicitudes pendientes." else "Todavía no se ha resuelto ninguna solicitud.",
                        color = Gris
                    )
                }
            } else {
                LazyColumn(Modifier.padding(16.dp)) {
                    items(lista) { solicitud ->
                        TarjetaSolicitud(
                            solicitud = solicitud,
                            onClick = { if (solicitud.estado == EstadoSolicitud.PENDIENTE) solicitudSeleccionada = solicitud }
                        )
                    }
                }
            }
        }
    }

    solicitudSeleccionada?.let { solicitud ->
        DialogoRevisarSolicitud(
            solicitud = solicitud,
            usuarioActual = usuarioActual,
            onCerrar = { solicitudSeleccionada = null },
            onResuelta = {
                solicitudSeleccionada = null
                recargar()
            }
        )
    }
}

@Composable
private fun TarjetaSolicitud(solicitud: Solicitud, onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
            Text("Carné del estudiante: ${solicitud.carneEstudiante}", color = Gris, fontSize = 13.sp)
            Text("Enviado por: ${solicitud.nombrePadre}  ·  ${solicitud.fechaEnvio}", color = Gris, fontSize = 13.sp)
            if (solicitud.comentarioEncargado.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Comentario: ${solicitud.comentarioEncargado}", color = Rosado, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Diálogo de revisión: muestra los campos que mandó el padre ya
 * cargados en un formulario editable. Si el Encargado cambia algo
 * ahí mismo (por ejemplo corrige una coma o un dato mal escrito) y
 * luego toca "Aceptar", se guarda con esa corrección. También puede
 * "Rechazar" o "Devolver al padre" sin tocar los campos.
 */
@Composable
private fun DialogoRevisarSolicitud(
    solicitud: Solicitud,
    usuarioActual: Usuario,
    onCerrar: () -> Unit,
    onResuelta: () -> Unit
) {
    var mostrarDialogoRechazar by remember { mutableStateOf(false) }
    var mostrarDialogoDevolver by remember { mutableStateOf(false) }

    FormularioCampos(
        titulo = "${CatalogoCampos.etiquetaTipo(solicitud.tipo)} · Carné ${solicitud.carneEstudiante}",
        campos = CatalogoCampos.camposPara(solicitud.tipo),
        valoresIniciales = solicitud.campos,
        textoBotonPrincipal = "Aceptar",
        onCancelar = onCerrar,
        onGuardar = { camposFinales ->
            // "Aceptar" usa los valores que estén en el formulario en
            // ese momento: si el Encargado corrigió algo, se guarda ya
            // corregido.
            LocalRepo.aceptarSolicitud(solicitud.idSolicitud, usuarioActual.idUsuario, camposFinales) {
                onResuelta()
            }
        },
        contenidoExtra = {
            Text(
                "Enviado por ${solicitud.nombrePadre} el ${solicitud.fechaEnvio}. Puedes corregir cualquier campo antes de aceptar.",
                color = Gris,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { mostrarDialogoDevolver = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, tint = AzulOscuro)
                    Spacer(Modifier.width(4.dp))
                    Text("Devolver", color = AzulOscuro)
                }
                OutlinedButton(
                    onClick = { mostrarDialogoRechazar = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Rosado)
                    Spacer(Modifier.width(4.dp))
                    Text("Rechazar", color = Rosado)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    )

    if (mostrarDialogoRechazar) {
        DialogoTexto(
            titulo = "Rechazar solicitud",
            etiqueta = "Motivo del rechazo",
            textoBoton = "Rechazar",
            onCancelar = { mostrarDialogoRechazar = false },
            onConfirmar = { motivo ->
                LocalRepo.rechazarSolicitud(solicitud.idSolicitud, usuarioActual.idUsuario, motivo) {
                    mostrarDialogoRechazar = false
                    onResuelta()
                }
            }
        )
    }

    if (mostrarDialogoDevolver) {
        DialogoTexto(
            titulo = "Devolver al padre",
            etiqueta = "¿Qué dato debe corregir?",
            textoBoton = "Devolver",
            onCancelar = { mostrarDialogoDevolver = false },
            onConfirmar = { comentario ->
                LocalRepo.devolverSolicitud(solicitud.idSolicitud, usuarioActual.idUsuario, comentario) {
                    mostrarDialogoDevolver = false
                    onResuelta()
                }
            }
        )
    }
}

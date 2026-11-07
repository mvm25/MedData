package com.example.app.chatbot

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.Azul
import com.example.app.AzulOscuro
import com.example.app.Gris
import com.example.app.TextoOscuro
import com.example.app.model.Usuario
import kotlinx.coroutines.delay

// ======================================================
// ESTADO DEL CHAT
// ======================================================

class ChatbotEstado(
    private val motor: ChatbotMotor,
    private val usuario: Usuario
) {
    val mensajes = mutableStateListOf<MensajeChat>()
    val sugerencias: List<String> = motor.sugerencias(usuario)

    var abierto by mutableStateOf(false)
    var escribiendo by mutableStateOf(false)
        private set

    private val contexto = ContextoChat()
    private var siguienteId = 0L

    // Sirve para ignorar una respuesta que llegue tarde (después del tiempo de espera).
    private var turno = 0

    init {
        mensajes.add(MensajeChat(siguienteId++, motor.saludoInicial(usuario), esUsuario = false))
    }

    fun enviar(texto: String) {
        val limpio = texto.trim()
        if (limpio.isEmpty() || escribiendo) return

        mensajes.add(MensajeChat(siguienteId++, limpio, esUsuario = true))
        escribiendo = true
        turno++
        val miTurno = turno

        motor.responder(limpio, usuario, contexto) { respuesta ->
            if (miTurno == turno) {
                mensajes.add(MensajeChat(siguienteId++, respuesta, esUsuario = false))
                escribiendo = false
            }
        }
    }

    fun tiempoAgotado() {
        if (!escribiendo) return
        turno++
        mensajes.add(
            MensajeChat(
                siguienteId++,
                "No pude obtener la información a tiempo. Revisa tu conexión e inténtalo de nuevo.",
                esUsuario = false
            )
        )
        escribiendo = false
    }
}

// ======================================================
// CÍRCULO FLOTANTE + PANEL A LA DERECHA
//
// Se coloca encima de cualquier pantalla (ver MainActivity).
// margenInferior sirve para subir el círculo cuando la pantalla
// ya tiene su propio botón flotante abajo a la derecha.
// ======================================================

@Composable
fun ChatbotOverlay(
    usuario: Usuario,
    modifier: Modifier = Modifier,
    margenInferior: Dp = 16.dp,
    datos: ChatbotDatos = ChatbotConfig.datos,
    iniciarAbierto: Boolean = false
) {
    // Se reinicia la conversación si cambia el usuario o su rol.
    val estado = remember(usuario.idUsuario, usuario.rol, datos) {
        ChatbotEstado(ChatbotMotorReglas(datos), usuario).also { it.abierto = iniciarAbierto }
    }

    if (!LocalInspectionMode.current) {
        BackHandler(enabled = estado.abierto) { estado.abierto = false }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Fondo oscuro: al tocarlo se cierra el chat.
        AnimatedVisibility(
            visible = estado.abierto,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { estado.abierto = false }
            )
        }

        // Panel del chat, pegado a la derecha.
        AnimatedVisibility(
            visible = estado.abierto,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            PanelChat(usuario = usuario, estado = estado)
        }

        // Círculo pequeño abajo a la derecha.
        AnimatedVisibility(
            visible = !estado.abierto,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = margenInferior),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(AzulOscuro)
                    .clickable { estado.abierto = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Abrir asistente",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun PanelChat(usuario: Usuario, estado: ChatbotEstado) {

    val ancho = minOf(400, (LocalConfiguration.current.screenWidthDp * 0.88f).toInt()).dp
    var texto by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Baja al último mensaje cada vez que hay uno nuevo.
    LaunchedEffect(estado.mensajes.size, estado.escribiendo) {
        val total = estado.mensajes.size + if (estado.escribiendo) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    // Si Firebase no responde en 15 segundos, se avisa en vez de quedarse "escribiendo".
    LaunchedEffect(estado.escribiendo) {
        if (estado.escribiendo) {
            delay(15_000)
            estado.tiempoAgotado()
        }
    }

    fun enviarTexto() {
        if (texto.isNotBlank()) {
            estado.enviar(texto)
            texto = ""
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(ancho)
            // Consume los toques para que no lleguen al fondo oscuro.
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---------- Encabezado ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AzulOscuro)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Asistente MedData", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Solo consulta · ${etiquetaRol(usuario)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = { estado.abierto = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar asistente", tint = Color.White)
                }
            }

            // ---------- Mensajes ----------
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(estado.mensajes, key = { it.id }) { mensaje ->
                    BurbujaMensaje(mensaje)
                }
                if (estado.escribiendo) {
                    item {
                        Text("Escribiendo…", color = Gris, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // ---------- Sugerencias rápidas ----------
            if (estado.sugerencias.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(estado.sugerencias) { sugerencia ->
                        AssistChip(
                            onClick = { estado.enviar(sugerencia) },
                            enabled = !estado.escribiendo,
                            label = { Text(sugerencia, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(color = Gris.copy(alpha = 0.4f), modifier = Modifier.padding(top = 8.dp))

            // ---------- Escribir ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe tu pregunta…") },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { enviarTexto() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { enviarTexto() },
                    enabled = texto.isNotBlank() && !estado.escribiendo,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = AzulOscuro)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BurbujaMensaje(mensaje: MensajeChat) {
    val esUsuario = mensaje.esUsuario

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (esUsuario) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (esUsuario) 16.dp else 4.dp,
                        bottomEnd = if (esUsuario) 4.dp else 16.dp
                    )
                )
                .background(if (esUsuario) AzulOscuro else Azul.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = mensaje.texto,
                color = if (esUsuario) Color.White else TextoOscuro,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

private fun etiquetaRol(usuario: Usuario): String = when {
    usuario.esEncargadoSalud -> "Encargado de salud"
    usuario.esProfesor -> "Profesor"
    usuario.esPadre -> "Padre/madre"
    else -> "Sin rol"
}

// ======================================================
// PREVIEWS (se ven en Android Studio sin Firebase).
// Usa el modo "Interactive" de la Preview para escribir y
// probar las respuestas con los datos de ejemplo.
// ======================================================

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "Padre - abierto")
@Composable
private fun PreviewChatPadre() {
    ChatbotOverlay(usuario = ChatbotDemo.padre, datos = ChatbotDatosDemo(), iniciarAbierto = true)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "Encargado - abierto")
@Composable
private fun PreviewChatEncargado() {
    ChatbotOverlay(usuario = ChatbotDemo.encargado, datos = ChatbotDatosDemo(), iniciarAbierto = true)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "Profesor - abierto")
@Composable
private fun PreviewChatProfesor() {
    ChatbotOverlay(usuario = ChatbotDemo.profesor, datos = ChatbotDatosDemo(), iniciarAbierto = true)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720, name = "Solo el círculo")
@Composable
private fun PreviewChatCerrado() {
    ChatbotOverlay(usuario = ChatbotDemo.padre, datos = ChatbotDatosDemo())
}

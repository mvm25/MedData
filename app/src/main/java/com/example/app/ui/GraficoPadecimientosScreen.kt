package com.example.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AzulOscuro
import com.example.app.Fondo
import com.example.app.Gris
import com.example.app.TextoOscuro
import com.example.app.data.LocalRepo
import com.example.app.model.Padecimiento
import java.text.Normalizer
import java.util.Locale

// ======================================================
// GRÁFICO CIRCULAR DE PADECIMIENTOS
//
// Solo lo abre el Encargado de Salud (botón junto al buscador de
// EstudiantesScreen). Muestra qué porcentaje de los padecimientos
// registrados corresponde a cada enfermedad, con una leyenda de
// colores al lado.
//
// Se actualiza solo: escucha Firebase en vivo, así que si se
// agrega, edita o elimina un padecimiento el gráfico cambia sin
// tener que salir y volver a entrar.
//
// Regla de conteo: un estudiante cuenta UNA sola vez por
// padecimiento (aunque lo tenga registrado dos veces), y
// "Rinitis", "rinitis " y "RINITIS" cuentan como el mismo.
//   Ejemplo: 3 estudiantes -> 2 con rinitis y 1 con asma
//            = rinitis 66,7 %  ·  asma 33,3 %
// ======================================================

data class EstadisticaPadecimiento(
    val nombre: String,
    val cantidad: Int,        // cuántos estudiantes lo tienen
    val porcentaje: Float,    // 0..100
    val color: Color
)

data class ResumenPadecimientos(
    val estadisticas: List<EstadisticaPadecimiento>,
    val totalRegistros: Int,
    val totalEstudiantes: Int
)

object EstadisticasPadecimientos {

    private val paleta = listOf(
        Color(0xFF587C89), Color(0xFFDAA5AF), Color(0xFFE0B04F), Color(0xFF6FA97F),
        Color(0xFF8E7CC3), Color(0xFFE07A5F), Color(0xFF4FA3C7), Color(0xFFB5838D),
        Color(0xFF3D405B), Color(0xFFC3C0AF), Color(0xFF81B29A), Color(0xFFF2CC8F)
    )

    private fun colorPara(indice: Int): Color =
        if (indice < paleta.size) paleta[indice]
        else Color.hsv((indice * 137.5f) % 360f, 0.55f, 0.85f)   // colores extra si hay muchísimas enfermedades

    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun calcular(padecimientos: List<Padecimiento>): ResumenPadecimientos {
        val vistos = HashSet<Pair<String, String>>()     // (carné, padecimiento) ya contados
        val conteo = LinkedHashMap<String, Int>()
        val nombres = HashMap<String, String>()
        val estudiantes = HashSet<String>()

        for (p in padecimientos) {
            val nombre = p.nombre.trim().replace(Regex("\\s+"), " ")
            if (nombre.isEmpty()) continue

            val clave = normalizar(nombre)
            if (!vistos.add(p.carne to clave)) continue

            conteo[clave] = (conteo[clave] ?: 0) + 1
            if (clave !in nombres) nombres[clave] = nombre.replaceFirstChar { it.uppercase() }
            estudiantes.add(p.carne)
        }

        val total = conteo.values.sum()
        val ordenados = conteo.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )

        val lista = ordenados.mapIndexed { i, e ->
            EstadisticaPadecimiento(
                nombre = nombres.getValue(e.key),
                cantidad = e.value,
                porcentaje = e.value * 100f / total,
                color = colorPara(i)
            )
        }
        return ResumenPadecimientos(lista, total, estudiantes.size)
    }
}

private fun formatoPorcentaje(p: Float): String =
    String.format(Locale.forLanguageTag("es"), "%.1f%%", p)

// ------------------------------------------------------
// PANTALLA
// ------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraficoPadecimientosScreen(onVolver: () -> Unit) {

    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var padecimientos by remember { mutableStateOf(listOf<Padecimiento>()) }

    // Escucha en vivo mientras la pantalla está abierta y deja de escuchar al salir.
    DisposableEffect(Unit) {
        val detener = LocalRepo.observarPadecimientosEnVivo { lista ->
            if (lista == null) {
                error = true
            } else {
                padecimientos = lista
                error = false
            }
            cargando = false
        }
        onDispose { detener() }
    }

    val resumen = remember(padecimientos) { EstadisticasPadecimientos.calcular(padecimientos) }

    BackHandler(onBack = onVolver)

    Scaffold(
        containerColor = Fondo,
        topBar = {
            TopAppBar(
                title = { Text("Gráfico de padecimientos", fontWeight = FontWeight.Bold) },
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
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                cargando -> CircularProgressIndicator(color = AzulOscuro)
                error -> Text(
                    "No se pudo cargar la información. Revisa tu conexión e inténtalo de nuevo.",
                    color = Gris,
                    modifier = Modifier.padding(24.dp)
                )
                resumen.estadisticas.isEmpty() -> Text(
                    "Aún no hay padecimientos registrados.",
                    color = Gris,
                    modifier = Modifier.padding(24.dp)
                )
                else -> ContenidoGrafico(resumen)
            }
        }
    }
}

@Composable
private fun ContenidoGrafico(resumen: ResumenPadecimientos) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Padecimientos registrados", fontWeight = FontWeight.Bold, color = TextoOscuro, fontSize = 17.sp)
                Text(
                    "${resumen.totalRegistros} padecimiento(s) en ${resumen.totalEstudiantes} estudiante(s)",
                    color = Gris,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    GraficoCircular(resumen.estadisticas, Modifier.size(150.dp))

                    Spacer(Modifier.width(16.dp))

                    // Leyenda a la par del gráfico.
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        resumen.estadisticas.forEach { FilaLeyenda(it) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Cada estudiante cuenta una sola vez por padecimiento.",
                    color = Gris,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GraficoCircular(datos: List<EstadisticaPadecimiento>, modifier: Modifier = Modifier) {

    // Animación de entrada (se dibuja de 0 a 360°). Los cambios posteriores se aplican al instante.
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progreso.animateTo(1f, tween(700)) }

    val descripcion = "Gráfico circular de padecimientos: " +
        datos.joinToString(", ") { "${it.nombre} ${formatoPorcentaje(it.porcentaje)}" }

    Canvas(modifier = modifier.semantics { contentDescription = descripcion }) {
        var inicio = -90f   // arranca arriba
        datos.forEach { d ->
            val barrido = 360f * d.porcentaje / 100f * progreso.value
            drawArc(color = d.color, startAngle = inicio, sweepAngle = barrido, useCenter = true)
            if (datos.size > 1) {
                // Línea blanca fina para separar los sectores.
                drawArc(
                    color = Color.White,
                    startAngle = inicio,
                    sweepAngle = barrido,
                    useCenter = true,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            inicio += barrido
        }
    }
}

@Composable
private fun FilaLeyenda(d: EstadisticaPadecimiento) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(d.color)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                d.nombre,
                fontWeight = FontWeight.Medium,
                color = TextoOscuro,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (if (d.cantidad == 1) "1 estudiante" else "${d.cantidad} estudiantes") +
                    " · ${formatoPorcentaje(d.porcentaje)}",
                color = Gris,
                fontSize = 12.sp
            )
        }
    }
}

// ------------------------------------------------------
// PREVIEW (no necesita Firebase)
// Ejemplo: 3 estudiantes, 2 con rinitis y 1 con asma.
// ------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 500)
@Composable
private fun PreviewGraficoPadecimientos() {
    val resumen = EstadisticasPadecimientos.calcular(
        listOf(
            Padecimiento(idPadecimiento = "1", carne = "2024001", nombre = "Rinitis"),
            Padecimiento(idPadecimiento = "2", carne = "2024002", nombre = "rinitis "),
            Padecimiento(idPadecimiento = "3", carne = "2024003", nombre = "Asma")
        )
    )
    ContenidoGrafico(resumen)
}

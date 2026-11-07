package com.example.app.chatbot

import com.example.app.model.*
import java.text.Normalizer

// ======================================================
// MOTOR DEL CHATBOT
//
// ChatbotMotor es la "interfaz" que usa la pantalla. Hoy la
// implementa ChatbotMotorReglas (entiende preguntas por palabras
// clave y responde con datos de ChatbotDatos). Si más adelante
// quieren usar un modelo de IA, se crea otra clase que implemente
// ChatbotMotor y se cambia en ChatbotOverlay; la UI no cambia.
//
// IMPORTANTE si se conecta una IA: la llave del modelo NUNCA debe
// ir dentro de la app. Debe llamarse desde un servidor (por ejemplo
// una Cloud Function) que valide el token del usuario y aplique los
// mismos permisos de ChatbotPermisos del lado del servidor.
// ======================================================

data class MensajeChat(
    val id: Long,
    val texto: String,
    val esUsuario: Boolean
)

enum class Intencion {
    SALUDO, AYUDA, GRACIAS, LISTA, FICHA,
    PADECIMIENTOS, ANTECEDENTES, VACUNAS, CITAS, AUTORIZACIONES,
    SOLICITUDES, MODIFICAR, CONSEJO_MEDICO, DESCONOCIDO
}

/** Memoria de una conversación (se borra al cerrar sesión). */
class ContextoChat {
    var carneActual: String? = null
    var intencionPendiente: Intencion? = null
    var estudiantes: List<Estudiante>? = null
    var estudiantesCargadosEn: Long = 0L
}

interface ChatbotMotor {
    fun saludoInicial(usuario: Usuario): String
    fun sugerencias(usuario: Usuario): List<String>
    fun responder(
        texto: String,
        usuario: Usuario,
        contexto: ContextoChat,
        onRespuesta: (String) -> Unit
    )
}

class ChatbotMotorReglas(private val datos: ChatbotDatos) : ChatbotMotor {

    // -------------------- TEXTOS FIJOS --------------------

    private val sinAcceso =
        "Tu cuenta no tiene un rol con acceso a la información médica. Contacta al colegio."

    private val denegado =
        "No tienes permiso para ver la información de ese estudiante. " +
            "Solo puedo mostrarte datos de los estudiantes a los que tu cuenta tiene acceso."

    private val consejoMedico =
        "No puedo dar diagnósticos, dosis ni recomendaciones médicas. " +
            "Para eso consulta a un profesional de salud o al encargado de salud del colegio. " +
            "Sí puedo mostrarte la información que ya está registrada."

    // -------------------- API --------------------

    override fun saludoInicial(usuario: Usuario): String {
        val nombre = usuario.nombre.trim().split(" ").firstOrNull().orEmpty()
        val hola = if (nombre.isBlank()) "¡Hola!" else "¡Hola, $nombre!"
        return "$hola Soy el asistente de MedData. Solo consulto información: no creo, edito ni elimino registros.\n\n" +
            when {
                usuario.esPadre -> "Puedo mostrarte la información médica de tus hijos y el estado de tus solicitudes."
                usuario.esEncargadoSalud -> "Puedo consultar la ficha de cualquier estudiante y las solicitudes pendientes."
                usuario.esProfesor -> "Puedo consultar la información médica de los estudiantes (solo lectura)."
                else -> sinAcceso
            }
    }

    override fun sugerencias(usuario: Usuario): List<String> = when {
        usuario.esPadre -> listOf("Mis hijos", "Vacunas", "Citas médicas", "Mis solicitudes")
        usuario.esEncargadoSalud -> listOf("Solicitudes pendientes", "Estudiantes", "Ayuda")
        usuario.esProfesor -> listOf("Estudiantes", "Ayuda")
        else -> emptyList()
    }

    override fun responder(
        texto: String,
        usuario: Usuario,
        contexto: ContextoChat,
        onRespuesta: (String) -> Unit
    ) {
        if (!ChatbotPermisos.tieneRolValido(usuario)) {
            onRespuesta(sinAcceso)
            return
        }

        val norm = normalizar(texto)
        val tokens = tokenizar(norm)

        when (val intencion = detectarIntencion(norm, tokens)) {
            Intencion.SALUDO -> onRespuesta(saludoInicial(usuario))
            Intencion.AYUDA -> onRespuesta(textoAyuda(usuario))
            Intencion.GRACIAS -> onRespuesta("¡Con gusto! Si necesitas algo más, aquí estoy.")
            Intencion.MODIFICAR -> onRespuesta(textoModificar(usuario))
            Intencion.CONSEJO_MEDICO -> onRespuesta(consejoMedico)
            Intencion.SOLICITUDES -> responderSolicitudes(usuario, onRespuesta)
            Intencion.LISTA -> cargarEstudiantes(usuario, contexto, { onRespuesta(textoError(it)) }) { lista ->
                onRespuesta(textoLista(usuario, lista))
            }
            else -> responderSobreEstudiante(intencion, norm, tokens, usuario, contexto, onRespuesta)
        }
    }

    // -------------------- INTENCIÓN --------------------

    private val verbosModificar = setOf(
        "agregar", "agrega", "anadir", "anade", "crear", "crea", "editar", "edita",
        "modificar", "modifica", "cambiar", "cambia", "eliminar", "elimina", "borrar", "borra",
        "actualizar", "actualiza", "aceptar", "acepta", "rechazar", "rechaza",
        "devolver", "devuelve", "enviar", "envio", "subir", "sube", "registrar", "registra"
    )

    private fun detectarIntencion(norm: String, tokens: List<String>): Intencion {
        fun tiene(vararg prefijos: String) = tokens.any { t -> prefijos.any { t.startsWith(it) } }

        return when {
            tokens.any { it in verbosModificar } -> Intencion.MODIFICAR
            tiene("sintoma", "receta", "recomiend") ||
                norm.contains("es grave") || norm.contains("que debo") ||
                norm.contains("que puedo darle") -> Intencion.CONSEJO_MEDICO
            tiene("solicit") -> Intencion.SOLICITUDES
            tiene("padec", "enfermed", "condicion") -> Intencion.PADECIMIENTOS
            tiene("antecedent") -> Intencion.ANTECEDENTES
            tiene("vacun", "inmuniz") -> Intencion.VACUNAS
            tiene("cita") -> Intencion.CITAS
            tiene("autoriz", "medicament", "medicina", "pastilla") -> Intencion.AUTORIZACIONES
            tiene("ficha", "resumen", "expediente", "informacion", "datos") -> Intencion.FICHA
            tiene("estudiant", "alumn", "hijo", "hija", "lista") -> Intencion.LISTA
            tiene("ayuda", "puedes", "haces", "funcion", "opcion") -> Intencion.AYUDA
            tiene("gracias") -> Intencion.GRACIAS
            tiene("hola", "buen", "hey", "saludos") -> Intencion.SALUDO
            else -> Intencion.DESCONOCIDO
        }
    }

    // -------------------- ESTUDIANTES --------------------

    private fun cargarEstudiantes(
        usuario: Usuario,
        contexto: ContextoChat,
        onError: (Throwable) -> Unit,
        onListo: (List<Estudiante>) -> Unit
    ) {
        val cache = contexto.estudiantes
        val ahora = System.currentTimeMillis()
        if (cache != null && ahora - contexto.estudiantesCargadosEn < 120_000) {
            onListo(cache)
            return
        }
        datos.estudiantesVisibles(usuario) { resultado ->
            resultado.fold(
                onSuccess = { lista ->
                    // Doble candado: aunque la fuente ya filtre, se vuelve a filtrar aquí.
                    val permitidos = lista.filter { ChatbotPermisos.puedeVerEstudiante(usuario, it.carne) }
                    contexto.estudiantes = permitidos
                    contexto.estudiantesCargadosEn = System.currentTimeMillis()
                    onListo(permitidos)
                },
                onFailure = onError
            )
        }
    }

    private sealed class Resolucion {
        data class Encontrado(val estudiante: Estudiante, val explicito: Boolean) : Resolucion()
        data class Ambiguo(val candidatos: List<Estudiante>, val explicito: Boolean) : Resolucion()
        object NoPermitido : Resolucion()
        object NoEncontrado : Resolucion()
    }

    private val palabrasVacias = setOf("del", "los", "las", "mis", "por", "con", "que", "una", "uno")

    private fun tokensNombre(e: Estudiante): List<String> =
        tokenizar(normalizar("${e.nombre} ${e.apellido}"))
            .filter { it.length >= 3 && it !in palabrasVacias }

    private fun resolverEstudiante(
        norm: String,
        tokens: List<String>,
        visibles: List<Estudiante>,
        usuario: Usuario,
        contexto: ContextoChat
    ): Resolucion {
        val pegado = norm.replace(Regex("[^a-z0-9]"), "")

        // 1. Carné escrito en el mensaje (solo entre los que el usuario puede ver).
        visibles.firstOrNull { e ->
            val c = normalizar(e.carne).replace(Regex("[^a-z0-9]"), "")
            c.length >= 4 && pegado.contains(c)
        }?.let { return Resolucion.Encontrado(it, true) }

        // 2. Escribió algo que parece un carné pero no está entre los permitidos.
        //    (Ajustar la expresión si los carnés del colegio tienen otro formato.)
        //    No se confirma si ese carné existe o no.
        if (Regex("\\d{5,}").containsMatchIn(norm)) {
            return if (usuario.esPadre) Resolucion.NoPermitido else Resolucion.NoEncontrado
        }

        // 3. Nombre o apellido.
        val puntajes = visibles.map { e -> e to tokensNombre(e).count { it in tokens } }
        val mejor = puntajes.maxOfOrNull { it.second } ?: 0
        if (mejor > 0) {
            val top = puntajes.filter { it.second == mejor }.map { it.first }
            return if (top.size == 1) Resolucion.Encontrado(top[0], true)
            else Resolucion.Ambiguo(top, true)
        }

        // 4. Sin nombre en el mensaje: usar el contexto o el único hijo.
        if (usuario.esPadre && visibles.size == 1) return Resolucion.Encontrado(visibles[0], false)
        contexto.carneActual?.let { actual ->
            visibles.firstOrNull { it.carne == actual }?.let { return Resolucion.Encontrado(it, false) }
        }
        if (usuario.esPadre && visibles.size > 1) return Resolucion.Ambiguo(visibles, false)
        return Resolucion.NoEncontrado
    }

    private fun responderSobreEstudiante(
        intencion: Intencion,
        norm: String,
        tokens: List<String>,
        usuario: Usuario,
        contexto: ContextoChat,
        onRespuesta: (String) -> Unit
    ) {
        cargarEstudiantes(usuario, contexto, { onRespuesta(textoError(it)) }) { visibles ->
            if (visibles.isEmpty()) {
                onRespuesta(
                    if (usuario.esPadre)
                        "No encontré estudiantes vinculados a tu cuenta. Revisa que los carnés de tus hijos estén bien registrados."
                    else
                        "Todavía no hay estudiantes registrados."
                )
                return@cargarEstudiantes
            }

            val sinIntencion = intencion == Intencion.DESCONOCIDO

            when (val r = resolverEstudiante(norm, tokens, visibles, usuario, contexto)) {
                is Resolucion.NoPermitido -> onRespuesta(denegado)

                is Resolucion.Encontrado -> {
                    if (sinIntencion && !r.explicito) {
                        onRespuesta(textoNoEntendi(usuario))
                    } else {
                        val queQuiere = if (sinIntencion) {
                            contexto.intencionPendiente ?: Intencion.FICHA
                        } else intencion
                        responderFicha(queQuiere, r.estudiante, usuario, contexto, onRespuesta)
                    }
                }

                is Resolucion.Ambiguo -> {
                    if (sinIntencion && !r.explicito) {
                        onRespuesta(textoNoEntendi(usuario))
                    } else {
                        if (!sinIntencion) contexto.intencionPendiente = intencion
                        onRespuesta(textoAmbiguo(r.candidatos))
                    }
                }

                is Resolucion.NoEncontrado -> {
                    if (sinIntencion) {
                        onRespuesta(textoNoEntendi(usuario))
                    } else {
                        contexto.intencionPendiente = intencion
                        onRespuesta("¿De qué estudiante? Escribe su nombre o su carné.")
                    }
                }
            }
        }
    }

    private fun responderFicha(
        intencion: Intencion,
        est: Estudiante,
        usuario: Usuario,
        contexto: ContextoChat,
        onRespuesta: (String) -> Unit
    ) {
        if (!ChatbotPermisos.puedeVerEstudiante(usuario, est.carne)) {
            onRespuesta(denegado)
            return
        }

        contexto.carneActual = est.carne
        contexto.intencionPendiente = null

        datos.ficha(est.carne) { resultadoFicha ->
            val ficha = resultadoFicha.getOrElse {
                onRespuesta(textoError(it))
                return@ficha
            }
            val nombre = est.nombreCompleto.ifBlank { est.carne }

            onRespuesta(
                when (intencion) {
                    Intencion.PADECIMIENTOS -> bloque(
                        "Padecimientos de $nombre", "No hay padecimientos registrados para $nombre.",
                        ficha.padecimientos.map { p ->
                            "• ${p.nombre.ifBlank { "Sin nombre" }}" + detalle(
                                linea("Tipo" to p.tipo, "Estado" to p.estado, "Diagnóstico" to p.fechaDiagnostico,
                                    "Tratamiento" to p.tratamiento, "Obs." to p.observaciones)
                            )
                        }
                    )

                    Intencion.ANTECEDENTES -> bloque(
                        "Antecedentes de $nombre", "No hay antecedentes registrados para $nombre.",
                        ficha.antecedentes.map { a ->
                            "• ${a.tipo.ifBlank { "Antecedente" }}" + detalle(
                                linea("Descripción" to a.descripcion, "Fecha" to a.fecha, "Obs." to a.observaciones)
                            )
                        }
                    )

                    Intencion.VACUNAS -> bloque(
                        "Vacunas de $nombre", "No hay vacunas registradas para $nombre.",
                        ficha.vacunas.map { v ->
                            "• ${v.nombre.ifBlank { "Vacuna" }}" + detalle(
                                linea("Previene" to v.enfermedad, "Aplicada" to v.fechaAplicacion,
                                    "Próxima dosis" to v.proximaDosis, "Lote" to v.lote, "Estado" to v.estado)
                            )
                        }
                    )

                    Intencion.CITAS -> bloque(
                        "Citas médicas de $nombre", "No hay citas médicas registradas para $nombre.",
                        ficha.citas.map { c ->
                            val titulo = listOf(c.tipo.replaceFirstChar { it.uppercase() }, c.fecha)
                                .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Cita" }
                            "• $titulo" + detalle(linea("Motivo" to c.motivo, "Obs." to c.observaciones))
                        }
                    )

                    Intencion.AUTORIZACIONES -> bloque(
                        "Autorizaciones de medicamento de $nombre",
                        "No hay autorizaciones de medicamento registradas para $nombre.",
                        ficha.autorizaciones.map { a ->
                            "• ${a.medicamento.ifBlank { "Medicamento" }} — " +
                                (if (a.autorizado) "Autorizado" else "No autorizado") +
                                detalle(linea("Motivo" to a.motivo))
                        }
                    )

                    else -> {
                        val generales = linea(
                            "Edad" to (if (est.edad > 0) est.edad.toString() else ""),
                            "Sección" to est.seccion,
                            "Sexo" to est.sexo
                        )
                        "Ficha de $nombre (carné ${est.carne})\n" +
                            (if (generales.isNotBlank()) "$generales\n" else "") +
                            "• Padecimientos: ${ficha.padecimientos.size}\n" +
                            "• Antecedentes: ${ficha.antecedentes.size}\n" +
                            "• Vacunas: ${ficha.vacunas.size}\n" +
                            "• Citas médicas: ${ficha.citas.size}\n" +
                            "• Autorizaciones de medicamento: ${ficha.autorizaciones.size}\n\n" +
                            "Pídeme el detalle de cualquiera, por ejemplo: \"vacunas\"."
                    }
                }
            )
        }
    }

    // -------------------- SOLICITUDES --------------------

    private fun responderSolicitudes(usuario: Usuario, onRespuesta: (String) -> Unit) {
        when {
            ChatbotPermisos.puedeVerSolicitudesPendientes(usuario) -> {
                datos.solicitudesPendientes { resultado ->
                    val lista = resultado.getOrElse {
                        onRespuesta(textoError(it))
                        return@solicitudesPendientes
                    }
                    val pendientes = lista.filter { it.estado == EstadoSolicitud.PENDIENTE }
                        .sortedBy { it.fechaEnvio }
                    if (pendientes.isEmpty()) {
                        onRespuesta("No hay solicitudes pendientes.")
                    } else {
                        val lineas = pendientes.take(8).joinToString("\n") { s ->
                            "• ${CatalogoCampos.etiquetaTipo(s.tipo)} · carné ${s.carneEstudiante} · " +
                                "de ${s.nombrePadre.ifBlank { "un padre" }} · ${s.fechaEnvio}"
                        }
                        val resto = if (pendientes.size > 8) "\n…y ${pendientes.size - 8} más." else ""
                        onRespuesta(
                            "Hay ${pendientes.size} solicitud(es) pendiente(s):\n$lineas$resto\n\n" +
                                "Puedes revisarlas en \"Solicitudes pendientes\"."
                        )
                    }
                }
            }

            ChatbotPermisos.puedeVerSusSolicitudes(usuario) -> {
                datos.solicitudesDe(usuario.idUsuario) { resultado ->
                    val lista = resultado.getOrElse {
                        onRespuesta(textoError(it))
                        return@solicitudesDe
                    }
                    // Doble candado: solo las del propio padre.
                    val propias = lista.filter { it.idPadre == usuario.idUsuario }
                        .sortedByDescending { it.fechaEnvio }
                    if (propias.isEmpty()) {
                        onRespuesta("No has enviado solicitudes todavía.")
                    } else {
                        fun cuantas(estado: String) = propias.count { it.estado == estado }
                        val resumen = linea(
                            "Pendientes" to cuantas(EstadoSolicitud.PENDIENTE).toString(),
                            "Devueltas" to cuantas(EstadoSolicitud.DEVUELTA).toString(),
                            "Aceptadas" to cuantas(EstadoSolicitud.ACEPTADA).toString(),
                            "Rechazadas" to cuantas(EstadoSolicitud.RECHAZADA).toString()
                        )
                        val lineas = propias.take(8).joinToString("\n") { s ->
                            "• ${CatalogoCampos.etiquetaTipo(s.tipo)} · carné ${s.carneEstudiante} · " +
                                "${etiquetaEstado(s.estado)} · ${s.fechaEnvio}" +
                                (if (s.comentarioEncargado.isNotBlank())
                                    "\n   Comentario del encargado: ${s.comentarioEncargado}" else "")
                        }
                        onRespuesta("Tus solicitudes ($resumen):\n$lineas")
                    }
                }
            }

            else -> onRespuesta("Tu rol no tiene acceso a las solicitudes.")
        }
    }

    private fun textoError(e: Throwable): String = when (e) {
        is ChatbotNoConectadoException ->
            "El asistente todavía no está conectado a la base de datos. Inténtalo más tarde."
        is ChatbotSinPermisoException ->
            "No tengo permiso para leer esa información."
        else ->
            "No pude consultar la base de datos. Revisa tu conexión e inténtalo de nuevo."
    }

    private fun etiquetaEstado(estado: String) = when (estado) {
        EstadoSolicitud.PENDIENTE -> "Pendiente"
        EstadoSolicitud.ACEPTADA -> "Aceptada"
        EstadoSolicitud.RECHAZADA -> "Rechazada"
        EstadoSolicitud.DEVUELTA -> "Devuelta para corregir"
        else -> estado
    }

    // -------------------- TEXTOS --------------------

    private fun textoLista(usuario: Usuario, lista: List<Estudiante>): String {
        if (usuario.esPadre) {
            if (lista.isEmpty()) {
                return "No encontré estudiantes vinculados a tu cuenta. Revisa que los carnés estén bien registrados."
            }
            val faltantes = usuario.carnesHijos.filter { c -> lista.none { it.carne == c } }
            return "Tus hijos registrados:\n" +
                lista.joinToString("\n") { "• ${it.nombreCompleto} · carné ${it.carne} · sección ${it.seccion}" } +
                (if (faltantes.isNotEmpty()) "\n\nEstos carnés de tu cuenta aún no aparecen en el colegio: ${faltantes.joinToString(", ")}." else "")
        }

        if (lista.isEmpty()) return "Todavía no hay estudiantes registrados."
        val muestra = lista.take(10).joinToString("\n") { "• ${it.nombreCompleto} · carné ${it.carne} · ${it.seccion}" }
        val resto = if (lista.size > 10) "\n…y ${lista.size - 10} más." else ""
        return "Hay ${lista.size} estudiante(s) registrado(s):\n$muestra$resto\n\n" +
            "Escribe el nombre o carné de uno para ver su información."
    }

    private fun textoAmbiguo(candidatos: List<Estudiante>): String {
        val lineas = candidatos.take(6).joinToString("\n") { "• ${it.nombreCompleto} (carné ${it.carne})" }
        val resto = if (candidatos.size > 6) "\n…y ${candidatos.size - 6} más." else ""
        return "¿De cuál estudiante?\n$lineas$resto\n\nEscribe el nombre completo o el carné."
    }

    private fun textoAyuda(usuario: Usuario): String = when {
        usuario.esPadre ->
            "Puedo ayudarte con:\n" +
                "• Ver a tus hijos\n• Padecimientos, antecedentes, vacunas, citas y autorizaciones de medicamento de cada hijo\n" +
                "• El estado de tus solicitudes\n\nEjemplo: \"vacunas de Lucía\"."
        usuario.esEncargadoSalud ->
            "Puedo ayudarte con:\n" +
                "• Buscar un estudiante por nombre o carné\n• Ver su ficha, padecimientos, antecedentes, vacunas, citas y autorizaciones\n" +
                "• Ver las solicitudes pendientes\n\nEjemplo: \"citas de 2024002\"."
        usuario.esProfesor ->
            "Puedo ayudarte con:\n" +
                "• Buscar un estudiante por nombre o carné\n• Ver su información médica (solo lectura)\n\nEjemplo: \"padecimientos de Mateo\"."
        else -> sinAcceso
    }

    private fun textoNoEntendi(usuario: Usuario) =
        "No estoy seguro de haber entendido. " + textoAyuda(usuario)

    private fun textoModificar(usuario: Usuario): String = when {
        usuario.esEncargadoSalud ->
            "Yo solo consulto información. Para crear, editar o eliminar registros usa la pantalla de Estudiantes, " +
                "y para aceptar, rechazar o devolver solicitudes usa \"Solicitudes pendientes\"."
        usuario.esPadre ->
            "Yo solo consulto información y no puedo modificar registros. Para agregar o corregir datos de tu hijo/a " +
                "usa \"Enviar información\" (recuerda adjuntar el dictamen médico). El encargado de salud la revisará."
        usuario.esProfesor ->
            "Tu rol es de solo lectura: no puedes crear, editar ni eliminar información, y yo tampoco puedo hacerlo por ti."
        else -> sinAcceso
    }

    // -------------------- UTILIDADES --------------------

    private fun bloque(titulo: String, vacio: String, items: List<String>): String =
        if (items.isEmpty()) vacio else "$titulo:\n" + items.joinToString("\n")

    private fun linea(vararg partes: Pair<String, String>): String =
        partes.filter { it.second.isNotBlank() }.joinToString(" · ") { "${it.first}: ${it.second}" }

    private fun detalle(s: String) = if (s.isBlank()) "" else "\n   $s"

    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun tokenizar(norm: String): List<String> =
        norm.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
}

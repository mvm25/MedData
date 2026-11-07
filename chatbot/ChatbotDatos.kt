package com.example.app.chatbot

import com.example.app.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

// ======================================================
// FUENTE DE DATOS DEL CHATBOT
//
// El motor no sabe de dónde vienen los datos: solo usa la
// interfaz ChatbotDatos. Hay dos implementaciones:
//
//  - ChatbotDatosFirebase: lee directo de Firebase Realtime
//    Database. Es independiente de LocalRepo y lee solo lo que el
//    rol necesita (un padre lee únicamente los nodos de SUS hijos).
//    Si Firebase todavía no está configurado, NO falla: devuelve
//    ChatbotNoConectadoException y el chat avisa que aún no hay
//    conexión.
//  - ChatbotDatosDemo: datos de ejemplo en memoria, para probar
//    el chat (Previews de Android Studio) sin Firebase.
//
// Para conectar: ver GUIA_CHATBOT.md. Si el esquema de la base
// cambia, solo hay que editar ChatbotRutas.
// ======================================================

/** Nombres de los nodos en Realtime Database (deben coincidir con reglas_realtime_database.json). */
object ChatbotRutas {
    const val ESTUDIANTES = "estudiantes"
    const val PADECIMIENTOS = "padecimientos"
    const val ANTECEDENTES = "antecedentes"
    const val VACUNAS = "vacunas"
    const val CITAS = "citas_medicas"
    const val AUTORIZACIONES = "autorizaciones"
    const val SOLICITUDES = "solicitudes"
}

class ChatbotNoConectadoException : Exception("Firebase no está configurado todavía")
class ChatbotSinPermisoException : Exception("Permiso denegado por las reglas de Firebase")

object ChatbotConfig {

    /**
     * true  -> datos de ejemplo (no toca Firebase).
     * false -> datos reales desde Firebase.
     */
    const val USAR_DATOS_DEMO = false

    val datos: ChatbotDatos by lazy {
        if (USAR_DATOS_DEMO) ChatbotDatosDemo() else ChatbotDatosFirebase()
    }
}

interface ChatbotDatos {
    /** Estudiantes que ESTE usuario puede ver (padre: solo sus hijos). */
    fun estudiantesVisibles(usuario: Usuario, onResult: (Result<List<Estudiante>>) -> Unit)

    fun ficha(carne: String, onResult: (Result<FichaMedica>) -> Unit)

    fun solicitudesPendientes(onResult: (Result<List<Solicitud>>) -> Unit)

    fun solicitudesDe(idPadre: String, onResult: (Result<List<Solicitud>>) -> Unit)
}

// ------------------------------------------------------
// FIREBASE (real)
// ------------------------------------------------------

class ChatbotDatosFirebase : ChatbotDatos {

    /** null si Firebase aún no está inicializado (no hay google-services.json / proyecto). */
    private fun raiz() = try {
        FirebaseApp.getInstance()
        FirebaseDatabase.getInstance().reference
    } catch (e: Exception) {
        null
    }

    private fun <T> leer(ref: Query, convertir: (DataSnapshot) -> T, onResult: (Result<T>) -> Unit) {
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(
                    try {
                        Result.success(convertir(snapshot))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(
                    Result.failure(
                        if (error.code == DatabaseError.PERMISSION_DENIED) ChatbotSinPermisoException()
                        else error.toException()
                    )
                )
            }
        })
    }

    private fun <T : Any> leerLista(ref: Query, clase: Class<T>, onResult: (Result<List<T>>) -> Unit) =
        leer(ref, { s -> s.children.mapNotNull { it.getValue(clase) } }, onResult)

    override fun estudiantesVisibles(usuario: Usuario, onResult: (Result<List<Estudiante>>) -> Unit) {
        val db = raiz() ?: return onResult(Result.failure(ChatbotNoConectadoException()))

        when {
            // Padre: se lee solo el nodo de cada hijo, nunca la lista completa.
            usuario.esPadre -> {
                val carnes = usuario.carnesHijos.filter { it.isNotBlank() }.distinct()
                if (carnes.isEmpty()) return onResult(Result.success(emptyList()))

                val resultados = arrayOfNulls<Estudiante>(carnes.size)
                var faltan = carnes.size
                var error: Throwable? = null

                carnes.forEachIndexed { i, carne ->
                    leer(db.child(ChatbotRutas.ESTUDIANTES).child(carne), { it.getValue(Estudiante::class.java) }) { r ->
                        r.onSuccess { resultados[i] = it }
                        r.onFailure { if (error == null) error = it }
                        faltan--
                        if (faltan == 0) {
                            val e = error
                            onResult(
                                if (e != null) Result.failure(e)
                                else Result.success(resultados.filterNotNull().sortedBy { it.nombreCompleto })
                            )
                        }
                    }
                }
            }

            ChatbotPermisos.puedeVerListaCompleta(usuario) ->
                leerLista(db.child(ChatbotRutas.ESTUDIANTES), Estudiante::class.java) { r ->
                    onResult(r.map { lista -> lista.sortedBy { it.nombreCompleto } })
                }

            else -> onResult(Result.success(emptyList()))
        }
    }

    override fun ficha(carne: String, onResult: (Result<FichaMedica>) -> Unit) {
        val db = raiz() ?: return onResult(Result.failure(ChatbotNoConectadoException()))

        var est = Estudiante()
        var pads = listOf<Padecimiento>()
        var ants = listOf<Antecedente>()
        var vacs = listOf<Vacuna>()
        var cits = listOf<CitaMedica>()
        var auts = listOf<AutorizacionMedicamento>()
        var faltan = 6
        var error: Throwable? = null

        fun terminar() {
            faltan--
            if (faltan == 0) {
                val e = error
                onResult(
                    if (e != null) Result.failure(e)
                    else Result.success(FichaMedica(est, pads, ants, vacs, cits, auts))
                )
            }
        }

        fun <T> alLlegar(r: Result<T>, guardar: (T) -> Unit) {
            r.onSuccess(guardar)
            r.onFailure { if (error == null) error = it }
            terminar()
        }

        leer(db.child(ChatbotRutas.ESTUDIANTES).child(carne), { it.getValue(Estudiante::class.java) ?: Estudiante() }) { alLlegar(it) { v -> est = v } }
        leerLista(db.child(ChatbotRutas.PADECIMIENTOS).child(carne), Padecimiento::class.java) { alLlegar(it) { v -> pads = v } }
        leerLista(db.child(ChatbotRutas.ANTECEDENTES).child(carne), Antecedente::class.java) { alLlegar(it) { v -> ants = v } }
        leerLista(db.child(ChatbotRutas.VACUNAS).child(carne), Vacuna::class.java) { alLlegar(it) { v -> vacs = v } }
        leerLista(db.child(ChatbotRutas.CITAS).child(carne), CitaMedica::class.java) { alLlegar(it) { v -> cits = v } }
        leerLista(db.child(ChatbotRutas.AUTORIZACIONES).child(carne), AutorizacionMedicamento::class.java) { alLlegar(it) { v -> auts = v } }
    }

    // Consultas filtradas en el servidor (requieren ".indexOn" en las reglas; ya agregado
    // en reglas_realtime_database.json).

    override fun solicitudesPendientes(onResult: (Result<List<Solicitud>>) -> Unit) {
        val db = raiz() ?: return onResult(Result.failure(ChatbotNoConectadoException()))
        leerLista(
            db.child(ChatbotRutas.SOLICITUDES).orderByChild("estado").equalTo(EstadoSolicitud.PENDIENTE),
            Solicitud::class.java,
            onResult
        )
    }

    override fun solicitudesDe(idPadre: String, onResult: (Result<List<Solicitud>>) -> Unit) {
        val db = raiz() ?: return onResult(Result.failure(ChatbotNoConectadoException()))
        leerLista(
            db.child(ChatbotRutas.SOLICITUDES).orderByChild("idPadre").equalTo(idPadre),
            Solicitud::class.java,
            onResult
        )
    }
}

// ------------------------------------------------------
// DEMO (en memoria)
// ------------------------------------------------------

object ChatbotDemo {

    val padre = Usuario(
        idUsuario = "demo-padre",
        nombre = "Carlos Mora",
        rol = Roles.PADRE,
        carnesHijos = listOf("2024001", "2024004")
    )

    val encargado = Usuario(
        idUsuario = "demo-encargado",
        nombre = "Marta Solano",
        rol = Roles.ENCARGADO_SALUD
    )

    val profesor = Usuario(
        idUsuario = "demo-profesor",
        nombre = "Jorge Alfaro",
        rol = Roles.PROFESOR
    )
}

class ChatbotDatosDemo : ChatbotDatos {

    private val estudiantes = listOf(
        Estudiante(carne = "2024001", nombre = "Lucía", apellido = "Mora", edad = 12, sexo = "F", seccion = "7-A"),
        Estudiante(carne = "2024002", nombre = "Mateo", apellido = "Rojas", edad = 13, sexo = "M", seccion = "8-B"),
        Estudiante(carne = "2024003", nombre = "Sofía", apellido = "Vargas", edad = 12, sexo = "F", seccion = "7-A"),
        Estudiante(carne = "2024004", nombre = "Diego", apellido = "Mora", edad = 15, sexo = "M", seccion = "10-C")
    )

    private val fichas: Map<String, FichaMedica> = mapOf(
        "2024001" to FichaMedica(
            estudiante = estudiantes[0],
            padecimientos = listOf(
                Padecimiento("p1", "2024001", "Asma", "Respiratorio", "2019-03-10", "Controlado", "Inhalador de rescate", "")
            ),
            antecedentes = listOf(
                Antecedente("a1", "2024001", "Cirugía", "Amigdalectomía", "2021-07-02", "Sin complicaciones")
            ),
            vacunas = listOf(
                Vacuna("v1", "2024001", "Influenza", "Influenza estacional", "2026-04-15", "2027-04-15", "L-4482", "Al día")
            ),
            citas = listOf(
                CitaMedica("c1", "2024001", "fisica", "Control de asma", "2026-12-02", "Traer inhalador")
            ),
            autorizaciones = listOf(
                AutorizacionMedicamento("m1", "2024001", "demo-padre", "Salbutamol", "Crisis de asma", true)
            )
        ),
        "2024002" to FichaMedica(
            estudiante = estudiantes[1],
            padecimientos = listOf(
                Padecimiento("p2", "2024002", "Rinitis alérgica", "Alergia", "2020-05-01", "Activo", "Antihistamínico", "")
            )
        ),
        "2024003" to FichaMedica(estudiante = estudiantes[2]),
        "2024004" to FichaMedica(
            estudiante = estudiantes[3],
            vacunas = listOf(
                Vacuna("v2", "2024004", "Tdap", "Tétanos, difteria y tos ferina", "2025-02-20", "", "T-9012", "Al día")
            ),
            citas = listOf(
                CitaMedica("c2", "2024004", "nutricional", "Seguimiento de peso", "2026-11-20", "")
            )
        )
    )

    private val solicitudes = listOf(
        Solicitud(
            idSolicitud = "s1", carneEstudiante = "2024001", idPadre = "demo-padre", nombrePadre = "Carlos Mora",
            tipo = TipoSolicitud.VACUNA, estado = EstadoSolicitud.PENDIENTE, fechaEnvio = "2026-11-04 08:30"
        ),
        Solicitud(
            idSolicitud = "s2", carneEstudiante = "2024004", idPadre = "demo-padre", nombrePadre = "Carlos Mora",
            tipo = TipoSolicitud.PADECIMIENTO, estado = EstadoSolicitud.DEVUELTA,
            comentarioEncargado = "El dictamen no se lee bien, súbelo de nuevo.", fechaEnvio = "2026-11-02 14:10"
        ),
        Solicitud(
            idSolicitud = "s3", carneEstudiante = "2024002", idPadre = "demo-otro-padre", nombrePadre = "Ana Rojas",
            tipo = TipoSolicitud.AUTORIZACION_MEDICAMENTO, estado = EstadoSolicitud.PENDIENTE, fechaEnvio = "2026-11-05 10:00"
        )
    )

    override fun estudiantesVisibles(usuario: Usuario, onResult: (Result<List<Estudiante>>) -> Unit) {
        onResult(Result.success(estudiantes.filter { ChatbotPermisos.puedeVerEstudiante(usuario, it.carne) }))
    }

    override fun ficha(carne: String, onResult: (Result<FichaMedica>) -> Unit) {
        onResult(Result.success(fichas[carne] ?: FichaMedica()))
    }

    override fun solicitudesPendientes(onResult: (Result<List<Solicitud>>) -> Unit) {
        onResult(Result.success(solicitudes.filter { it.estado == EstadoSolicitud.PENDIENTE }))
    }

    override fun solicitudesDe(idPadre: String, onResult: (Result<List<Solicitud>>) -> Unit) {
        onResult(Result.success(solicitudes.filter { it.idPadre == idPadre }))
    }
}

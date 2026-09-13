package com.example.app.model

// ======================================================
// MODELOS DE DATOS
//
// Roles del sistema (se eliminaron "profesor" y "brigadista":
// hacían casi lo mismo que un encargado de salud, así que se
// unificaron en un solo rol con permisos completos):
//
//  - ENCARGADO_SALUD: el único rol que puede crear, editar y
//    eliminar información médica (CRUD completo). También es
//    quien revisa las Solicitudes que mandan los padres:
//    puede Aceptarlas, Rechazarlas, Devolverlas al padre para
//    que corrija algo, o corregir él mismo un dato mal escrito
//    (una coma, un tildé, etc.) antes de aceptarla.
//
//  - PADRE: solo puede ver la información de su(s) hijo(s) y
//    enviar Solicitudes con información nueva o cambios. No
//    puede editar directamente ningún registro médico.
//
// Todas las clases traen valores por defecto ("") porque así
// es más fácil convertirlas desde/hacia una base de datos (por
// ejemplo Firebase Realtime Database más adelante).
// ======================================================

object Roles {
    const val ENCARGADO_SALUD = "encargado_salud"
    const val PADRE = "padre"
}

data class Usuario(
    val idUsuario: String = "",
    val nombre: String = "",
    val correo: String = "",
    val telefono: String = "",
    val rol: String = Roles.PADRE,
    // Solo aplica si rol == PADRE: carné(s) del/los estudiante(s)
    // de los que es encargado. Un padre puede tener más de un hijo
    // registrado en el colegio.
    val carnesHijos: List<String> = emptyList()
) {
    val esEncargadoSalud: Boolean get() = rol == Roles.ENCARGADO_SALUD
    val esPadre: Boolean get() = rol == Roles.PADRE
}

data class Estudiante(
    val carne: String = "",
    val nombre: String = "",
    val apellido: String = "",
    val correo: String = "",
    val telefono: String = "",
    val edad: Int = 0,
    val sexo: String = "",
    val seccion: String = ""
) {
    val nombreCompleto: String
        get() = "$nombre $apellido".trim()
}

data class Padecimiento(
    val idPadecimiento: String = "",
    val carne: String = "",
    val nombre: String = "",
    val tipo: String = "",
    val fechaDiagnostico: String = "",
    val estado: String = "",
    val tratamiento: String = "",
    val observaciones: String = ""
)

data class Antecedente(
    val idAntecedente: String = "",
    val carne: String = "",
    val tipo: String = "",
    val descripcion: String = "",
    val fecha: String = "",
    val observaciones: String = ""
)

data class Vacuna(
    val idVacuna: String = "",
    val carne: String = "",
    val nombre: String = "",
    val enfermedad: String = "",
    val fechaAplicacion: String = "",
    val proximaDosis: String = "",
    val lote: String = "",
    val estado: String = ""
)

data class CitaMedica(
    val idCita: String = "",
    val carne: String = "",
    // "mental", "fisica" o "nutricional"
    val tipo: String = "",
    val motivo: String = "",
    val fecha: String = "",
    val observaciones: String = ""
)

data class AutorizacionMedicamento(
    val idAutorizacion: String = "",
    val carne: String = "",
    val idUsuario: String = "",
    val medicamento: String = "",
    val motivo: String = "",
    val autorizado: Boolean = false
)

/**
 * Agrupa toda la ficha médica de un estudiante para mostrarla
 * en la pantalla de detalle.
 */
data class FichaMedica(
    val estudiante: Estudiante = Estudiante(),
    val padecimientos: List<Padecimiento> = emptyList(),
    val antecedentes: List<Antecedente> = emptyList(),
    val vacunas: List<Vacuna> = emptyList(),
    val citas: List<CitaMedica> = emptyList(),
    val autorizaciones: List<AutorizacionMedicamento> = emptyList()
)

// ======================================================
// SOLICITUDES
//
// Es lo que manda un Padre cuando quiere agregar o corregir
// información de su hijo/a. No modifica nada directamente:
// queda "pendiente" hasta que un Encargado de Salud la revisa.
// ======================================================

object TipoSolicitud {
    const val PADECIMIENTO = "padecimiento"
    const val ANTECEDENTE = "antecedente"
    const val VACUNA = "vacuna"
    const val CITA_MEDICA = "cita_medica"
    const val AUTORIZACION_MEDICAMENTO = "autorizacion_medicamento"
    const val DATOS_GENERALES = "datos_generales"
}

object EstadoSolicitud {
    const val PENDIENTE = "pendiente"
    const val ACEPTADA = "aceptada"
    const val RECHAZADA = "rechazada"
    // Vuelve al padre porque falta o está mal un dato.
    const val DEVUELTA = "devuelta"
}

data class Solicitud(
    val idSolicitud: String = "",
    val carneEstudiante: String = "",
    val idPadre: String = "",
    val nombrePadre: String = "",
    val tipo: String = TipoSolicitud.PADECIMIENTO,
    // Vacío si es información nueva. Si es una corrección de un
    // registro que ya existe, aquí va el id de ese registro.
    val idRegistroRelacionado: String = "",
    // Los datos propuestos, como pares clave/valor. La clave es el
    // mismo nombre del campo del modelo real (ver CatalogoCampos).
    val campos: Map<String, String> = emptyMap(),
    val estado: String = EstadoSolicitud.PENDIENTE,
    // Motivo de rechazo, o qué hay que corregir si fue devuelta.
    val comentarioEncargado: String = "",
    val fechaEnvio: String = "",
    val fechaRevision: String = "",
    val idEncargadoRevisor: String = ""
)

/** Describe un campo de un formulario dinámico (clave interna + texto que ve el usuario). */
data class CampoFormulario(
    val clave: String,
    val etiqueta: String
)

/**
 * Catálogo de qué campos se piden según el tipo de solicitud (o de
 * registro). Se usa para construir el mismo formulario genérico en
 * tres lugares distintos: cuando el padre envía una solicitud, cuando
 * el encargado de salud crea/edita un registro directamente, y cuando
 * el encargado corrige un dato antes de aceptar una solicitud.
 */
object CatalogoCampos {

    fun camposPara(tipo: String): List<CampoFormulario> = when (tipo) {

        TipoSolicitud.PADECIMIENTO -> listOf(
            CampoFormulario("nombre", "Nombre del padecimiento"),
            CampoFormulario("tipo", "Tipo"),
            CampoFormulario("fechaDiagnostico", "Fecha de diagnóstico"),
            CampoFormulario("estado", "Estado"),
            CampoFormulario("tratamiento", "Tratamiento"),
            CampoFormulario("observaciones", "Observaciones")
        )

        TipoSolicitud.ANTECEDENTE -> listOf(
            CampoFormulario("tipo", "Tipo de antecedente"),
            CampoFormulario("descripcion", "Descripción"),
            CampoFormulario("fecha", "Fecha"),
            CampoFormulario("observaciones", "Observaciones")
        )

        TipoSolicitud.VACUNA -> listOf(
            CampoFormulario("nombre", "Nombre de la vacuna"),
            CampoFormulario("enfermedad", "Enfermedad que previene"),
            CampoFormulario("fechaAplicacion", "Fecha de aplicación"),
            CampoFormulario("proximaDosis", "Próxima dosis"),
            CampoFormulario("lote", "Lote"),
            CampoFormulario("estado", "Estado")
        )

        TipoSolicitud.CITA_MEDICA -> listOf(
            CampoFormulario("tipo", "Tipo (mental / física / nutricional)"),
            CampoFormulario("motivo", "Motivo"),
            CampoFormulario("fecha", "Fecha"),
            CampoFormulario("observaciones", "Observaciones")
        )

        TipoSolicitud.AUTORIZACION_MEDICAMENTO -> listOf(
            CampoFormulario("medicamento", "Medicamento"),
            CampoFormulario("motivo", "Motivo"),
            CampoFormulario("autorizado", "¿Autorizado? (si / no)")
        )

        TipoSolicitud.DATOS_GENERALES -> listOf(
            CampoFormulario("telefono", "Teléfono"),
            CampoFormulario("correo", "Correo"),
            CampoFormulario("seccion", "Sección"),
            CampoFormulario("sexo", "Sexo"),
            CampoFormulario("edad", "Edad")
        )

        else -> emptyList()
    }

    fun etiquetaTipo(tipo: String): String = when (tipo) {
        TipoSolicitud.PADECIMIENTO -> "Padecimiento"
        TipoSolicitud.ANTECEDENTE -> "Antecedente"
        TipoSolicitud.VACUNA -> "Vacuna"
        TipoSolicitud.CITA_MEDICA -> "Cita médica"
        TipoSolicitud.AUTORIZACION_MEDICAMENTO -> "Autorización de medicamento"
        TipoSolicitud.DATOS_GENERALES -> "Datos generales del estudiante"
        else -> tipo
    }

    val todosLosTipos = listOf(
        TipoSolicitud.PADECIMIENTO,
        TipoSolicitud.ANTECEDENTE,
        TipoSolicitud.VACUNA,
        TipoSolicitud.CITA_MEDICA,
        TipoSolicitud.AUTORIZACION_MEDICAMENTO,
        TipoSolicitud.DATOS_GENERALES
    )
}

package com.example.app.data

import com.example.app.model.*

// ======================================================
// MAPEADOR
//
// Las Solicitudes guardan sus datos como un simple
// Map<String, String> (clave -> valor en texto) para poder
// usar un solo formulario genérico sin importar el tipo.
// Estas funciones convierten ese mapa hacia/desde los
// modelos reales (Padecimiento, Vacuna, etc.) en el momento
// en que un Encargado de Salud acepta la solicitud.
// ======================================================

object Mapeador {

    // -------------------- PADECIMIENTO --------------------

    fun aMapa(p: Padecimiento): Map<String, String> = mapOf(
        "nombre" to p.nombre,
        "tipo" to p.tipo,
        "fechaDiagnostico" to p.fechaDiagnostico,
        "estado" to p.estado,
        "tratamiento" to p.tratamiento,
        "observaciones" to p.observaciones
    )

    fun padecimientoDeMapa(carne: String, id: String, m: Map<String, String>) = Padecimiento(
        idPadecimiento = id,
        carne = carne,
        nombre = m["nombre"].orEmpty(),
        tipo = m["tipo"].orEmpty(),
        fechaDiagnostico = m["fechaDiagnostico"].orEmpty(),
        estado = m["estado"].orEmpty(),
        tratamiento = m["tratamiento"].orEmpty(),
        observaciones = m["observaciones"].orEmpty()
    )

    // -------------------- ANTECEDENTE --------------------

    fun aMapa(a: Antecedente): Map<String, String> = mapOf(
        "tipo" to a.tipo,
        "descripcion" to a.descripcion,
        "fecha" to a.fecha,
        "observaciones" to a.observaciones
    )

    fun antecedenteDeMapa(carne: String, id: String, m: Map<String, String>) = Antecedente(
        idAntecedente = id,
        carne = carne,
        tipo = m["tipo"].orEmpty(),
        descripcion = m["descripcion"].orEmpty(),
        fecha = m["fecha"].orEmpty(),
        observaciones = m["observaciones"].orEmpty()
    )

    // -------------------- VACUNA --------------------

    fun aMapa(v: Vacuna): Map<String, String> = mapOf(
        "nombre" to v.nombre,
        "enfermedad" to v.enfermedad,
        "fechaAplicacion" to v.fechaAplicacion,
        "proximaDosis" to v.proximaDosis,
        "lote" to v.lote,
        "estado" to v.estado
    )

    fun vacunaDeMapa(carne: String, id: String, m: Map<String, String>) = Vacuna(
        idVacuna = id,
        carne = carne,
        nombre = m["nombre"].orEmpty(),
        enfermedad = m["enfermedad"].orEmpty(),
        fechaAplicacion = m["fechaAplicacion"].orEmpty(),
        proximaDosis = m["proximaDosis"].orEmpty(),
        lote = m["lote"].orEmpty(),
        estado = m["estado"].orEmpty()
    )

    // -------------------- CITA MÉDICA --------------------

    fun aMapa(c: CitaMedica): Map<String, String> = mapOf(
        "tipo" to c.tipo,
        "motivo" to c.motivo,
        "fecha" to c.fecha,
        "observaciones" to c.observaciones
    )

    fun citaDeMapa(carne: String, id: String, m: Map<String, String>) = CitaMedica(
        idCita = id,
        carne = carne,
        tipo = m["tipo"].orEmpty(),
        motivo = m["motivo"].orEmpty(),
        fecha = m["fecha"].orEmpty(),
        observaciones = m["observaciones"].orEmpty()
    )

    // -------------------- AUTORIZACIÓN DE MEDICAMENTO --------------------

    fun aMapa(au: AutorizacionMedicamento): Map<String, String> = mapOf(
        "medicamento" to au.medicamento,
        "motivo" to au.motivo,
        "autorizado" to if (au.autorizado) "si" else "no"
    )

    fun autorizacionDeMapa(carne: String, id: String, idUsuario: String, m: Map<String, String>) = AutorizacionMedicamento(
        idAutorizacion = id,
        carne = carne,
        idUsuario = idUsuario,
        medicamento = m["medicamento"].orEmpty(),
        motivo = m["motivo"].orEmpty(),
        autorizado = m["autorizado"]?.trim()?.lowercase() in listOf("si", "sí", "true", "1")
    )

    // -------------------- DATOS GENERALES DEL ESTUDIANTE --------------------

    fun aMapaDatosGenerales(e: Estudiante): Map<String, String> = mapOf(
        "telefono" to e.telefono,
        "correo" to e.correo,
        "seccion" to e.seccion,
        "sexo" to e.sexo,
        "edad" to e.edad.toString()
    )

    fun aplicarDatosGenerales(e: Estudiante, m: Map<String, String>): Estudiante = e.copy(
        telefono = m["telefono"] ?: e.telefono,
        correo = m["correo"] ?: e.correo,
        seccion = m["seccion"] ?: e.seccion,
        sexo = m["sexo"] ?: e.sexo,
        edad = m["edad"]?.toIntOrNull() ?: e.edad
    )
}

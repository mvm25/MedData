package com.example.app.chatbot

import com.example.app.model.Usuario

// ======================================================
// PERMISOS DEL CHATBOT
//
// Una sola fuente de verdad sobre qué puede consultar cada rol.
// El motor SIEMPRE pasa por aquí antes de pedir datos, y vuelve a
// filtrar lo que regresa la fuente de datos (doble candado).
//
//  - ENCARGADO_SALUD: consulta todos los estudiantes y las
//    solicitudes pendientes.
//  - PROFESOR: solo lectura de estudiantes y su ficha. Sin solicitudes.
//  - PADRE: solo SUS hijos (carnesHijos) y SUS propias solicitudes.
//
// El chatbot es de consulta: no crea, edita ni elimina nada para
// ningún rol.
// ======================================================

object ChatbotPermisos {

    fun tieneRolValido(usuario: Usuario): Boolean =
        usuario.esEncargadoSalud || usuario.esPadre || usuario.esProfesor

    fun puedeVerEstudiante(usuario: Usuario, carne: String): Boolean = when {
        usuario.esEncargadoSalud || usuario.esProfesor -> true
        usuario.esPadre -> carne.isNotBlank() && carne in usuario.carnesHijos
        else -> false
    }

    fun puedeVerListaCompleta(usuario: Usuario): Boolean =
        usuario.esEncargadoSalud || usuario.esProfesor

    fun puedeVerSolicitudesPendientes(usuario: Usuario): Boolean =
        usuario.esEncargadoSalud

    fun puedeVerSusSolicitudes(usuario: Usuario): Boolean =
        usuario.esPadre && usuario.idUsuario.isNotBlank()
}

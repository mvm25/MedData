package com.example.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import com.example.app.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalRepo {

    private val db = FirebaseDatabase.getInstance().reference

    // -------------------- USUARIOS --------------------

    fun obtenerUsuario(uid: String, onResult: (Usuario?) -> Unit) {
        db.child("usuarios").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.getValue(Usuario::class.java)?.copy(idUsuario = uid))
                }
                override fun onCancelled(error: DatabaseError) { onResult(null) }
            })
    }

    fun guardarUsuario(uid: String, usuario: Usuario, onDone: (Boolean) -> Unit = {}) {
        db.child("usuarios").child(uid).setValue(usuario.copy(idUsuario = uid))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // -------------------- ESTUDIANTES --------------------

    fun observarEstudiantes(onResult: (List<Estudiante>) -> Unit) {
        db.child("estudiantes")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.children.mapNotNull { it.getValue(Estudiante::class.java) }
                        .sortedBy { it.nombreCompleto })
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    fun observarEstudiantesDe(carnes: List<String>, onResult: (List<Estudiante>) -> Unit) {
        observarEstudiantes { lista -> onResult(lista.filter { it.carne in carnes }) }
    }

    fun guardarEstudiante(estudiante: Estudiante, onDone: (Boolean) -> Unit = {}) {
        db.child("estudiantes").child(estudiante.carne).setValue(estudiante)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarEstudiante(carne: String, onDone: (Boolean) -> Unit = {}) {
        db.child("estudiantes").child(carne).removeValue()
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun actualizarDatosGenerales(estudiante: Estudiante, onDone: (Boolean) -> Unit = {}) {
        db.child("estudiantes").child(estudiante.carne).setValue(estudiante)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Busca todos los carnés/cédulas que los padres hayan registrado al crear su cuenta. */
    fun observarCarnesRegistradosPorPadres(onResult: (List<String>) -> Unit) {
        db.child("usuarios")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val carnes = snapshot.children.mapNotNull { it.getValue(Usuario::class.java) }
                        .filter { it.esPadre }
                        .flatMap { it.carnesHijos }
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    onResult(carnes)
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    // -------------------- ESTADÍSTICAS --------------------

    /**
     * Escucha TODOS los padecimientos de todos los estudiantes (padecimientos/{carne}/{id})
     * y avisa cada vez que algo cambia: alta, edición o baja.
     * Devuelve una función para dejar de escuchar; hay que llamarla al cerrar la pantalla.
     * onResult recibe null si Firebase rechazó la lectura.
     */
    fun observarPadecimientosEnVivo(onResult: (List<Padecimiento>?) -> Unit): () -> Unit {
        val ref = db.child("padecimientos")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onResult(
                    snapshot.children.flatMap { porEstudiante ->
                        porEstudiante.children.mapNotNull { hijo ->
                            hijo.getValue(Padecimiento::class.java)?.let { pad ->
                                if (pad.carne.isBlank()) pad.copy(carne = porEstudiante.key.orEmpty()) else pad
                            }
                        }
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) { onResult(null) }
        }
        ref.addValueEventListener(listener)
        return { ref.removeEventListener(listener) }
    }

    // -------------------- FICHA MÉDICA COMPLETA --------------------

    fun obtenerFichaMedica(carne: String, onResult: (FichaMedica) -> Unit) {
        // Obtenemos los 6 datos necesarios para construir la FichaMedica.
        // Como son asíncronos, los pedimos y vamos guardando resultados.
        var est = Estudiante()
        var pads = listOf<Padecimiento>()
        var ants = listOf<Antecedente>()
        var vacs = listOf<Vacuna>()
        var cits = listOf<CitaMedica>()
        var auts = listOf<AutorizacionMedicamento>()

        var faltan = 6
        fun check() {
            faltan--
            if (faltan == 0) {
                onResult(FichaMedica(est, pads, ants, vacs, cits, auts))
            }
        }

        db.child("estudiantes").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { est = s.getValue(Estudiante::class.java) ?: Estudiante(); check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
        db.child("padecimientos").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { pads = s.children.mapNotNull { it.getValue(Padecimiento::class.java) }; check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
        db.child("antecedentes").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { ants = s.children.mapNotNull { it.getValue(Antecedente::class.java) }; check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
        db.child("vacunas").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { vacs = s.children.mapNotNull { it.getValue(Vacuna::class.java) }; check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
        db.child("citas_medicas").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { cits = s.children.mapNotNull { it.getValue(CitaMedica::class.java) }; check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
        db.child("autorizaciones").child(carne).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { auts = s.children.mapNotNull { it.getValue(AutorizacionMedicamento::class.java) }; check() }
            override fun onCancelled(e: DatabaseError) { check() }
        })
    }

    // -------------------- CRUD MÉDICO --------------------

    fun guardarPadecimiento(p: Padecimiento, onDone: (Boolean) -> Unit = {}) {
        val id = p.idPadecimiento.ifBlank { db.child("padecimientos").child(p.carne).push().key ?: return }
        db.child("padecimientos").child(p.carne).child(id).setValue(p.copy(idPadecimiento = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarPadecimiento(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("padecimientos").child(carne).child(id).removeValue().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun guardarAntecedente(a: Antecedente, onDone: (Boolean) -> Unit = {}) {
        val id = a.idAntecedente.ifBlank { db.child("antecedentes").child(a.carne).push().key ?: return }
        db.child("antecedentes").child(a.carne).child(id).setValue(a.copy(idAntecedente = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarAntecedente(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("antecedentes").child(carne).child(id).removeValue().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun guardarVacuna(v: Vacuna, onDone: (Boolean) -> Unit = {}) {
        val id = v.idVacuna.ifBlank { db.child("vacunas").child(v.carne).push().key ?: return }
        db.child("vacunas").child(v.carne).child(id).setValue(v.copy(idVacuna = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarVacuna(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("vacunas").child(carne).child(id).removeValue().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun guardarCita(c: CitaMedica, onDone: (Boolean) -> Unit = {}) {
        val id = c.idCita.ifBlank { db.child("citas_medicas").child(c.carne).push().key ?: return }
        db.child("citas_medicas").child(c.carne).child(id).setValue(c.copy(idCita = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarCita(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("citas_medicas").child(carne).child(id).removeValue().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun guardarAutorizacion(au: AutorizacionMedicamento, onDone: (Boolean) -> Unit = {}) {
        val id = au.idAutorizacion.ifBlank { db.child("autorizaciones").child(au.carne).push().key ?: return }
        db.child("autorizaciones").child(au.carne).child(id).setValue(au.copy(idAutorizacion = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarAutorizacion(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("autorizaciones").child(carne).child(id).removeValue().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // -------------------- SOLICITUDES --------------------

    /**
     * El padre envía una solicitud nueva. El dictamen médico (imagen en
     * Base64, ver ImagenUtil) es OBLIGATORIO: si viene vacío no se envía
     * nada. Se guarda la imagen primero y la solicitud después, así el
     * encargado de salud nunca ve una solicitud sin su dictamen.
     */
    fun crearSolicitud(solicitud: Solicitud, dictamenBase64: String, onDone: (Boolean) -> Unit = {}) {
        if (dictamenBase64.isBlank()) return onDone(false)

        val id = db.child("solicitudes").push().key ?: return onDone(false)

        // Formato año-mes-día para que ordenar por texto = ordenar por fecha.
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val nueva = solicitud.copy(
            idSolicitud = id,
            estado = EstadoSolicitud.PENDIENTE,
            fechaEnvio = fecha,
            tieneDictamen = true
        )

        db.child("dictamenes").child(id).setValue(dictamenBase64)
            .addOnCompleteListener { guardoImagen ->
                if (!guardoImagen.isSuccessful) {
                    onDone(false)
                    return@addOnCompleteListener
                }
                db.child("solicitudes").child(id).setValue(nueva)
                    .addOnCompleteListener { onDone(it.isSuccessful) }
            }
    }

    /** Trae la imagen del dictamen (Base64) de una solicitud, o null si no hay / falló. */
    fun obtenerDictamen(idSolicitud: String, onResult: (String?) -> Unit) {
        db.child("dictamenes").child(idSolicitud)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.getValue(String::class.java))
                }
                override fun onCancelled(error: DatabaseError) { onResult(null) }
            })
    }

    fun observarSolicitudesPendientes(onResult: (List<Solicitud>) -> Unit) {
        db.child("solicitudes")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.children.mapNotNull { it.getValue(Solicitud::class.java) }
                        .filter { it.estado == EstadoSolicitud.PENDIENTE })
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    fun observarTodasLasSolicitudes(onResult: (List<Solicitud>) -> Unit) {
        db.child("solicitudes")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.children.mapNotNull { it.getValue(Solicitud::class.java) }
                        .sortedByDescending { it.fechaEnvio })
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    fun observarSolicitudesDePadre(idPadre: String, onResult: (List<Solicitud>) -> Unit) {
        db.child("solicitudes")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.children.mapNotNull { it.getValue(Solicitud::class.java) }
                        .filter { it.idPadre == idPadre }
                        .sortedByDescending { it.fechaEnvio })
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    fun aceptarSolicitud(idSolicitud: String, idEncargado: String, camposFinales: Map<String, String>, onDone: (Boolean) -> Unit = {}) {
        db.child("solicitudes").child(idSolicitud).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)

                when (solicitud.tipo) {
                    TipoSolicitud.PADECIMIENTO -> guardarPadecimiento(Mapeador.padecimientoDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, camposFinales))
                    TipoSolicitud.ANTECEDENTE -> guardarAntecedente(Mapeador.antecedenteDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, camposFinales))
                    TipoSolicitud.VACUNA -> guardarVacuna(Mapeador.vacunaDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, camposFinales))
                    TipoSolicitud.CITA_MEDICA -> guardarCita(Mapeador.citaDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, camposFinales))
                    TipoSolicitud.AUTORIZACION_MEDICAMENTO -> guardarAutorizacion(Mapeador.autorizacionDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, solicitud.idPadre, camposFinales))
                    TipoSolicitud.DATOS_GENERALES -> {
                        db.child("estudiantes").child(solicitud.carneEstudiante).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(s: DataSnapshot) {
                                val est = s.getValue(Estudiante::class.java) ?: return
                                actualizarDatosGenerales(Mapeador.aplicarDatosGenerales(est, camposFinales))
                            }
                            override fun onCancelled(e: DatabaseError) {}
                        })
                    }
                }

                db.child("solicitudes").child(idSolicitud).setValue(
                    solicitud.copy(estado = EstadoSolicitud.ACEPTADA, campos = camposFinales, idEncargadoRevisor = idEncargado)
                ).addOnCompleteListener { onDone(it.isSuccessful) }
            }
            override fun onCancelled(error: DatabaseError) { onDone(false) }
        })
    }

    fun rechazarSolicitud(idSolicitud: String, idEncargado: String, motivo: String, onDone: (Boolean) -> Unit = {}) {
        db.child("solicitudes").child(idSolicitud).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)
                db.child("solicitudes").child(idSolicitud).setValue(
                    solicitud.copy(estado = EstadoSolicitud.RECHAZADA, idEncargadoRevisor = idEncargado, comentarioEncargado = motivo)
                ).addOnCompleteListener { onDone(it.isSuccessful) }
            }
            override fun onCancelled(error: DatabaseError) { onDone(false) }
        })
    }

    fun devolverSolicitud(idSolicitud: String, idEncargado: String, motivo: String, onDone: (Boolean) -> Unit = {}) {
        db.child("solicitudes").child(idSolicitud).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)
                db.child("solicitudes").child(idSolicitud).setValue(
                    solicitud.copy(estado = EstadoSolicitud.DEVUELTA, idEncargadoRevisor = idEncargado, comentarioEncargado = motivo)
                ).addOnCompleteListener { onDone(it.isSuccessful) }
            }
            override fun onCancelled(error: DatabaseError) { onDone(false) }
        })
    }

    /**
     * El padre corrige y reenvía una solicitud devuelta. Si el encargado la
     * devolvió porque el dictamen no se leía, el padre puede mandar uno nuevo
     * en [nuevoDictamenBase64]; si es null, se queda el que ya estaba.
     */
    fun reenviarSolicitud(
        idSolicitud: String,
        nuevosCampos: Map<String, String>,
        nuevoDictamenBase64: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        fun actualizarSolicitud() {
            db.child("solicitudes").child(idSolicitud).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)
                    db.child("solicitudes").child(idSolicitud).setValue(
                        solicitud.copy(
                            estado = EstadoSolicitud.PENDIENTE,
                            campos = nuevosCampos,
                            comentarioEncargado = "",
                            tieneDictamen = solicitud.tieneDictamen || !nuevoDictamenBase64.isNullOrBlank()
                        )
                    ).addOnCompleteListener { onDone(it.isSuccessful) }
                }
                override fun onCancelled(error: DatabaseError) { onDone(false) }
            })
        }

        if (nuevoDictamenBase64.isNullOrBlank()) {
            actualizarSolicitud()
        } else {
            db.child("dictamenes").child(idSolicitud).setValue(nuevoDictamenBase64)
                .addOnCompleteListener { guardoImagen ->
                    if (guardoImagen.isSuccessful) actualizarSolicitud() else onDone(false)
                }
        }
    }
}

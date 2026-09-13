package com.example.app.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import com.example.app.model.*

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

    fun crearSolicitud(solicitud: Solicitud, onDone: (Boolean) -> Unit = {}) {
        val id = db.child("solicitudes").push().key ?: return
        db.child("solicitudes").child(id).setValue(
            solicitud.copy(idSolicitud = id, estado = EstadoSolicitud.PENDIENTE)
        ).addOnCompleteListener { onDone(it.isSuccessful) }
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

    fun reenviarSolicitud(idSolicitud: String, nuevosCampos: Map<String, String>, onDone: (Boolean) -> Unit = {}) {
        db.child("solicitudes").child(idSolicitud).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)
                db.child("solicitudes").child(idSolicitud).setValue(
                    solicitud.copy(estado = EstadoSolicitud.PENDIENTE, campos = nuevosCampos, comentarioEncargado = "")
                ).addOnCompleteListener { onDone(it.isSuccessful) }
            }
            override fun onCancelled(error: DatabaseError) { onDone(false) }
        })
    }
}

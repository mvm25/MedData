package com.example.app.data

import com.google.firebase.auth.FirebaseAuth

data class SesionUsuario(
    val uid: String,
    val correo: String,
    val nombre: String
)

object LocalAuth {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: SesionUsuario?
        get() = auth.currentUser?.let {
            SesionUsuario(uid = it.uid, correo = it.email.orEmpty(), nombre = it.displayName.orEmpty())
        }

    fun crearCuenta(
        nombre: String,
        correo: String,
        password: String,
        onResult: (exito: Boolean, uid: String?, error: String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(correo, password)
            .addOnCompleteListener { tarea ->
                if (tarea.isSuccessful) {
                    val uid = tarea.result?.user?.uid
                    // Guarda el nombre en el perfil de FirebaseAuth (opcional).
                    val cambios = com.google.firebase.auth.userProfileChangeRequest {
                        displayName = nombre
                    }
                    tarea.result?.user?.updateProfile(cambios)
                    onResult(true, uid, null)
                } else {
                    onResult(false, null, tarea.exception?.message)
                }
            }
    }

    fun iniciarSesion(
        correo: String,
        password: String,
        onResult: (exito: Boolean, uid: String?, error: String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(correo, password)
            .addOnCompleteListener { tarea ->
                if (tarea.isSuccessful) {
                    onResult(true, tarea.result?.user?.uid, null)
                } else {
                    onResult(false, null, tarea.exception?.message)
                }
            }
    }

    fun cerrarSesion() {
        auth.signOut()
    }

    /**
     * Borra la cuenta que acaba de crearse. Se usa cuando el registro falla
     * a la mitad (por ejemplo, el código de acceso era incorrecto y la base
     * de datos rechazó el perfil): sin esto quedaría un usuario en
     * Authentication sin perfil, y ese correo ya no se podría volver a usar.
     */
    fun eliminarCuentaActual(onDone: () -> Unit = {}) {
        val usuario = auth.currentUser
        if (usuario == null) {
            onDone()
            return
        }
        usuario.delete().addOnCompleteListener {
            auth.signOut()
            onDone()
        }
    }
}
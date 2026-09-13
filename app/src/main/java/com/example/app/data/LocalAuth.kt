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
}
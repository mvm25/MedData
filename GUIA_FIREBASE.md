# Guía para conectar Firebase (Auth + Realtime Database)

Ahora mismo la app **no está conectada a Firebase**: usa dos objetos locales,
`LocalAuth` y `LocalRepo`, que guardan todo en memoria (RAM) mientras la app
está abierta. Esto se hizo a propósito para poder probar todo el flujo de
roles (Encargado de Salud / Padre) y de Solicitudes sin necesitar todavía un
proyecto de Firebase real.

Esta guía explica exactamente **qué archivos tocar** y **qué poner en cada
uno** cuando quieras conectar Firebase de verdad. La idea es que solo
reemplaces el *contenido* de las funciones, no cómo las llaman las pantallas
(los nombres y los parámetros de las funciones se dejaron iguales a propósito).

---

## 0. Lo que ya viene listo en el proyecto

En `app/build.gradle.kts` ya están estas líneas (no hay que agregarlas):

```kotlin
plugins {
    ...
    id("com.google.gms.google-services")
}

dependencies {
    ...
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
}
```

También debería existir un archivo `google-services.json` dentro de
`app/app/`. Si no está (o es de otro proyecto), tienes que:

1. Ir a [https://console.firebase.google.com](https://console.firebase.google.com)
2. Crear un proyecto (o usar uno existente).
3. Agregar una app Android con el `applicationId` **com.example.app** (es el
   mismo `namespace`/`applicationId` que ya tiene el proyecto en
   `build.gradle.kts`).
4. Descargar el `google-services.json` que te da Firebase.
5. Ponerlo exactamente en: `app/app/google-services.json`
   (al mismo nivel que `build.gradle.kts` del módulo `app`).
6. En la consola de Firebase, activar:
   - **Authentication** → método "Correo electrónico/contraseña".
   - **Realtime Database** → crear la base (elige la región más cercana).

---

## 1. Reemplazar `LocalAuth.kt` por `FirebaseAuth`

Archivo: `app/app/src/main/java/com/example/app/data/LocalAuth.kt`

Ahí mismo, dentro del objeto (puedes renombrarlo o dejarlo igual, pero si lo
renombras acuérdate de actualizar los `import` en `AuthScreens.kt` y
`MainActivity.kt`), cambia el contenido así:

```kotlin
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
```

No hace falta tocar `AuthScreens.kt` ni `MainActivity.kt`: siguen llamando
`LocalAuth.iniciarSesion(...)`, `LocalAuth.crearCuenta(...)`,
`LocalAuth.cerrarSesion()` y `LocalAuth.currentUser` exactamente igual.

---

## 2. Reemplazar `LocalRepo.kt` por `FirebaseDatabase`

Archivo: `app/app/src/main/java/com/example/app/data/LocalRepo.kt`

Aquí es donde vive toda la lógica de estudiantes, fichas médicas y
solicitudes. La estructura recomendada en Realtime Database es:

```
raiz
 ├─ usuarios / {idUsuario}                    -> Usuario (encargado_salud o padre)
 ├─ estudiantes / {carne}                     -> Estudiante
 ├─ padecimientos / {carne} / {id}             -> Padecimiento
 ├─ antecedentes / {carne} / {id}              -> Antecedente
 ├─ vacunas / {carne} / {id}                   -> Vacuna
 ├─ citas_medicas / {carne} / {id}             -> CitaMedica
 ├─ autorizaciones / {carne} / {id}            -> AutorizacionMedicamento
 └─ solicitudes / {idSolicitud}                -> Solicitud
```

Reemplaza el objeto completo por algo como esto (se muestra el patrón
completo para `usuarios`, `estudiantes` y `solicitudes`; el resto —
`padecimientos`, `antecedentes`, `vacunas`, `citas_medicas`,
`autorizaciones` — sigue exactamente el mismo patrón que ya tenía el
`FirebaseRepo.kt` original del proyecto):

```kotlin
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
        // Con pocos carnés por padre, lo más simple es traer todos y
        // filtrar en el cliente, igual que hace la versión local:
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

    // obtenerFichaMedica(): igual que el FirebaseRepo.kt original
    // (encadena las 5 lecturas de padecimientos/antecedentes/vacunas/
    // citas_medicas/autorizaciones para ese carné).

    fun actualizarDatosGenerales(estudiante: Estudiante, onDone: (Boolean) -> Unit = {}) {
        db.child("estudiantes").child(estudiante.carne).setValue(estudiante)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // -------------------- CRUD MÉDICO --------------------
    // Mismo patrón para las 5 tablas (guardarX / eliminarX), por ejemplo:

    fun guardarPadecimiento(p: Padecimiento, onDone: (Boolean) -> Unit = {}) {
        val id = p.idPadecimiento.ifBlank { db.child("padecimientos").child(p.carne).push().key ?: return }
        db.child("padecimientos").child(p.carne).child(id).setValue(p.copy(idPadecimiento = id))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun eliminarPadecimiento(carne: String, id: String, onDone: (Boolean) -> Unit = {}) {
        db.child("padecimientos").child(carne).child(id).removeValue()
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // Repite el mismo patrón de guardarPadecimiento/eliminarPadecimiento
    // para: guardarAntecedente/eliminarAntecedente (nodo "antecedentes"),
    // guardarVacuna/eliminarVacuna (nodo "vacunas"),
    // guardarCita/eliminarCita (nodo "citas_medicas"),
    // guardarAutorizacion/eliminarAutorizacion (nodo "autorizaciones").

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

    fun observarSolicitudesDePadre(idPadre: String, onResult: (List<Solicitud>) -> Unit) {
        db.child("solicitudes")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.children.mapNotNull { it.getValue(Solicitud::class.java) }
                        .filter { it.idPadre == idPadre })
                }
                override fun onCancelled(error: DatabaseError) { onResult(emptyList()) }
            })
    }

    fun aceptarSolicitud(idSolicitud: String, idEncargado: String, camposFinales: Map<String, String>, onDone: (Boolean) -> Unit = {}) {
        db.child("solicitudes").child(idSolicitud)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val solicitud = snapshot.getValue(Solicitud::class.java) ?: return onDone(false)

                    // Aplica los campos al nodo real (usa Mapeador.kt, igual
                    // que la versión local) según solicitud.tipo:
                    when (solicitud.tipo) {
                        TipoSolicitud.PADECIMIENTO -> guardarPadecimiento(
                            Mapeador.padecimientoDeMapa(solicitud.carneEstudiante, solicitud.idRegistroRelacionado, camposFinales)
                        )
                        // ... mismo patrón para ANTECEDENTE, VACUNA, CITA_MEDICA,
                        // AUTORIZACION_MEDICAMENTO y DATOS_GENERALES (copia el
                        // bloque "when" completo de la versión local de
                        // aceptarSolicitud()).
                    }

                    db.child("solicitudes").child(idSolicitud).setValue(
                        solicitud.copy(estado = EstadoSolicitud.ACEPTADA, campos = camposFinales, idEncargadoRevisor = idEncargado)
                    ).addOnCompleteListener { onDone(it.isSuccessful) }
                }
                override fun onCancelled(error: DatabaseError) { onDone(false) }
            })
    }

    // rechazarSolicitud(), devolverSolicitud() y reenviarSolicitud() siguen
    // el mismo patrón: leer el nodo, hacer .copy(...) con los nuevos
    // valores, y volver a hacer setValue().
}
```

> **Nota:** los métodos de Realtime Database son asíncronos (`addOnCompleteListener`,
> `ValueEventListener`), por eso todas las funciones ya reciben un
> `onResult` / `onDone` como parámetro — no hace falta cambiar nada en las
> pantallas (`EstudiantesScreen.kt`, `PadreScreen.kt`, `SolicitudesScreen.kt`),
> ellas ya están escritas esperando una respuesta por callback.

---

## 3. Reglas de seguridad (MUY IMPORTANTE)

Ahora mismo, en el código de la app, el permiso se controla solo en la UI:
por ejemplo, `EstudiantesScreen.kt` únicamente muestra los botones de
editar si `usuarioActual.esEncargadoSalud == true`. Eso evita que un padre
normal *vea* el botón, pero **no evita** que alguien con conocimientos
técnicos llame directamente a la base de datos y edite lo que quiera.

Por eso, cuando conectes Firebase real, en la consola ve a
**Realtime Database → Reglas** y pon algo como esto (ajusta los nombres de
nodo si los cambiaste):

```json
{
  "rules": {
    "usuarios": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid"
      }
    },

    "estudiantes": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },

    "padecimientos": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },
    "antecedentes": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },
    "vacunas": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },
    "citas_medicas": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },
    "autorizaciones": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud'"
    },

    "solicitudes": {
      ".read": "auth != null",
      "$idSolicitud": {
        ".write": "auth != null && (
          root.child('usuarios').child(auth.uid).child('rol').val() === 'encargado_salud' ||
          !data.exists() ||
          data.child('idPadre').val() === auth.uid
        )"
      }
    }
  }
}
```

Esto hace, del lado del servidor (no se puede saltar desde la app):

- Un **padre** solo puede leer/escribir su propio nodo en `usuarios/{uid}`.
- Solo un usuario cuyo `rol` sea `"encargado_salud"` puede escribir en
  `estudiantes`, `padecimientos`, `antecedentes`, `vacunas`,
  `citas_medicas` y `autorizaciones` (CRUD real).
- Cualquiera con sesión puede leer esos nodos (para que el padre pueda ver
  a su hijo/a); si quieres que un padre solo pueda leer los estudiantes que
  le corresponden, se necesita reestructurar un poco los datos (por
  ejemplo guardando el/los `idPadre` dentro de cada `Estudiante`) — dímelo
  si quieres que lo dejemos así de una vez.
- En `solicitudes/{id}`, un padre solo puede crear/editar una solicitud
  si es nueva (`!data.exists()`) o si él mismo es el dueño
  (`data.child('idPadre').val() === auth.uid`); solo el encargado de salud
  puede aceptar/rechazar/devolver cualquier solicitud.

---

## 4. Resumen de lo que hay que tocar

| Qué | Archivo | Qué hacer |
|---|---|---|
| Cuenta de Firebase | Consola de Firebase | Crear proyecto, activar Auth + Realtime Database |
| Config de la app | `app/app/google-services.json` | Descargarlo de la consola y ponerlo ahí |
| Login/registro | `data/LocalAuth.kt` | Reemplazar el contenido por el bloque de la sección 1 |
| Base de datos | `data/LocalRepo.kt` | Reemplazar el contenido por el bloque de la sección 2 |
| Permisos reales | Consola de Firebase → Realtime Database → Reglas | Pegar el JSON de la sección 3 |
| Pantallas (Auth, Estudiantes, Solicitudes, Padre) | *(ninguno)* | No hay que tocarlas: ya llaman a `LocalAuth`/`LocalRepo` por su nombre, y esos nombres no cambian |

Con eso, la app queda funcionando igual que ahora pero guardando todo de
verdad en Firebase, y con los permisos de cada rol aplicados también del
lado del servidor.

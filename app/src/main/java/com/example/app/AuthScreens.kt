package com.example.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.data.LocalAuth
import com.example.app.data.LocalRepo
import com.example.app.model.Roles
import com.example.app.model.Usuario

// ======================================================
// INICIO DE SESIÓN Y REGISTRO
//
// Solo existen dos roles al registrarse:
//  - Encargado de salud (CRUD completo, revisa solicitudes)
//  - Padre de familia (solo puede enviar solicitudes sobre
//    su(s) hijo(s); por eso el registro le pide el/los
//    carné(s) del estudiante)
//
// NOTA: por ahora el login/registro usa LocalAuth (en
// memoria, ver LocalAuth.kt) en vez de FirebaseAuth. Revisa
// GUIA_FIREBASE.md para ver exactamente qué cambiar cuando
// quieras conectar Firebase de verdad.
// ======================================================

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recordar by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "MedData",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = AzulOscuro
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tu información médica, organizada",
            color = Gris,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Bienvenido",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoOscuro
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Inicia sesión en tu cuenta",
                    color = Gris
                )

                Spacer(modifier = Modifier.height(25.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = recordar,
                        onCheckedChange = { recordar = it },
                        colors = CheckboxDefaults.colors(checkedColor = AzulOscuro)
                    )
                    Text(text = "Recuérdame", color = TextoOscuro)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        LocalAuth.iniciarSesion(email, password) { exito, _, error ->
                            if (exito) {
                                Toast.makeText(context, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                                onLogin()
                            } else {
                                Toast.makeText(context, error ?: "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Azul)
                ) {
                    Text(text = "INICIAR SESIÓN", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onRegister) {
                    Text(text = "¿No tienes cuenta? Crear una", color = Rosado)
                }
            }
        }
    }
}


// ======================================================
// REGISTRO
// ======================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    onRegistroExitoso: () -> Unit,
    onVolver: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmarPassword by remember { mutableStateOf("") }

    // Rol con el que se crea la cuenta. Por defecto "padre".
    var rolSeleccionado by remember { mutableStateOf(Roles.PADRE) }
    var menuRolAbierto by remember { mutableStateOf(false) }

    // Solo se pide y se usa si rolSeleccionado == Roles.PADRE.
    var carnesHijosTexto by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        TextButton(onClick = onVolver, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Volver")
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "Volver al inicio de sesión")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Crear cuenta",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoOscuro
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmarPassword,
                    onValueChange = { confirmarPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selector de rol: solo Encargado de Salud o Padre de familia.
                ExposedDropdownMenuBox(
                    expanded = menuRolAbierto,
                    onExpandedChange = { menuRolAbierto = it }
                ) {
                    OutlinedTextField(
                        value = if (rolSeleccionado == Roles.ENCARGADO_SALUD) "Encargado(a) de salud" else "Padre de familia",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuRolAbierto) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = menuRolAbierto,
                        onDismissRequest = { menuRolAbierto = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Padre de familia") },
                            onClick = {
                                rolSeleccionado = Roles.PADRE
                                menuRolAbierto = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Encargado(a) de salud") },
                            onClick = {
                                rolSeleccionado = Roles.ENCARGADO_SALUD
                                menuRolAbierto = false
                            }
                        )
                    }
                }

                // Solo se le pide el carné al Padre: es lo que conecta su
                // cuenta con la ficha de su hijo/a.
                if (rolSeleccionado == Roles.PADRE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = carnesHijosTexto,
                        onValueChange = { carnesHijosTexto = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Carné(s) de su(s) hijo(s)") },
                        placeholder = { Text("Ej: 2024015, 2024016") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Text(
                        text = "Si tiene más de un hijo en el colegio, separe los carnés con coma.",
                        color = Gris,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (nombre.isBlank() || email.isBlank() || password.isBlank() || confirmarPassword.isBlank()) {
                            Toast.makeText(context, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (password != confirmarPassword) {
                            Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (password.length < 6) {
                            Toast.makeText(context, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val carnesHijos = carnesHijosTexto
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        if (rolSeleccionado == Roles.PADRE && carnesHijos.isEmpty()) {
                            Toast.makeText(context, "Ingresa el carné de al menos un hijo/a", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        LocalAuth.crearCuenta(nombre, email, password) { exito, uid, error ->
                            if (exito && uid != null) {
                                val nuevoUsuario = Usuario(
                                    idUsuario = uid,
                                    nombre = nombre,
                                    correo = email,
                                    rol = rolSeleccionado,
                                    carnesHijos = if (rolSeleccionado == Roles.PADRE) carnesHijos else emptyList()
                                )

                                LocalRepo.guardarUsuario(uid, nuevoUsuario) {
                                    Toast.makeText(context, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show()
                                    onRegistroExitoso()
                                }
                            } else {
                                Toast.makeText(context, error ?: "Error al crear la cuenta", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rosado)
                ) {
                    Text(text = "CREAR CUENTA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

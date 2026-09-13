package com.example.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.app.data.LocalAuth
import com.example.app.data.LocalRepo
import com.example.app.data.SesionUsuario
import com.example.app.model.Usuario
import com.example.app.ui.EstudianteDetalleScreen
import com.example.app.ui.EstudiantesScreen
import com.example.app.ui.PadreScreen
import com.example.app.ui.SolicitudesScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MedDataApp()
        }
    }
}


// ======================================================
// APP PRINCIPAL
//
// Nota: usuario/appUsuario usan LocalAuth y LocalRepo (en
// memoria) en vez de FirebaseAuth/FirebaseDatabase. Revisa
// GUIA_FIREBASE.md para saber exactamente qué reemplazar
// cuando conectes Firebase de verdad.
// ======================================================

@Composable
fun MedDataApp() {

    val context = androidx.compose.ui.platform.LocalContext.current

    var usuario by remember { mutableStateOf(LocalAuth.currentUser) }

    var appUsuario by remember { mutableStateOf<Usuario?>(null) }

    var carneSeleccionado by remember { mutableStateOf("") }

    var pestañaPadreInicial by remember { mutableStateOf(0) }

    var pantalla by remember {
        mutableStateOf(if (usuario != null) "inicio" else "login")
    }

    LaunchedEffect(usuario) {
        val uid = usuario?.uid
        if (uid != null) {
            LocalRepo.obtenerUsuario(uid) { perfil -> appUsuario = perfil }
        } else {
            appUsuario = null
        }
    }

    fun cerrarSesion() {
        LocalAuth.cerrarSesion()
        usuario = null
        pantalla = "login"
        Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }

    when (pantalla) {

        "login" -> {
            LoginScreen(
                onLogin = {
                    usuario = LocalAuth.currentUser
                    pantalla = "inicio"
                },
                onRegister = { pantalla = "registro" }
            )
        }

        "registro" -> {
            RegistroScreen(
                onRegistroExitoso = {
                    usuario = LocalAuth.currentUser
                    pantalla = "inicio"
                },
                onVolver = { pantalla = "login" }
            )
        }

        "inicio" -> {
            val perfil = appUsuario

            if (perfil == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { cerrarSesion() }) {
                            Text("Cerrar sesión (si tarda demasiado)")
                        }
                    }
                }
            } else {
                // Cada rol ve opciones distintas en su pantalla de inicio.
                val opciones = if (perfil.esEncargadoSalud) {
                    listOf("Estudiantes", "Solicitudes pendientes")
                } else {
                    listOf("Mis hijos", "Enviar información", "Mis solicitudes")
                }

                HomeScreen(
                    opciones = opciones,
                    onSeleccionarOpcion = { opcion ->
                        when (opcion) {
                            "Estudiantes" -> pantalla = "estudiantes"
                            "Solicitudes pendientes" -> pantalla = "solicitudes"
                            "Mis hijos" -> { pestañaPadreInicial = 0; pantalla = "padre" }
                            "Enviar información" -> { pestañaPadreInicial = 1; pantalla = "padre" }
                            "Mis solicitudes" -> { pestañaPadreInicial = 2; pantalla = "padre" }
                        }
                    },
                    onCuenta = { pantalla = "cuenta" },
                    onCerrarSesion = { cerrarSesion() }
                )
            }
        }

        "estudiantes" -> {
            val perfil = appUsuario

            if (perfil == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EstudiantesScreen(
                    usuarioActual = perfil,
                    onVolver = { pantalla = "inicio" },
                    onSeleccionarEstudiante = { estudiante ->
                        carneSeleccionado = estudiante.carne
                        pantalla = "detalleEstudiante"
                    }
                )
            }
        }

        "detalleEstudiante" -> {
            val perfil = appUsuario

            if (perfil != null) {
                EstudianteDetalleScreen(
                    usuarioActual = perfil,
                    carne = carneSeleccionado,
                    onVolver = { pantalla = "estudiantes" }
                )
            }
        }

        "solicitudes" -> {
            val perfil = appUsuario

            if (perfil != null) {
                SolicitudesScreen(
                    usuarioActual = perfil,
                    onVolver = { pantalla = "inicio" }
                )
            }
        }

        "padre" -> {
            val perfil = appUsuario

            if (perfil != null) {
                PadreScreen(
                    usuarioActual = perfil,
                    pestañaInicial = pestañaPadreInicial,
                    onVolver = { pantalla = "inicio" }
                )
            }
        }

        "cuenta" -> {
            CuentaScreen(
                usuario = usuario,
                onInicio = { pantalla = "inicio" },
                onCerrarSesion = { cerrarSesion() }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    opciones: List<String>,
    onSeleccionarOpcion: (String) -> Unit,
    onCuenta: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var busqueda by remember { mutableStateOf("") }

    val resultados = opciones.filter { it.contains(busqueda, ignoreCase = true) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "MedData",
                    modifier = Modifier.padding(24.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AzulOscuro
                )

                HorizontalDivider(color = Gris)

                NavigationDrawerItem(
                    label = { Text("Inicio") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Inicio") },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Azul.copy(alpha = 0.25f),
                        selectedTextColor = AzulOscuro
                    )
                )

                NavigationDrawerItem(
                    label = { Text("Cuenta") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onCuenta()
                    },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Cuenta") }
                )

                Spacer(modifier = Modifier.weight(1f))

                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onCerrarSesion()
                    },
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión") }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = Fondo,
            topBar = {
                TopAppBar(
                    title = { Text(text = "Inicio", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menú")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = TextoOscuro,
                        navigationIconContentColor = AzulOscuro
                    )
                )
            }
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp)
            ) {

                Text(
                    text = "Bienvenido a MedData",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoOscuro
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(text = "¿Qué deseas consultar?", color = Gris)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = AzulOscuro
                        )
                    },
                    placeholder = { Text("Buscar...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                LazyColumn {
                    items(resultados) { opcion ->

                        Card(
                            onClick = { onSeleccionarOpcion(opcion) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(45.dp)
                                        .background(
                                            when (opcion) {
                                                "Estudiantes" -> Azul
                                                "Solicitudes pendientes" -> Rosado
                                                "Mis hijos" -> Azul
                                                "Enviar información" -> Beige
                                                "Mis solicitudes" -> Rosado
                                                else -> Gris
                                            },
                                            RoundedCornerShape(10.dp)
                                        )
                                )

                                Spacer(modifier = Modifier.width(15.dp))

                                Text(
                                    text = opcion,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextoOscuro
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CuentaScreen(
    usuario: SesionUsuario?,
    onInicio: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onInicio) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
            }

            Text(text = "Cuenta", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextoOscuro)
        }

        Spacer(modifier = Modifier.height(25.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(25.dp)) {

                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Cuenta",
                    modifier = Modifier.size(70.dp),
                    tint = Azul
                )

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = usuario?.nombre?.ifBlank { "Usuario" } ?: "Usuario",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextoOscuro
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(text = usuario?.correo ?: "Sin correo", color = Gris)

                Spacer(modifier = Modifier.height(25.dp))

                Button(
                    onClick = onCerrarSesion,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rosado)
                ) {
                    Text(text = "CERRAR SESIÓN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

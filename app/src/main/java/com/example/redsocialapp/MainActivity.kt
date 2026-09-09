package com.example.redsocialapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantallaPosts()
                }
            }
        }
    }
}

@Composable
fun PantallaPosts() {
    val db = remember { FirebaseFirestore.getInstance() }

    val posts = remember { mutableStateListOf<Post>() }

    // Guarda el último documento leído, para usarlo como "cursor" en la paginación
    var ultimoDocumento by remember { mutableStateOf<DocumentSnapshot?>(null) }

    // Indica si ya no hay más posts que cargar (llegamos al final)
    var hayMasPosts by remember { mutableStateOf(true) }

    var cargandoInicial by remember { mutableStateOf(false) }
    var cargandoMas by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // ---- Carga inicial: últimos 5 posts ----
    fun cargarNuevosPosts() {
        scope.launch {
            cargandoInicial = true
            mensajeError = null
            try {
                val resultado = db.collection("posts")
                    .orderBy("fecha", Query.Direction.DESCENDING)
                    .limit(5)
                    .get()
                    .await()

                posts.clear()
                for (doc in resultado.documents) {
                    val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                    if (post != null) posts.add(post)
                }

                // Guardamos el último documento visible como referencia para "cargar más"
                ultimoDocumento = resultado.documents.lastOrNull()

                // Si trajo menos de 5, ya no hay más para cargar
                hayMasPosts = resultado.documents.size == 5

            } catch (e: Exception) {
                mensajeError = "Error al cargar posts: ${e.message}"
            } finally {
                cargandoInicial = false
            }
        }
    }

    // ---- Cargar los siguientes 5, sin repetir los anteriores ----
    fun cargarMasPosts() {
        val cursor = ultimoDocumento ?: return

        scope.launch {
            cargandoMas = true
            mensajeError = null
            try {
                val resultado = db.collection("posts")
                    .orderBy("fecha", Query.Direction.DESCENDING)
                    .startAfter(cursor)   // <-- clave: empieza DESPUÉS del último que ya tenemos
                    .limit(5)
                    .get()
                    .await()

                for (doc in resultado.documents) {
                    val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                    if (post != null) posts.add(post)
                }

                if (resultado.documents.isNotEmpty()) {
                    ultimoDocumento = resultado.documents.last()
                }

                hayMasPosts = resultado.documents.size == 5

            } catch (e: Exception) {
                mensajeError = "Error al cargar más posts: ${e.message}"
            } finally {
                cargandoMas = false
            }
        }
    }

    // Carga automática al abrir la pantalla
    LaunchedEffect(Unit) {
        cargarNuevosPosts()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(
            text = "Feed de Posts",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (cargandoInicial) {
            CircularProgressIndicator()
        }

        mensajeError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Lista de posts ----
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(posts) { post ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = post.texto, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatearFecha(post),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- Botón "Cargar más" ----
        if (hayMasPosts) {
            Button(
                onClick = { cargarMasPosts() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargandoMas
            ) {
                if (cargandoMas) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Cargar más")
                }
            }
        } else {
            Text(
                text = "No hay más posts",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Función auxiliar para mostrar el Timestamp de forma legible
fun formatearFecha(post: Post): String {
    val fecha = post.fecha?.toDate() ?: return "Sin fecha"
    val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formato.format(fecha)
}
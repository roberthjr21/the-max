package co.themax.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
fun AiPreferences(store: AiSettings, onSaved: () -> Unit, onClose: () -> Unit) {
    var p by remember { mutableStateOf(store.provider) }
    var key by remember(p) { mutableStateOf("") }
    var erase by remember(p) { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(store.prompt) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Ajustes de IA") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Proveedor")
            Provider.values().forEach { item ->
                FilterChip(selected = item == p, onClick = { p = item }, label = { Text(item.label) })
            }
            OutlinedTextField(key, { key = it.take(2048); erase = false }, label = { Text("Clave de " + p.label) },
                placeholder = { Text(if (store.configured(p) && !erase) "Clave guardada" else "Pega tu clave") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (store.configured(p)) TextButton(onClick = { erase = !erase; key = "" }) {
                Text(if (erase) "Deshacer eliminación" else "Eliminar clave al guardar")
            }
            OutlinedTextField(prompt,{ prompt = it.take(2000) },label = { Text("Instrucciones de análisis") }, minLines = 3, maxLines = 7)
            TextButton(onClick = { prompt = DEFAULT_PROMPT }) { Text("Restablecer instrucciones") }
            if (error.isNotEmpty()) Text(error)
        }
    }, confirmButton = { Button(onClick = {
        try { store.save(p,key,prompt,erase); key = ""; onSaved() }
        catch (_: Exception) { error = "No se pudieron guardar los ajustes protegidos." }
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable
fun AiPanel(store: AiSettings, sample: List<AiLine>, total: Int, share: (Tile) -> Unit, settings: () -> Unit) {
    val p = store.provider
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AiResult?>(null) }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xffffe1e9))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Red flags con IA", style = MaterialTheme.typography.titleLarge)
            result?.let { r ->
                Text("${r.percent} %",style = MaterialTheme.typography.headlineLarge)
                Text("Intensidad estimada por ${p.label} en ${sample.size} mensajes.")
                if (r.findings.isEmpty()) Text("La IA no encontró señales en el fragmento.")
                Text(r.summary)
                r.findings.forEach { f -> Text("${f.signal}\nMensaje ${f.message} · ${sample[f.message-1].speaker}: «${f.example}»") }
                TextButton(onClick = { share(Tile("Red flags con IA",0xffffe1e9,detail = "${r.percent} % · ${r.findings.size} hallazgos",
                    note = "Estimación de ${p.label} sobre ${sample.size} mensajes.")) }) { Text("Compartir porcentaje sin citas") }
            }
            if (error.isNotEmpty()) Text(error)
            if (!store.configured(p)) TextButton(onClick = settings) { Text("Configurar IA opcional") }
            else Button(enabled = !busy && sample.isNotEmpty(), onClick = { preview = true }) {
                Text(if (busy) "Analizando…" else "Revisar fragmento con ${p.label}")
            }
        }
    }
    if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text("¿Enviar a ${p.label}?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enviarás los últimos ${sample.size} de $total mensajes válidos, con hasta 500 caracteres por mensaje, y tus instrucciones. Los remitentes aparecen como A y B. El texto puede contener datos personales. El proveedor puede cobrar por esta consulta.")
            Text("Instrucciones"); Text(store.prompt)
            sample.forEachIndexed { i,l -> Text("${i+1} · ${l.speaker}\n${l.text}") }
        }
    }, confirmButton = { Button(onClick = {
        if (!busy) {
            preview = false; busy = true; error = ""; result = null
            val instructions = store.prompt
            scope.launch {
                try {
                    result = withContext(Dispatchers.IO) {
                        val key = try { store.key(p) } catch (_: Exception) { throw AiFailure("Vuelve a guardar tu clave en Ajustes.") }
                        analyzeOnline(p,key,instructions,sample)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: AiFailure) { error = e.message.orEmpty() }
                catch (_: Exception) { error = "No se pudo completar la consulta. El análisis local sigue disponible." }
                finally { busy = false }
            }
        }
    }) { Text("Enviar y analizar") } }, dismissButton = { TextButton(onClick = { preview = false }) { Text("Cancelar") } })
}

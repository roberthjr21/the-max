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
    var automatic by remember(p) { mutableStateOf(store.automatic(p)) }
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
            Text("Analizar cada chat al importarlo")
            Switch(checked = automatic && !erase, onCheckedChange = { automatic = it },
                enabled = !erase && (key.isNotBlank() || store.configured(p)))
            Text("Al elegir quién eres, enviarás a ${p.label} hasta 80 mensajes recientes, 500 caracteres por mensaje y 12.000 en total, junto con tus instrucciones. El texto puede contener datos personales. Tu proveedor puede cobrar por cada chat. Puedes desactivarlo aquí.")
            OutlinedTextField(prompt,{ prompt = it.take(2000) },label = { Text("Instrucciones de análisis") }, minLines = 3, maxLines = 7)
            TextButton(onClick = { prompt = DEFAULT_PROMPT }) { Text("Restablecer instrucciones") }
            if (error.isNotEmpty()) Text(error)
        }
    }, confirmButton = { Button(onClick = {
        try { store.save(p,key,prompt,erase,automatic); key = ""; onSaved() }
        catch (_: Exception) { error = "No se pudieron guardar los ajustes protegidos." }
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable
fun AiPanel(store: AiSettings, sample: List<AiLine>, total: Int, state: AiRun, start: () -> Unit, share: (Tile) -> Unit) {
    val p = store.provider
    var preview by remember { mutableStateOf(false) }
    val busy = state is AiRun.Loading
    val result = (state as? AiRun.Done)?.result
    val error = (state as? AiRun.Failed)?.reason.orEmpty()
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
            if (busy) {
                LinearProgressIndicator()
                Text("Analizando el fragmento con ${p.label}…")
            } else if (sample.isEmpty()) Text("El chat no tiene mensajes con texto para analizar.")
            else if (result == null) Button(onClick = { if (store.automatic(p)) start() else preview = true }) {
                Text(if (error.isNotEmpty()) "Reintentar" else "Analizar con ${p.label}")
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
        preview = false
        start()
    }) { Text("Enviar y analizar") } }, dismissButton = { TextButton(onClick = { preview = false }) { Text("Cancelar") } })
}

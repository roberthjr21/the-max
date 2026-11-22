package co.themax.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

private val paper = Color(0xfffafaf7)
private val ink = Color(0xff102c34)
private val green = Color(0xff16894d)
private val demo by lazy { analyze("""21/11/26, 9:00 - Tú: Buenos días ☀️
21/11/26, 9:02 - Luna 💗: Holaaa
21/11/26, 9:03 - Luna 💗: ¿Vamos por un café?
21/11/26, 9:05 - Tú: ¡Vamos!
22/11/26, 10:15 - Luna 💗: Me encantó el café ✨""") }

class MainActivity : ComponentActivity() {
    private class ChatState : androidx.lifecycle.ViewModel() {
        var result by mutableStateOf<Report?>(null)
        var pending by mutableStateOf<Report?>(null)
        var me by mutableStateOf("")
    }
    private val chat by lazy {
        androidx.lifecycle.ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(type: Class<T>): T = ChatState() as T
        })[ChatState::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = green)) { Screen() } }
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var result by chat::result
        var pending by chat::pending
        var me by chat::me
        var hidden by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        var update by remember { mutableStateOf<Update?>(null) }
        var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
        fun work(operation: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                try { operation() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { notice = e.localizedMessage ?: "No se pudo completar la operación." }
                finally { busy = false }
            }
        }
        fun load(uri: Uri) = work {
            require(uri.scheme == "content") { "Selecciona un archivo desde el teléfono." }
            pending = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { analyze(readExport(it)) } ?: error("No pudimos abrir el archivo.")
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { load(it) } }
        fun install() {
            val apk = stagedPath?.let(::File) ?: return
            val uri = FileProvider.getUriForFile(this, "co.themax.chat.files", apk)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            work { if (packageManager.canRequestPackageInstalls()) install() else notice = "Puedes volver a intentar la actualización cuando quieras." }
        }
        LaunchedEffect(Unit) {
            @Suppress("DEPRECATION")
            val uri = intent.takeIf { it.action == Intent.ACTION_SEND }?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null && result == null && pending == null) load(uri)
        }
        val report = result ?: demo
        val self = if (result == null) demo.names.first() else me
        val other = report.names.first { it != self }
        val label = if (hidden) "Otra persona" else other
        Column(Modifier.fillMaxSize().background(paper).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.max_icon),null,Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text("The Max", color = green, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            if (result == null) Text("VISTA DE EJEMPLO", Modifier.align(Alignment.CenterHorizontally), color = green,
                style = MaterialTheme.typography.labelMedium)
            Text(buildAnnotatedString {
                append("Tú & "); withStyle(SpanStyle(color = Color(0xff349dd2))) { append(label) }
            }, Modifier.fillMaxWidth(), fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, color = ink)
            val cards = remember(report, self, label) { listOf(
                Tile("Mensajes",0xffa5f3db,"tú" to report.counts.getValue(self).toString(),label to report.counts.getValue(other).toString()),
                Tile("Tiempo medio de respuesta",0xffd4befb,"tú" to delayText(report.delays[self]),label to delayText(report.delays[other]),note="Tiempo entre turnos, con pausas menores a 8 horas."),
                Tile("Quién rompe el silencio",0xffffb6cf,"tú" to (report.starts[self] ?: 0).toString(),label to (report.starts[other] ?: 0).toString(),note="Cuenta el inicio del chat y las pausas de 8 horas o más."),
                Tile("Primer mensaje",0xffffe8a6,detail=report.first.body.trim().take(200),note=if (report.first.sender == self) "Lo enviaste tú" else "Lo envió $label"),
                Tile("Su día más activo",0xffffd8c3,detail=report.peak),
                Tile("Palabras favoritas",0xffbce5fb,detail=report.words.joinToString(" · ").ifEmpty { "Sin palabras frecuentes" },note=report.emoji?.let { "Emoji más usado: $it" } ?: "")
            ) + insightCards(report,self,other,label) }
            cards.forEachIndexed { i, tile ->
                TileView(tile, if (i == 0) ({
                    if (result == null) picker.launch(arrayOf("text/plain","application/zip","application/x-zip-compressed","application/octet-stream"))
                    else work { shareCard(this@MainActivity,tile) }
                }) else null, if (result == null) "Importar mi chat" else "Compartir tarjeta",
                    if (result != null && i != 0) ({ work { shareCard(this@MainActivity,tile) } }) else null)
            }
            if (result != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hidden,{ hidden = it }); Text("Ocultar nombre del contacto")
                }
                TextButton(onClick = { picker.launch(arrayOf("*/*")) },enabled = !busy) { Text("Importar otro chat") }
            } else Text("En WhatsApp, exporta un chat individual sin multimedia. El análisis se hace en este teléfono.",color = ink)
            OutlinedButton(onClick = { work {
                val available = withContext(Dispatchers.IO) { latest() }
                if (available.code > BuildConfig.VERSION_CODE) update = available
                else notice = "Tienes la versión más reciente."
            } },enabled = !busy,modifier = Modifier.fillMaxWidth()) { Text("Buscar actualización · v${BuildConfig.VERSION_NAME}") }
            Spacer(Modifier.height(12.dp))
        }
        if (busy) AlertDialog(onDismissRequest = {},title = { Text("Un momento…") },text = { LinearProgressIndicator() },confirmButton = {})
        pending?.let { candidate -> AlertDialog(onDismissRequest = { pending = null },title = { Text("¿Cuál de los dos eres tú?") },
            text = { Column { candidate.names.forEach { person -> TextButton(onClick = {
                me = person; result = candidate; pending = null
            }) { Text(person) } } } },confirmButton = {}) }
        update?.let { available -> AlertDialog(onDismissRequest = { update = null },title = { Text("The Max ${available.name}") },
            text = { Text("Hay una versión nueva. Android te pedirá confirmar la instalación.") },
            confirmButton = { TextButton(onClick = { update = null; work {
                stagedPath = withContext(Dispatchers.IO) { downloadUpdate(this@MainActivity,available).path }
                if (packageManager.canRequestPackageInstalls()) install()
                else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } }) { Text("Descargar e instalar") } },dismissButton = { TextButton(onClick = { update = null }) { Text("Después") } }) }
        notice?.let { AlertDialog(onDismissRequest = { notice = null },text = { Text(it) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Entendido") } }) }
    }
}
private fun delayText(n: Long?) = when (n) { null -> "Sin datos"; 0L -> "<1 min"; else -> "$n min" }
data class Tile(val title: String,val tint: Long,val left: Pair<String,String>? = null,val right: Pair<String,String>? = null,
    val detail: String = "",val note: String = "")

@Composable
private fun TileView(tile: Tile, action: (() -> Unit)?, actionLabel: String, share: (() -> Unit)?) {
    val fill = Color(tile.tint)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(fill,fill.copy(alpha=.83f))), RoundedCornerShape(25.dp))
        .padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tile.title,fontWeight = FontWeight.ExtraBold,fontSize = 19.sp,color = ink)
        if (tile.left != null && tile.right != null) Row(Modifier.fillMaxWidth()) {
            listOf(tile.left,tile.right).forEachIndexed { i, item ->
                Column(Modifier.weight(1f),horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                    Text(item.first,color = if (i == 0) Color(0xff007d78) else Color(0xffa52262),fontWeight = FontWeight.Bold,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                    Text(item.second,fontWeight = FontWeight.Black,fontSize = 34.sp,lineHeight = 38.sp,color = ink)
                }
            }
        } else Text(tile.detail,fontWeight = FontWeight.Bold,fontSize = 23.sp,color = ink)
        if (tile.note.isNotEmpty()) Text(tile.note,color = ink,fontSize = 13.sp)
        if (action != null) Button(onClick = action,modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
        if (share != null) TextButton(onClick = share) { Text("Compartir tarjeta",color = ink) }
    }
}

package co.themax.chat

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

private const val origin = "https://raw.githubusercontent.com/roberthjr21/the-max/main/downloads/"
data class Update(val code: Int, val name: String, val digest: String)
private fun openAsset(name: String) = URL(origin + name).openConnection().apply {
    connectTimeout = 15000
    readTimeout = 30000
    useCaches = false
}.getInputStream()

fun latest(): Update {
    val metadata = JSONObject(openAsset("latest.json").bufferedReader().use { it.readText() })
    return Update(metadata.getInt("versionCode"), metadata.getString("versionName"), metadata.getString("sha256"))
}

@Suppress("DEPRECATION")
fun downloadUpdate(context: Context, update: Update): File {
    require(update.code > BuildConfig.VERSION_CODE)
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val staged = File.createTempFile("new-", ".apk", directory)
    try {
        val hash = MessageDigest.getInstance("SHA-256")
        openAsset("The-Max-${update.code}.apk").use { input ->
            staged.outputStream().use { out ->
                val buffer = ByteArray(32768)
                var total = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size
                    require(total <= 100 * 1024 * 1024)
                    hash.update(buffer, 0, size)
                    out.write(buffer, 0, size)
                }
            }
        }
        require(hash.digest().joinToString("") { "%02x".format(it) } == update.digest) { "La descarga está incompleta o dañada." }
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(staged.path, PackageManager.GET_SIGNATURES)
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        require(archive != null && archive.packageName == current.packageName && archive.versionCode == update.code &&
            !current.signatures.isNullOrEmpty() && archive.signatures?.toSet() == current.signatures?.toSet()) { "La firma o la versión del APK no coincide." }
        val target = File(directory, "verified-${update.code}.apk")
        check(staged.renameTo(target))
        return target
    } finally { staged.delete() }
}

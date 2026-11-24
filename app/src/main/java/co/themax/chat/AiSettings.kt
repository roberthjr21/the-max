package co.themax.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class Provider(val label: String) { OPENAI("OpenAI"), ANTHROPIC("Anthropic"), GEMINI("Gemini") }

class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("analysis_ai", Context.MODE_PRIVATE)
    val provider get() = Provider.values().firstOrNull { it.name == prefs.getString("provider", null) } ?: Provider.OPENAI
    val prompt get() = prefs.getString("prompt", null) ?: DEFAULT_PROMPT
    fun configured(p: Provider) = prefs.contains(p.name)

    private fun secret(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "themax.ai.credentials.v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun save(p: Provider, replacement: String, instructions: String, erase: Boolean) {
        require(instructions.length <= 2000)
        val edit = prefs.edit().putString("provider", p.name).putString("prompt", instructions.ifBlank { DEFAULT_PROMPT })
        if (erase) edit.remove(p.name)
        else if (replacement.isNotBlank()) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret(true))
            cipher.updateAAD(p.name.toByteArray())
            val data = cipher.iv + cipher.doFinal(replacement.trim().toByteArray())
            edit.putString(p.name, Base64.encodeToString(data, Base64.NO_WRAP))
        }
        check(edit.commit())
    }
    fun key(p: Provider): String {
        val data = Base64.decode(requireNotNull(prefs.getString(p.name, null)), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(false), GCMParameterSpec(128, data, 0, 12))
        cipher.updateAAD(p.name.toByteArray())
        return String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }
}

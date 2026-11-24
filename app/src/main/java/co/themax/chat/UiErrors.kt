package co.themax.chat

internal class ChatError(message: String) : IllegalArgumentException(message)
internal fun displayError(error: Exception): String = when (error) {
    is ChatError -> error.message ?: "No pudimos leer el chat."
    is java.net.SocketTimeoutException -> "La conexión tardó demasiado. Inténtalo de nuevo."
    is java.net.UnknownHostException -> "No pudimos conectar. Revisa tu conexión a internet."
    is SecurityException -> "Falta permiso para completar esta operación."
    is java.io.IOException -> "No pudimos leer o descargar el archivo. Inténtalo de nuevo."
    else -> "No se pudo completar la operación. Inténtalo de nuevo."
}

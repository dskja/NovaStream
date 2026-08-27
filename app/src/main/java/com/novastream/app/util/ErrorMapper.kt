package com.novastream.app.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Zentralisierte Fehlerbehandlung - wandelt technische Exceptions in
 * benutzerfreundliche deutsche Fehlermeldungen um.
 */
object ErrorMapper {

    /** Wandelt eine Exception in eine benutzerfreundliche Fehlermeldung um. */
    fun toUserMessage(error: Throwable): String {
        return when (error) {
            is SocketTimeoutException -> "Zeitüberschreitung beim Laden. Bitte überprüfe deine Internetverbindung."
            is UnknownHostException -> "Server nicht erreichbar. Überprüfe deine Internetverbindung oder versuche es später erneut."
            is SSLException -> "Sichere Verbindung konnte nicht hergestellt werden. Versuche es erneut."
            is IOException -> "Netzwerkfehler: ${error.message ?: "Unbekannter Verbindungsfehler"}"
            is IllegalStateException -> "Die Website-Struktur hat sich geändert. Ein Update könnte helfen."
            is IllegalArgumentException -> "Ungültige Anfrage: ${error.message ?: ""}"
            else -> {
                val msg = error.message
                if (msg.isNullOrBlank()) "Ein unbekannter Fehler ist aufgetreten"
                else "Fehler: $msg"
            }
        }
    }

    /** True wenn der Fehler netzwerkbezogen ist (retry könnte helfen). */
    fun isNetworkError(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
               error is UnknownHostException ||
               error is SSLException ||
               error is IOException
    }

    /** True wenn der Fehler temporär ist und ein Retry sinnvoll ist. */
    fun isRetryable(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
               error is UnknownHostException ||
               error is IOException
    }
}

package com.novastream.app.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import java.net.ConnectException

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
            is SSLHandshakeException -> "Sichere Verbindung fehlgeschlagen. Möglicherweise ist das Gerät-Datum falsch."
            is SSLException -> "Sichere Verbindung konnte nicht hergestellt werden. Versuche es erneut."
            is ConnectException -> "Verbindung zum Server abgelehnt. Versuche es später erneut."
            is IOException -> "Netzwerkfehler: ${error.message ?: "Unbekannter Verbindungsfehler"}"
            is IllegalStateException -> "Die Website-Struktur hat sich geändert. Ein Update könnte helfen."
            is IllegalArgumentException -> "Ungültige Anfrage: ${error.message ?: ""}"
            is NullPointerException -> "Ein interner Fehler ist aufgetreten. Versuche es erneut."
            is IndexOutOfBoundsException -> "Daten konnten nicht verarbeitet werden. Versuche es erneut."
            is SecurityException -> "Zugriff verweigert. Überprüfe deine Berechtigungen."
            is kotlinx.coroutines.TimeoutCancellationException -> "Zeitüberschreitung. Der Server braucht zu lange zum Antworten."
            is retrofit2.HttpException -> {
                when (error.code()) {
                    403 -> "Zugriff verweigert (403). Die Website blockiert möglicherweise die App."
                    404 -> "Seite nicht gefunden (404). Der Inhalt wurde möglicherweise entfernt."
                    429 -> "Zu viele Anfragen (429). Bitte warte einen Moment und versuche es erneut."
                    500, 502, 503 -> "Server-Fehler (${error.code()}). Versuche es später erneut."
                    else -> "HTTP-Fehler ${error.code()}: ${error.message()}"
                }
            }
            is com.google.gson.JsonSyntaxException -> "Daten konnten nicht gelesen werden. Versuche es erneut."
            is org.jsoup.HttpStatusException -> "HTTP-Fehler ${error.statusCode}: ${error.message}"
            is org.jsoup.UnsupportedMimeTypeException -> "Nicht unterstützter Inhaltstyp. Versuche es erneut."
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
               error is ConnectException ||
               error is IOException
    }

    /** True wenn der Fehler temporär ist und ein Retry sinnvoll ist. */
    fun isRetryable(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
               error is UnknownHostException ||
               error is ConnectException ||
               error is IOException ||
               error is kotlinx.coroutines.TimeoutCancellationException ||
               (error is retrofit2.HttpException && error.code() in setOf(429, 500, 502, 503))
    }

    /** True wenn der Fehler permanent ist (kein Retry sinnvoll). */
    fun isPermanent(error: Throwable): Boolean {
        return error is IllegalStateException ||
               error is IllegalArgumentException ||
               error is NullPointerException ||
               error is IndexOutOfBoundsException ||
               error is SecurityException
    }

    /** Gibt einen kurzen Error-Typ zurück (für Logging/Analytics). */
    fun errorCategory(error: Throwable): String {
        return when (error) {
            is SocketTimeoutException -> "TIMEOUT"
            is UnknownHostException -> "DNS"
            is SSLException -> "SSL"
            is ConnectException -> "CONNECT"
            is IOException -> "NETWORK"
            is IllegalStateException -> "PARSE"
            is IllegalArgumentException -> "VALIDATION"
            is NullPointerException -> "NPE"
            is IndexOutOfBoundsException -> "INDEX"
            is SecurityException -> "SECURITY"
            is kotlinx.coroutines.TimeoutCancellationException -> "COROUTINE_TIMEOUT"
            is retrofit2.HttpException -> "HTTP_${error.code()}"
            is com.google.gson.JsonSyntaxException -> "JSON"
            is org.jsoup.HttpStatusException -> "JSOUP_HTTP_${error.statusCode}"
            is org.jsoup.UnsupportedMimeTypeException -> "MIME"
            else -> "UNKNOWN"
        }
    }
}

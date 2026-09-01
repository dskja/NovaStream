package com.novastream.app.util

import android.content.Context
import com.novastream.app.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Zentralisierte Fehlerbehandlung – wandelt technische Exceptions in
 * benutzerfreundliche, lokalisierte Fehlermeldungen um.
 */
object ErrorMapper {

    /** Wandelt eine Exception in eine benutzerfreundliche Fehlermeldung um (App-Locale). */
    fun toUserMessage(error: Throwable): String {
        val ctx = AppContext.getOrNull()
        return if (ctx != null) toUserMessage(ctx, error) else toUserMessageEnglishFallback(error)
    }

    fun toUserMessage(context: Context, error: Throwable): String {
        return when (error) {
            is SocketTimeoutException ->
                context.getString(R.string.error_timeout)
            is UnknownHostException ->
                context.getString(R.string.error_unknown_host)
            is SSLHandshakeException ->
                context.getString(R.string.error_ssl_handshake)
            is SSLException ->
                context.getString(R.string.error_ssl)
            is ConnectException ->
                context.getString(R.string.error_connect)
            is IOException -> context.getString(
                R.string.error_io,
                error.message ?: context.getString(R.string.error_io_unknown)
            )
            is IllegalStateException ->
                context.getString(R.string.error_site_structure)
            is IllegalArgumentException -> context.getString(
                R.string.error_invalid_request,
                error.message ?: ""
            )
            is NullPointerException ->
                context.getString(R.string.error_internal)
            is IndexOutOfBoundsException ->
                context.getString(R.string.error_data_processing)
            is SecurityException ->
                context.getString(R.string.error_security)
            is kotlinx.coroutines.TimeoutCancellationException ->
                context.getString(R.string.error_coroutine_timeout)
            is kotlinx.coroutines.CancellationException ->
                context.getString(R.string.error_cancelled)
            is retrofit2.HttpException -> when (error.code()) {
                403 -> context.getString(R.string.error_http_403)
                404 -> context.getString(R.string.error_http_404)
                429 -> context.getString(R.string.error_http_429)
                500, 502, 503 -> context.getString(R.string.error_http_server, error.code())
                else -> context.getString(R.string.error_http_generic, error.code(), error.message())
            }
            is com.google.gson.JsonSyntaxException ->
                context.getString(R.string.error_json)
            is org.jsoup.HttpStatusException ->
                context.getString(R.string.error_jsoup_http, error.statusCode, error.message)
            is org.jsoup.UnsupportedMimeTypeException ->
                context.getString(R.string.error_mime)
            else -> {
                val msg = error.message
                if (msg.isNullOrBlank()) context.getString(R.string.error_unknown)
                else context.getString(R.string.error_with_message, msg)
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

    private fun toUserMessageEnglishFallback(error: Throwable): String = when (error) {
        is SocketTimeoutException -> "Loading timed out. Check your internet connection."
        is UnknownHostException -> "Server unreachable. Check your connection or try again later."
        is SSLHandshakeException -> "Secure connection failed. Your device date may be incorrect."
        is SSLException -> "Could not establish a secure connection. Try again."
        is ConnectException -> "Connection refused. Try again later."
        is IOException -> "Network error: ${error.message ?: "Unknown connection error"}"
        is IllegalStateException -> "The site structure has changed. An update may help."
        is IllegalArgumentException -> "Invalid request: ${error.message ?: ""}"
        is NullPointerException -> "An internal error occurred. Try again."
        is IndexOutOfBoundsException -> "Could not process data. Try again."
        is SecurityException -> "Access denied. Check your permissions."
        is kotlinx.coroutines.TimeoutCancellationException -> "Timed out. The server is taking too long."
        is kotlinx.coroutines.CancellationException -> "Operation cancelled."
        is retrofit2.HttpException -> when (error.code()) {
            403 -> "Access denied (403). The site may be blocking the app."
            404 -> "Page not found (404). Content may have been removed."
            429 -> "Too many requests (429). Wait a moment and try again."
            500, 502, 503 -> "Server error (${error.code()}). Try again later."
            else -> "HTTP error ${error.code()}: ${error.message()}"
        }
        is com.google.gson.JsonSyntaxException -> "Could not read data. Try again."
        is org.jsoup.HttpStatusException -> "HTTP error ${error.statusCode}: ${error.message}"
        is org.jsoup.UnsupportedMimeTypeException -> "Unsupported content type. Try again."
        else -> {
            val msg = error.message
            if (msg.isNullOrBlank()) "An unknown error occurred" else "Error: $msg"
        }
    }
}

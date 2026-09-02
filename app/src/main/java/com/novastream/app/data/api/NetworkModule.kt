package com.novastream.app.data.api

import com.novastream.app.data.model.NovaStreamConfig
import com.novastream.app.util.MediaUrls
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Stellt OkHttp + Retrofit bereit.
 * Nutzt DNS-over-HTTPS (Cloudflare 1.1.1.1 + Google 8.8.8.8 Fallback) um ISP-DNS-Blockaden zu umgehen.
 */
object NetworkModule {

    /** Upgrade http→https only for hosts that are not cleartext-allowed in network security config. */
    private val httpsUpgradeInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (url.scheme == "http") {
            val host = url.host
            if (!MediaUrls.isCleartextAllowedHost(host)) {
                val upgraded = request.newBuilder()
                    .url(url.newBuilder().scheme("https").build())
                    .build()
                return@Interceptor chain.proceed(upgraded)
            }
        }
        chain.proceed(request)
    }

    private val userAgentInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")

        if (original.header("Referer") == null) {
            // Scheme-aware Referer (HTTP IP mirrors must not get https://… Referer)
            builder.header("Referer", "${original.url.scheme}://${original.url.host}/")
        }

        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.novastream.app.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
    }

    /** Retry-Interceptor: wiederholt fehlgeschlagene Requests bis zu 2 Mal mit kurzem Backoff. */
    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        var lastException: Exception? = null
        while (attempt < 3) {
            try {
                val response = chain.proceed(chain.request())
                if (response.isSuccessful || response.code < 500) return@Interceptor response
                response.close()
            } catch (e: Exception) {
                lastException = e
                if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                    // Retry bei Timeout/Connect-Fehler
                } else {
                    throw e
                }
            }
            attempt++
        }
        throw lastException ?: java.io.IOException("Max retries exceeded")
    }

    private fun buildDispatcher(): Dispatcher = Dispatcher().apply {
        maxRequestsPerHost = 6
    }

    /**
     * Session cookies shared with Android WebView CookieManager (BetterStreamflix NetworkClient pattern).
     * Cloudflare / DDoS-Guard cookies obtained in WebView become available to OkHttp and vice versa.
     */
    private val cookieJar: CookieJar = object : CookieJar {
        private val memory = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            memory[url.host] = cookies
            try {
                val cm = android.webkit.CookieManager.getInstance()
                cookies.forEach { cookie ->
                    cm.setCookie(url.toString(), cookie.toString())
                }
                cm.flush()
            } catch (_: Throwable) {
                // CookieManager unavailable in some unit-test environments
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val byName = linkedMapOf<String, Cookie>()
            memory[url.host].orEmpty().forEach { byName[it.name] = it }
            try {
                val cookieString = android.webkit.CookieManager.getInstance().getCookie(url.toString())
                if (!cookieString.isNullOrBlank()) {
                    cookieString.split(";").forEach { part ->
                        Cookie.parse(url, part.trim())?.let { byName[it.name] = it }
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
            return byName.values.toList()
        }
    }

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .dns(dohDns)
            .cookieJar(cookieJar)
            .dispatcher(buildDispatcher())
            .addInterceptor(httpsUpgradeInterceptor)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(NovaStreamConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NovaStreamConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(NovaStreamConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.MODERN_TLS,
                    okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                    okhttp3.ConnectionSpec.CLEARTEXT
                )
            )
            .retryOnConnectionFailure(true)

    /**
     * DNS-over-HTTPS via Cloudflare (1.1.1.1) mit Google (8.8.8.8) Fallback.
     * Umgeht ISP-DNS-Blockaden (z.B. O2/Telefonica cuii-Sperre).
     * Bootstrap-IPs stellen sicher, dass DoH auch ohne funktionierenden System-DNS erreichbar ist.
     *
     * DNS Resolution wird lazy gemacht um blocking auf dem main thread zu vermeiden.
     */
    private val dohDnsRef = AtomicReference<Dns?>(null)

    private val dohDns: Dns by lazy {
        dohDnsRef.get() ?: run {
            val resolved = resolveDohDns()
            dohDnsRef.set(resolved)
            resolved
        }
    }

    /** Shared DoH HttpClient - verhindert mehrfache Thread-Pool-Erstellung. */
    private val dohClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveDohDns(): Dns {
        // Bootstrap IPs für Cloudflare + Google
        val bootstrap = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4").mapNotNull {
            try { java.net.InetAddress.getByName(it) } catch (_: Exception) { null }
        }
        if (bootstrap.isEmpty()) {
            if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("NetworkModule", "All bootstrap DNS failed, falling back to system DNS")
            return Dns.SYSTEM
        }

        // Primär: Cloudflare DoH
        val cloudflareBootstrap = bootstrap.filter { it.hostAddress == "1.1.1.1" || it.hostAddress == "1.0.0.1" }
        if (cloudflareBootstrap.isNotEmpty()) {
            try {
                return DnsOverHttps.Builder()
                    .client(dohClient)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(cloudflareBootstrap)
                    .includeIPv6(true)
                    .build()
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("NetworkModule", "Cloudflare DoH failed, trying Google", e)
            }
        }

        // Fallback: Google DoH
        val googleBootstrap = bootstrap.filter { it.hostAddress == "8.8.8.8" || it.hostAddress == "8.8.4.4" }
        if (googleBootstrap.isNotEmpty()) {
            try {
                return DnsOverHttps.Builder()
                    .client(dohClient)
                    .url("https://dns.google/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(googleBootstrap)
                    .includeIPv6(true)
                    .build()
            } catch (e: Exception) {
                if (com.novastream.app.BuildConfig.DEBUG) android.util.Log.w("NetworkModule", "Google DoH failed, using system DNS", e)
            }
        }

        return Dns.SYSTEM
    }

    val okHttpClient: OkHttpClient by lazy {
        baseClientBuilder()
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .build()
    }

    /** Separater Client für Coil-Bilder mit eigenem Connection-Pool. */
    val imageOkHttpClient: OkHttpClient by lazy {
        baseClientBuilder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(NovaStreamConfig.BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    val novaStreamApi: NovaStreamApi by lazy {
        retrofit.create(NovaStreamApi::class.java)
    }
}

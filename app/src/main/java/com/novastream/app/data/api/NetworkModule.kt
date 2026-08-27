package com.novastream.app.data.api

import com.novastream.app.data.model.NovaStreamConfig
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Stellt OkHttp + Retrofit bereit.
 * Nutzt DNS-over-HTTPS (Cloudflare 1.1.1.1 + Google 8.8.8.8 Fallback) um ISP-DNS-Blockaden zu umgehen.
 */
object NetworkModule {

    private val userAgentInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", NovaStreamConfig.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")

        if (original.header("Referer") == null) {
            val host = original.url.host
            builder.header("Referer", "https://$host/")
        }

        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.novastream.app.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
    }

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
                    .client(
                        OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build()
                    )
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
                    .client(
                        OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build()
                    )
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
        OkHttpClient.Builder()
            .dns(dohDns)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)  // Total call timeout - prevents infinite hangs
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))  // 5 connections, 5 min keep-alive
            .retryOnConnectionFailure(true)
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

package com.novastream.app.data.api

import com.novastream.app.data.model.SerienStreamConfig
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Stellt OkHttp + Retrofit bereit.
 * Nutzt DNS-over-HTTPS (Cloudflare 1.1.1.1) um ISP-DNS-Blockaden zu umgehen.
 */
object NetworkModule {

    private val userAgentInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", SerienStreamConfig.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .header("Referer", SerienStreamConfig.BASE_URL + "/")
            .build()
        chain.proceed(req)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * DNS-over-HTTPS via Cloudflare (1.1.1.1).
     * Umgeht ISP-DNS-Blockaden (z.B. O2/Telefonica cuii-Sperre).
     * Bootstrap-IPs 1.1.1.1 / 1.0.0.1 stellen sicher, dass DoH auch ohne
     * funktionierenden System-DNS erreichbar ist.
     */
    private val dohDns: Dns = DnsOverHttps.Builder()
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        )
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            listOf(
                java.net.InetAddress.getByName("1.1.1.1"),
                java.net.InetAddress.getByName("1.0.0.1")
            )
        )
        .includeIPv6(true)
        .build()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dohDns)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(SerienStreamConfig.BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    val serienStreamApi: SerienStreamApi by lazy {
        retrofit.create(SerienStreamApi::class.java)
    }
}

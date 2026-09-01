package com.novastream.app.data.iptv

/**
 * M3U playlist parser (v14) — ported from BetterStreamflix PlutoTvDeProvider / UserM3uProvider.
 */
object M3uParser {

    fun parse(m3uRaw: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = m3uRaw.lines()

        var curName = ""
        var curLogo = ""
        var curGroup = ""
        var curTvgId = ""
        var curUA: String? = null
        var curRef: String? = null

        for (line in lines) {
            val t = line.trim()
            when {
                t.startsWith("#EXTINF") -> {
                    curName = t.substringAfterLast(",").trim()
                    curLogo = Regex("""tvg-logo="([^"]+)"""").find(t)?.groupValues?.get(1).orEmpty()
                    curGroup = Regex("""group-title="([^"]+)"""").find(t)?.groupValues?.get(1).orEmpty()
                    curTvgId = Regex("""tvg-id="([^"]+)"""").find(t)?.groupValues?.get(1).orEmpty()
                    curUA = Regex("""http-user-agent="([^"]+)"""").find(t)?.groupValues?.get(1)
                    curRef = Regex("""http-referrer="([^"]+)"""").find(t)?.groupValues?.get(1)
                }
                t.startsWith("#EXTVLCOPT:") -> {
                    if (t.contains("http-user-agent=")) curUA = t.substringAfter("http-user-agent=").trim()
                    if (t.contains("http-referrer=")) curRef = t.substringAfter("http-referrer=").trim()
                }
                t.startsWith("http") -> {
                    if (curName.isNotEmpty()) {
                        val id = android.util.Base64.encodeToString(
                            "${t}|${curName}|${curLogo}|${curUA.orEmpty()}|${curRef.orEmpty()}".toByteArray(),
                            android.util.Base64.NO_WRAP
                        )
                        channels.add(
                            IptvChannel(
                                id = id,
                                name = curName,
                                logoUrl = curLogo.takeIf { it.isNotBlank() },
                                group = curGroup.takeIf { it.isNotBlank() },
                                streamUrl = t,
                                userAgent = curUA,
                                referer = curRef,
                                tvgId = curTvgId.takeIf { it.isNotBlank() }
                            )
                        )
                        curName = ""
                        curLogo = ""
                        curGroup = ""
                        curTvgId = ""
                        curUA = null
                        curRef = null
                    }
                }
            }
        }
        return channels
    }

    fun decodeChannelId(id: String): IptvChannel? {
        return try {
            val decoded = String(android.util.Base64.decode(id, android.util.Base64.DEFAULT))
            val parts = decoded.split("|")
            if (parts.size < 2) return null
            IptvChannel(
                id = id,
                name = parts[1],
                logoUrl = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                streamUrl = parts[0],
                userAgent = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                referer = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }
}

package com.novastream.app.data.iptv

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Basic XMLTV EPG parser (v14).
 */
object XmlTvEpgParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(xml: String): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var eventType = parser.eventType
        var currentChannel = ""
        var title = ""
        var desc = ""
        var start = 0L
        var stop = 0L

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        currentChannel = parser.getAttributeValue(null, "channel").orEmpty()
                        start = parseXmlTvDate(parser.getAttributeValue(null, "start"))
                        stop = parseXmlTvDate(parser.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                    }
                    "title" -> if (parser.depth > 1) title = parser.nextText()
                    "desc" -> if (parser.depth > 1) desc = parser.nextText()
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && currentChannel.isNotBlank()) {
                        programs.add(
                            EpgProgram(
                                channelId = currentChannel,
                                title = title,
                                description = desc.takeIf { it.isNotBlank() },
                                startMs = start,
                                endMs = stop
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }
        return programs
    }

    fun nowPlaying(programs: List<EpgProgram>, channelId: String, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        programs.filter { it.channelId == channelId && nowMs in it.startMs..it.endMs }
            .maxByOrNull { it.startMs }

    private fun parseXmlTvDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            // XMLTV format: 20260101120000 +0000
            val normalized = raw.replace(" ", " ")
            dateFormat.parse(normalized)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

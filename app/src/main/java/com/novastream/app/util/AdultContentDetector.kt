package com.novastream.app.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern

/**
 * Detects age-restricted (18+) content from HTML badges and rating metadata.
 * Returns true (adult), false (explicitly kid-safe rating), or null (unknown).
 */
object AdultContentDetector {

    private val adultPatterns = listOf(
        Pattern.compile("""\bFSK\s*18\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bab\s*18\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\b18\s*\+"""),
        Pattern.compile("""\b18\s*\+\b"""),
        Pattern.compile("""\bNC-?17\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bTV-?MA\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bTV-?14\b.*\b(?:violence|sexual|language)\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bR[- ]rated\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bRated\s+R\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bBBFC\s*18\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bkeine\s*jugendfreigabe\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\badults?\s*only\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bma[- ]?rating\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bage[- ]?restriction\b.*\b18\b""", Pattern.CASE_INSENSITIVE)
    )

    private val kidsPatterns = listOf(
        Pattern.compile("""\bFSK\s*0\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bFSK\s*6\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bFSK\s*12\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bab\s*6\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bTV-?Y7?\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bTV-?G\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bTV-?PG\b""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""\bPG-?13\b""", Pattern.CASE_INSENSITIVE)
    )

    fun detectFromHtml(html: String): Boolean? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html)
        return detectFromDocument(doc)
    }

    fun detectFromDocument(doc: Document): Boolean? {
        val metaRating = sequenceOf(
            doc.selectFirst("meta[property=og:rating]")?.attr("content"),
            doc.selectFirst("meta[name=rating]")?.attr("content"),
            doc.selectFirst("meta[itemprop=contentRating]")?.attr("content"),
            doc.selectFirst("[itemprop=contentRating]")?.text()
        ).filterNotNull().joinToString(" ")
        val badgeText = doc.select(
            ".age-rating, .fsk, .rating-badge, .certificate, [class*=fsk], [class*=age], [class*=adult]"
        ).text()
        val sample = buildString {
            append(doc.text().take(4000))
            append(' ')
            append(metaRating)
            append(' ')
            append(badgeText)
        }
        return detectFromText(sample)
    }

    fun detectFromText(text: String): Boolean? {
        val sample = text.lowercase()
        if (adultPatterns.any { it.matcher(sample).find() }) return true
        if (kidsPatterns.any { it.matcher(sample).find() }) return false
        return null
    }
}

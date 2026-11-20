package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One section of the detailed analysis: a [title], its [text] and the [evidence], the short figures it rests on. */
data class AiDeepSection(val title: String = "", val text: String = "", val evidence: List<String> = emptyList())

/**
 * The assistant's answer as the app shows it: a [summary], the [patterns] it saw, what to do in the coming days
 * ([doThis]), what to change ([adjust]) and the [outlook] for the next 3 to 7 days, and under them the [deep], the
 * detailed analysis those were drawn from (empty for an answer made before it existed, or when it didn't come back).
 * [plain] is the model's own text when it didn't come back as the JSON asked for, shown as it is; [partial] says some of
 * the five parts were missing or cut off.
 */
data class AiAnswer(
    val summary: String = "",
    val patterns: List<String> = emptyList(),
    val doThis: List<String> = emptyList(),
    val adjust: List<String> = emptyList(),
    val outlook: String = "",
    val plain: String? = null,
    val partial: Boolean = false,
    val deep: List<AiDeepSection> = emptyList(),
) {
    /** The answer has structure to show (not only [plain] text). */
    val structured: Boolean get() = plain == null

    /** The answer as JSON for the cache, readable again by [parse]. */
    fun toJson(): String = JSONObject().apply {
        put(SUMMARY, summary)
        put(PATTERNS, JSONArray(patterns))
        put(DO_THIS, JSONArray(doThis))
        put(ADJUST, JSONArray(adjust))
        put(OUTLOOK, outlook)
        if (plain != null) put(PLAIN, plain)
        put(PARTIAL, partial)
        if (deep.isNotEmpty()) put(DEEP, JSONArray().apply { deep.forEach { put(sectionJson(it)) } })
    }.toString()

    companion object {
        const val SUMMARY = "summary"
        const val PATTERNS = "patterns"
        const val DO_THIS = "doThis"
        const val ADJUST = "adjust"
        const val OUTLOOK = "outlook"
        const val DEEP = "deep"
        private const val PLAIN = "plain"
        private const val PARTIAL = "partial"

        /** The names a model may use for each part, the asked-for one first. */
        private val SUMMARY_NAMES = listOf(SUMMARY, "overview", "ملخص")
        private val PATTERN_NAMES = listOf(PATTERNS, "pattern", "observations", "أنماط")
        private val DO_NAMES = listOf(DO_THIS, "do_this", "dothis", "do", "actions", "todo")
        private val ADJUST_NAMES = listOf(ADJUST, "adjustments", "changes", "tweaks")
        private val OUTLOOK_NAMES = listOf(OUTLOOK, "forecast", "outlook_next_days", "expectation")
        private val DEEP_NAMES = listOf(DEEP, "detailed", "detail", "sections", "detailed_analysis", "analysis")
        private val TITLE_NAMES = listOf("title", "name", "heading", "section", "topic")
        private val TEXT_NAMES = listOf("text", "body", "analysis", "content", "details", "description")
        private val EVIDENCE_NAMES = listOf("evidence", "numbers", "data", "facts", "figures")

        /** Most items in a list and most characters in an item or a paragraph that the app keeps. */
        const val MAX_ITEMS = 8
        const val MAX_ITEM_CHARS = 500
        const val MAX_PARAGRAPH_CHARS = 1_200
        const val MAX_PLAIN_CHARS = 4_000

        /** The detailed analysis: most sections, most characters in a title, a section's text and an evidence item, and most evidence items. */
        const val MAX_SECTIONS = 8
        const val MAX_TITLE_CHARS = 80
        const val MAX_SECTION_CHARS = 2_500
        const val MAX_EVIDENCE = 8
        const val MAX_EVIDENCE_CHARS = 140

        private val fence = Regex("```[A-Za-z]*[ \\t]*\\r?\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
        private val openFence = Regex("^```[A-Za-z]*[ \\t]*\\r?\\n?")
        private val bullet = Regex("^(?:[-*\\u2022\\u25CF]+|\\d{1,2}[.)])\\s+")
        private val spaces = Regex("[ \\t\\u00A0]+")

        /**
         * [raw], the model's reply, as an answer. Never throws. Code fences around the JSON are stripped, fields it
         * doesn't know are ignored, an answer cut off mid-way is closed and read as far as it goes ([partial]), and a
         * reply with no JSON in it comes back as [plain] text. Null only when there is nothing to show (blank).
         */
        fun parse(raw: String?): AiAnswer? {
            val text = unfence(raw?.replace("﻿", "")?.trim().orEmpty())
            if (text.isEmpty()) return null
            val (obj, repaired) = readObject(text) ?: return plainOnly(text)
            val summary = paragraph(obj, SUMMARY_NAMES)
            val patterns = items(obj, PATTERN_NAMES)
            val doThis = items(obj, DO_NAMES)
            val adjust = items(obj, ADJUST_NAMES)
            val outlook = paragraph(obj, OUTLOOK_NAMES)
            val deep = deepSections(obj)
            val missing = listOf(summary.isEmpty(), patterns.isEmpty(), doThis.isEmpty(), adjust.isEmpty(), outlook.isEmpty()).count { it }
            // JSON with none of the five parts (and no detailed analysis) is not an answer in our shape: show what the model wrote.
            if (missing == PARTS && deep.isEmpty()) return plainOnly(text)
            // A missing or unreadable `deep` doesn't make the answer partial: the five parts are the answer, `deep` is more.
            return AiAnswer(summary, patterns, doThis, adjust, outlook, plain = null, partial = repaired || missing > 0, deep = deep)
        }

        private const val PARTS = 5

        /** An answer read back from [toJson] (the cache); null when [text] isn't one. */
        fun fromJson(text: String): AiAnswer? = try {
            val o = JSONObject(text)
            val plain = o.optString(PLAIN, "").takeIf { it.isNotBlank() }
            val list = { key: String -> o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() }
            AiAnswer(
                o.optString(SUMMARY, ""), list(PATTERNS), list(DO_THIS), list(ADJUST), o.optString(OUTLOOK, ""), plain, o.optBoolean(PARTIAL, false),
                deepSections(o),
            )
        } catch (e: JSONException) {
            null
        }

        private fun sectionJson(s: AiDeepSection): JSONObject =
            JSONObject().put("title", s.title).put("text", s.text).put("evidence", JSONArray(s.evidence))

        /**
         * The detailed analysis in [obj], as far as it reads: a list of sections (objects with a title, a text and
         * evidence, or bare strings), or an object of title to text. Anything else, or nothing, is no sections.
         */
        private fun deepSections(obj: JSONObject): List<AiDeepSection> {
            val v = DEEP_NAMES.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL && (it is JSONArray || it is JSONObject) } }
                ?: return emptyList()
            val raw: List<Any?> = when (v) {
                is JSONArray -> (0 until v.length()).map { v.opt(it) }
                is JSONObject -> v.keys().asSequence().map { k ->
                    val value = v.opt(k)
                    // A title mapped to a text, or to an object that holds the text and its evidence.
                    (value as? JSONObject ?: JSONObject().put("text", value)).put("title", k)
                }.toList()
                else -> emptyList()
            }
            return raw.mapNotNull { section(it) }.take(MAX_SECTIONS)
        }

        private fun section(v: Any?): AiDeepSection? {
            val s = when (v) {
                is String -> AiDeepSection(text = clean(v).take(MAX_SECTION_CHARS).trim())
                is JSONObject -> {
                    val title = clean(firstText(v, TITLE_NAMES)).take(MAX_TITLE_CHARS).trim()
                    val text = clean(firstText(v, TEXT_NAMES)).take(MAX_SECTION_CHARS).trim()
                    AiDeepSection(title, text, evidence(v))
                }
                else -> return null
            }
            return s.takeIf { it.text.isNotEmpty() || it.evidence.isNotEmpty() }
        }

        /** The first of [names] in [o] that holds text, as text (a list of strings is joined). */
        private fun firstText(o: JSONObject, names: List<String>): String {
            for (n in names) {
                val v = o.opt(n)
                val t = when (v) {
                    is String -> v
                    is JSONArray -> (0 until v.length()).mapNotNull { itemText(v.opt(it)) }.joinToString(" ")
                    is Number, is Boolean -> v.toString()
                    else -> ""
                }
                if (t.isNotBlank()) return t
            }
            return ""
        }

        private fun evidence(o: JSONObject): List<String> {
            val v = EVIDENCE_NAMES.firstNotNullOfOrNull { n -> o.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return emptyList()
            val raw = when (v) {
                is JSONArray -> (0 until v.length()).mapNotNull { itemText(v.opt(it)) }
                is String -> v.lines()
                else -> listOfNotNull(itemText(v))
            }
            return raw.map { clean(it).replace(bullet, "").take(MAX_EVIDENCE_CHARS).trim() }.filter { it.isNotEmpty() }.take(MAX_EVIDENCE)
        }

        private fun plainOnly(text: String): AiAnswer =
            AiAnswer(plain = digits(text).take(MAX_PLAIN_CHARS).trim(), partial = true)

        /** The text inside a code fence when there is one (an unclosed fence loses only its opening line). */
        internal fun unfence(text: String): String {
            fence.find(text)?.let { return it.groupValues[1].trim() }
            return text.replaceFirst(openFence, "").trim()
        }

        /** The first JSON object in [text] and whether it had to be closed ([repair]); null when there is none. */
        internal fun readObject(text: String): Pair<JSONObject, Boolean>? {
            val start = text.indexOf('{')
            if (start < 0) return null
            val candidate = text.substring(start)
            val end = balancedEnd(candidate)
            if (end != null) {
                try {
                    return JSONObject(candidate.substring(0, end + 1)) to false
                } catch (e: JSONException) {
                    // Fall through to the repair, which may read it as far as it goes.
                }
            }
            return try {
                JSONObject(repair(candidate)) to true
            } catch (e: JSONException) {
                null
            }
        }

        /** The index of the brace that closes the object [s] opens, or null when it never closes. */
        private fun balancedEnd(s: String): Int? {
            var depth = 0
            var inString = false
            var escaped = false
            for ((i, c) in s.withIndex()) {
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> { depth--; if (depth == 0) return i }
                }
            }
            return null
        }

        /**
         * [s] cut off or left open, closed: an open string ended, a dangling comma, colon or key dropped, every open
         * bracket and brace closed in the order they opened.
         */
        private fun repair(s: String): String {
            val open = ArrayDeque<Char>()
            var inString = false
            var escaped = false
            val out = StringBuilder()
            for (c in s) {
                out.append(c)
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> open.addLast('}')
                    '[' -> open.addLast(']')
                    '}', ']' -> open.removeLastOrNull()
                }
            }
            if (inString) {
                if (escaped) out.setLength(out.length - 1)
                out.append('"')
            }
            // A cut after a key's colon, after a comma or inside a key leaves nothing to read: back to the last whole value.
            var trimmed = out.toString().trimEnd()
            while (true) {
                trimmed = when {
                    trimmed.endsWith(",") || trimmed.endsWith(":") -> trimmed.dropLast(1).trimEnd()
                    trimmed.endsWith("\"") && open.lastOrNull() == '}' && keyOnly(trimmed) -> dropKey(trimmed)
                    else -> break
                }
            }
            return trimmed + open.reversed().joinToString("")
        }

        /** Whether [s] ends in a key with no value (`..., "key"`), which the closing braces would leave invalid. */
        private fun keyOnly(s: String): Boolean {
            val close = s.length - 1
            val open = s.lastIndexOf('"', close - 1)
            if (open < 0) return false
            val before = s.substring(0, open).trimEnd()
            return before.endsWith(",") || before.endsWith("{")
        }

        private fun dropKey(s: String): String {
            val open = s.lastIndexOf('"', s.length - 2)
            return s.substring(0, open).trimEnd().removeSuffix(",").trimEnd()
        }

        private fun paragraph(obj: JSONObject, names: List<String>): String {
            val v = names.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return ""
            val text = when (v) {
                is String -> v
                is JSONArray -> (0 until v.length()).mapNotNull { itemText(v.opt(it)) }.joinToString(" ")
                else -> itemText(v).orEmpty()
            }
            return clean(text).take(MAX_PARAGRAPH_CHARS).trim()
        }

        private fun items(obj: JSONObject, names: List<String>): List<String> {
            val v = names.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return emptyList()
            val raw = when (v) {
                is JSONArray -> (0 until v.length()).mapNotNull { itemText(v.opt(it)) }
                is String -> v.lines()
                else -> listOfNotNull(itemText(v))
            }
            return raw.map { clean(it).replace(bullet, "").take(MAX_ITEM_CHARS).trim() }.filter { it.isNotEmpty() }.take(MAX_ITEMS)
        }

        /** One list entry as text: a string as it is, an object by its first of text, title, description, action. */
        private fun itemText(v: Any?): String? = when (v) {
            null, JSONObject.NULL -> null
            is String -> v
            is Number, is Boolean -> v.toString()
            is JSONObject -> {
                val title = listOf("text", "title", "action", "description", "advice").firstNotNullOfOrNull { k -> v.optString(k, "").takeIf { it.isNotBlank() } }
                val more = listOf("detail", "details", "reason", "why").firstNotNullOfOrNull { k -> v.optString(k, "").takeIf { it.isNotBlank() } }
                listOfNotNull(title, more.takeIf { title != null }).joinToString(". ").ifBlank { null }
            }
            else -> null
        }

        private fun clean(s: String): String = digits(s).replace(spaces, " ").lines().joinToString(" ") { it.trim() }.trim()

        /** Eastern Arabic and Persian digits as Western ones, so the app shows 0 to 9 whatever the model wrote. */
        fun digits(s: String): String = buildString(s.length) {
            for (c in s) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                },
            )
        }
    }
}

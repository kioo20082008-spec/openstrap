package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openstrap.app.data.ai.AiAnswer
import java.time.LocalDate
import kotlin.math.roundToInt

/** What a statement of the body profile is about, and its key in the profile's JSON. The order is the order the page shows them in. */
enum class ProfileSection(val key: String) {
    /** How he sleeps: the sleep he really needs, how long, how it breaks. */
    Sleep("sleep"),

    /** His rhythm: chronotype, the usual window, what a late night does to the next. */
    Rhythm("rhythm"),

    /** What hurts his sleep. */
    SleepHurts("sleep_hurts"),

    /** How his HRV reacts to load, late nights and steps. */
    Hrv("hrv"),

    /** How his resting heart rate reacts. */
    RestingHr("rhr"),

    /** Skin temperature: his usual and what moves it. */
    SkinTemp("temp"),

    /** Respiration: his usual and what moves it. */
    Breathing("resp"),

    /** What days that look like an illness look like on him. */
    Illness("illness"),

    /** How fast he recovers after a hard day. */
    Recovery("recovery"),

    /** Which of the app's own readings looked implausible for him, and why. */
    Implausible("implausible"),
    ;

    companion object {
        fun of(key: String): ProfileSection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One thing the AI learned about him: a short [text] (plain Arabic), how sure it is ([confidence], 0 to 100), how many
 * observations it rests on ([n]) and, when it differs from the profile's, the first day it rests on ([from]).
 */
data class Statement(val text: String, val confidence: Int, val n: Int = 0, val from: LocalDate? = null)

/**
 * The few numbers a profile may hold, each checked against the bounds of what it is. [needMin] is the sleep he seems to
 * need, in minutes (a suggestion: the Sleep need setting stays his own), [bed] and [wake] his usual times as "HH:mm".
 */
data class ProfileFacts(
    val needMin: Int? = null,
    val needConf: Int? = null,
    val bed: String? = null,
    val wake: String? = null,
    val recoveryDays: Float? = null,
    val hrvMs: Int? = null,
    val rhrBpm: Int? = null,
) {
    val isEmpty: Boolean get() = this == ProfileFacts()

    /** These facts with the ones [other] has given taking the place of the ones they say something about. */
    fun overlay(other: ProfileFacts?): ProfileFacts = if (other == null) this else ProfileFacts(
        other.needMin ?: needMin, other.needConf ?: needConf, other.bed ?: bed, other.wake ?: wake,
        other.recoveryDays ?: recoveryDays, other.hrvMs ?: hrvMs, other.rhrBpm ?: rhrBpm,
    )
}

/**
 * The body profile (docs/algorithms/self-check.md, AI body learning): what the AI has learned about how HIS body works,
 * kept as a small document it rewrites each day, so the history never has to be sent in full again. [from] to [to] is the
 * span of data it rests on (the app's own bookkeeping, not the AI's), [updated] the day it was last rewritten.
 * [sections] holds only sections that have something in them.
 */
data class Profile(
    val updated: LocalDate? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val sections: Map<ProfileSection, List<Statement>> = emptyMap(),
    val facts: ProfileFacts = ProfileFacts(),
) {
    val statements: Int get() = sections.values.sumOf { it.size }
    val isEmpty: Boolean get() = statements == 0 && facts.isEmpty
}

/**
 * Reading, merging, bounding and writing the [Profile]. Everything the AI hands back passes through here before it is kept:
 * unknown sections and keys are dropped, text is cut down to plain letters and digits and a short length, numbers are put
 * inside their bounds, and the whole is held to [MAX_BYTES] by dropping the least certain statements first. Pure.
 */
object BodyProfile {
    /** The most bytes the stored profile may take. */
    const val MAX_BYTES = 4_096

    const val VERSION = 1

    /** Most statements in a section, and most characters in one. */
    const val MAX_PER_SECTION = 3
    const val MAX_TEXT = 120

    /** Bounds of the facts. */
    const val NEED_MIN = 360
    const val NEED_MAX = 600
    private const val HRV_MIN = 5
    private const val HRV_MAX = 300
    private const val RHR_MIN = 30
    private const val RHR_MAX = 120
    private const val RECOVERY_DAYS_MAX = 14f
    private const val MAX_OBSERVATIONS = 9_999
    private const val MIN_TEXT = 6
    private const val PERCENT = 100
    private const val DECIMAL = 10f

    /** Every key that may appear in the profile's JSON, so a test (and a reader) can see nothing else is stored or sent. */
    val KEYS: Set<String> = setOf("v", "u", "from", "to", "facts", "need", "need_c", "bed", "wake", "rec_days", "hrv_ms", "rhr_bpm", "t", "c", "n", "f") +
        ProfileSection.entries.map { it.key }

    private val url = Regex("[A-Za-z]+://\\S*")
    private val spaces = Regex("\\s{2,}")
    private val clock = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    private const val KEPT_PUNCTUATION = ".,;:()%/+-=?!،؛؟≈×"

    /**
     * [raw] as a short line of plain text: Western digits, letters, digits, the marks that belong to letters (Arabic shadda and
     * tanween, so «تعلّمت» stays whole), spaces and a little punctuation only (no quotes, angle, square or curly brackets, `@`,
     * `#` or links, so nothing in it can read as markup or an address), at most [max] characters.
     */
    fun cleanText(raw: String?, max: Int = MAX_TEXT): String {
        val flat = url.replace(AiAnswer.digits(raw.orEmpty()), " ")
        val kept = flat.map { if (it.isLetterOrDigit() || it.isLetterMark() || it == ' ' || it in KEPT_PUNCTUATION) it else ' ' }.joinToString("")
        return spaces.replace(kept, " ").trim().take(max).trim()
    }

    /** A combining mark (Arabic harakat and shadda, for one): part of the letter before it, not punctuation. */
    private fun Char.isLetterMark(): Boolean = category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

    // ---------------------------------------------------------------------------------------------------------------
    // Reading

    /** The profile stored as [raw] by [encode]; null for nothing or text that is not one. */
    fun decode(raw: String?): Profile? {
        if (raw.isNullOrBlank()) return null
        return try {
            fromJson(JSONObject(raw))
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * The profile in [o], as far as it reads: the sections it names (an empty list is kept, it says the section is cleared),
     * each statement cleaned, bounded and cut to [MAX_PER_SECTION] most certain ones, the facts that are valid, and the dates
     * that parse. Keys it doesn't know are ignored. Null when [o] is null.
     */
    fun fromJson(o: JSONObject?): Profile? {
        if (o == null) return null
        val sections = LinkedHashMap<ProfileSection, List<Statement>>()
        for (s in ProfileSection.entries) {
            val v = o.opt(s.key)
            if (v !is JSONArray) continue
            sections[s] = (0 until v.length()).mapNotNull { statement(v.opt(it)) }.sortedByDescending { it.confidence }.take(MAX_PER_SECTION)
        }
        return Profile(
            updated = date(o.opt("u")), from = date(o.opt("from")), to = date(o.opt("to")),
            sections = sections, facts = facts(o.opt("facts") as? JSONObject),
        )
    }

    private fun statement(v: Any?): Statement? = when (v) {
        is String -> cleanText(v).takeIf { it.length >= MIN_TEXT }?.let { Statement(it, DEFAULT_CONFIDENCE) }
        is JSONObject -> {
            val text = cleanText(firstText(v, "t", "text", "statement"))
            if (text.length < MIN_TEXT) null else Statement(
                text,
                percent(number(v, "c", "conf", "confidence")) ?: DEFAULT_CONFIDENCE,
                number(v, "n", "obs")?.roundToInt()?.coerceIn(0, MAX_OBSERVATIONS) ?: 0,
                date(v.opt("f")),
            )
        }
        else -> null
    }

    private const val DEFAULT_CONFIDENCE = 50

    private fun facts(o: JSONObject?): ProfileFacts {
        if (o == null) return ProfileFacts()
        return ProfileFacts(
            needMin = number(o, "need")?.roundToInt()?.takeIf { it in NEED_MIN..NEED_MAX },
            needConf = percent(number(o, "need_c")),
            bed = clockText(o.opt("bed")),
            wake = clockText(o.opt("wake")),
            recoveryDays = number(o, "rec_days")?.toFloat()?.takeIf { it in 0f..RECOVERY_DAYS_MAX }?.let { (it * DECIMAL).roundToInt() / DECIMAL },
            hrvMs = number(o, "hrv_ms")?.roundToInt()?.takeIf { it in HRV_MIN..HRV_MAX },
            rhrBpm = number(o, "rhr_bpm")?.roundToInt()?.takeIf { it in RHR_MIN..RHR_MAX },
        )
    }

    private fun clockText(v: Any?): String? = (v as? String)?.trim()?.takeIf { clock.matches(it) }

    private fun date(v: Any?): LocalDate? = (v as? String)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    internal fun firstText(o: JSONObject, vararg names: String): String? = names.firstNotNullOfOrNull { n -> (o.opt(n) as? String)?.takeIf { it.isNotBlank() } }

    /** The number under the first of [names], from a number or a text that is one (Eastern Arabic digits too). */
    internal fun number(o: JSONObject, vararg names: String): Double? {
        for (n in names) {
            val v = o.opt(n)
            val d = when (v) {
                is Number -> v.toDouble()
                is String -> AiAnswer.digits(v).trim().trimEnd('%').replace(',', '.').toDoubleOrNull()
                else -> null
            }
            if (d != null && d.isFinite()) return d
        }
        return null
    }

    /** A confidence given as 0 to 1 or 0 to 100, as 0 to 100. */
    internal fun percent(v: Double?): Int? = v?.let { (if (it in 0.0..1.0) it * PERCENT else it).roundToInt().coerceIn(0, PERCENT) }

    // ---------------------------------------------------------------------------------------------------------------
    // Writing

    fun toJson(p: Profile): JSONObject = JSONObject().apply {
        put("v", VERSION)
        p.updated?.let { put("u", it.toString()) }
        p.from?.let { put("from", it.toString()) }
        p.to?.let { put("to", it.toString()) }
        if (!p.facts.isEmpty) put("facts", factsJson(p.facts))
        for (s in ProfileSection.entries) {
            val list = p.sections[s]?.takeIf { it.isNotEmpty() } ?: continue
            put(
                s.key,
                JSONArray().apply {
                    list.forEach { st ->
                        put(
                            JSONObject().put("t", st.text).put("c", st.confidence).apply {
                                if (st.n > 0) put("n", st.n)
                                st.from?.let { put("f", it.toString()) }
                            },
                        )
                    }
                },
            )
        }
    }

    private fun factsJson(f: ProfileFacts): JSONObject = JSONObject().apply {
        f.needMin?.let { put("need", it) }
        f.needConf?.let { put("need_c", it) }
        f.bed?.let { put("bed", it) }
        f.wake?.let { put("wake", it) }
        f.recoveryDays?.let { put("rec_days", (it * DECIMAL).roundToInt() / DECIMAL.toDouble()) }
        f.hrvMs?.let { put("hrv_ms", it) }
        f.rhrBpm?.let { put("rhr_bpm", it) }
    }

    /** The profile as the text that is stored and sent. */
    fun encode(p: Profile): String = toJson(p).toString()

    fun bytes(p: Profile): Int = encode(p).toByteArray(Charsets.UTF_8).size

    // ---------------------------------------------------------------------------------------------------------------
    // Merging

    /**
     * The profile after a day's rewrite: what the AI [incoming] says on top of what was [old]. A section the answer names
     * replaces the old one (an empty one clears it); a section it leaves out stays as it was, and so does any fact it leaves
     * out. The span is the app's own: it starts at the earliest day of data seen ([dataFrom] or the old start) and ends
     * [today]. The result is bounded and fits [MAX_BYTES]. Without an [incoming] profile (an answer that had none) nothing
     * but the end of the span moves.
     */
    fun merge(old: Profile?, incoming: Profile?, today: LocalDate, dataFrom: LocalDate?): Profile {
        val sections = LinkedHashMap<ProfileSection, List<Statement>>()
        old?.sections?.let { sections.putAll(it) }
        incoming?.sections?.forEach { (section, list) -> if (list.isEmpty()) sections.remove(section) else sections[section] = list }
        val from = listOfNotNull(old?.from, dataFrom).minOrNull()
        val merged = Profile(
            updated = if (incoming != null) today else old?.updated,
            from = from,
            to = today,
            sections = ProfileSection.entries.mapNotNull { s -> sections[s]?.let { s to inSpan(it, from, today) } }.toMap(),
            facts = (old?.facts ?: ProfileFacts()).overlay(incoming?.facts),
        )
        return fit(merged)
    }

    /** [list] bounded: at most [MAX_PER_SECTION] of the most certain, and a first day only where it lies inside [from] to [to]. */
    private fun inSpan(list: List<Statement>, from: LocalDate?, to: LocalDate): List<Statement> =
        list.sortedByDescending { it.confidence }.take(MAX_PER_SECTION).map { st ->
            st.copy(from = st.from?.takeIf { f -> f <= to && (from == null || f >= from) })
        }

    /** [p] cut to [max] bytes by dropping, one at a time, the least certain statement (then the one with fewest observations). */
    fun fit(p: Profile, max: Int = MAX_BYTES): Profile {
        var cur = p
        while (bytes(cur) > max) {
            val victim = cur.sections.entries.flatMap { (s, l) -> l.map { s to it } }
                .minWithOrNull(compareBy({ it.second.confidence }, { it.second.n }, { -it.second.text.length })) ?: break
            cur = cur.copy(
                sections = cur.sections.mapValues { (s, l) -> if (s == victim.first) l - victim.second else l }.filterValues { it.isNotEmpty() },
            )
        }
        return if (bytes(cur) > max) cur.copy(facts = ProfileFacts()) else cur
    }
}

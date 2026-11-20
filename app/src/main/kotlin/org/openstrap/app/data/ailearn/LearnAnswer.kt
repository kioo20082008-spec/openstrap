package org.openstrap.app.data.ailearn

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.openstrap.app.data.ai.AiAnswer

/**
 * One change the AI proposes, as it wrote it and before anything is believed: the [key] it names (a knob, or the sleep need),
 * the value it wants ([to], null when it was not a number), the [evidence] it gives and how sure it is. [ProposalGate] decides.
 */
data class RawProposal(val key: String, val to: Double?, val evidence: String, val confidence: Int?)

/**
 * The AI's answer to the daily learning request: what it learned today in a few plain lines, the profile it rewrote and the
 * changes it proposes. Read tolerantly ([parse]); nothing in it is kept before [BodyProfile] and [ProposalGate] have bounded it.
 */
data class LearnAnswer(
    val learned: List<String> = emptyList(),
    val profile: Profile? = null,
    val proposals: List<RawProposal> = emptyList(),
) {
    /** Nothing usable in it. */
    val empty: Boolean get() = learned.isEmpty() && (profile == null || profile.isEmpty) && proposals.isEmpty()

    companion object {
        const val LEARNED = "learned"
        const val PROFILE = "profile"
        const val PROPOSALS = "proposals"

        /** Most lines of "what I learned" and the longest of them. */
        const val MAX_LINES = 3
        const val MAX_LINE = 200
        private const val MIN_LINE = 6

        /** Most proposals read, and the longest evidence kept. */
        const val MAX_READ = 12
        const val MAX_EVIDENCE = 200

        private val LEARNED_NAMES = listOf(LEARNED, "today", "what_learned", "learned_today", "ما_تعلمته")
        private val PROFILE_NAMES = listOf(PROFILE, "body_profile", "updated_profile")
        private val PROPOSAL_NAMES = listOf(PROPOSALS, "tuning", "changes", "adjustments")

        /**
         * [raw], the model's reply, as an answer. Never throws. Code fences around the JSON are stripped, keys it doesn't
         * know are ignored, an answer cut off mid-way is closed and read as far as it goes. Null when there is no JSON in it
         * or nothing usable ([empty]).
         */
        fun parse(raw: String?): LearnAnswer? {
            val text = AiAnswer.unfence(raw?.replace("﻿", "")?.trim().orEmpty())
            if (text.isEmpty()) return null
            val (obj, _) = AiAnswer.readObject(text) ?: return null
            val answer = LearnAnswer(lines(obj), profile(obj), proposals(obj))
            return answer.takeUnless { it.empty }
        }

        private fun lines(obj: JSONObject): List<String> {
            val v = LEARNED_NAMES.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return emptyList()
            val raw: List<String> = when (v) {
                is JSONArray -> (0 until v.length()).mapNotNull { i -> (v.opt(i) as? String) ?: (v.opt(i) as? JSONObject)?.let { BodyProfile.firstText(it, "t", "text", "line") } }
                is String -> v.lines()
                else -> emptyList()
            }
            return raw.map { BodyProfile.cleanText(it, MAX_LINE) }.filter { it.length >= MIN_LINE }.take(MAX_LINES)
        }

        private fun profile(obj: JSONObject): Profile? {
            val v = PROFILE_NAMES.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return null
            val o = when (v) {
                is JSONObject -> v
                // A model sometimes writes the profile as a string of JSON.
                is String -> try { JSONObject(AiAnswer.unfence(v.trim())) } catch (e: JSONException) { return null }
                else -> return null
            }
            return BodyProfile.fromJson(o)
        }

        private fun proposals(obj: JSONObject): List<RawProposal> {
            val v = PROPOSAL_NAMES.firstNotNullOfOrNull { n -> obj.opt(n)?.takeIf { it != JSONObject.NULL } } ?: return emptyList()
            val items: List<JSONObject> = when (v) {
                is JSONArray -> (0 until v.length()).mapNotNull { v.opt(it) as? JSONObject }
                // `{"rest": 0.8}` or `{"rest": {"to": 0.8, ...}}`
                is JSONObject -> v.keys().asSequence().toList().mapNotNull { k ->
                    when (val x = v.opt(k)) {
                        is JSONObject -> JSONObject(x.toString()).put("knob", k)
                        is Number -> JSONObject().put("knob", k).put("to", x)
                        else -> null
                    }
                }
                else -> emptyList()
            }
            return items.take(MAX_READ).map { o ->
                RawProposal(
                    key = (BodyProfile.firstText(o, "knob", "key", "name") ?: "").trim().lowercase(),
                    to = BodyProfile.number(o, "to", "value", "new", "target"),
                    evidence = BodyProfile.cleanText(BodyProfile.firstText(o, "evidence", "why", "reason"), MAX_EVIDENCE),
                    confidence = BodyProfile.percent(BodyProfile.number(o, "confidence", "conf", "c")),
                )
            }
        }
    }
}

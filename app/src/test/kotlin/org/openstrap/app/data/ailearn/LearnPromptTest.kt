package org.openstrap.app.data.ailearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.ai.AiPayload
import org.openstrap.app.data.selfcheck.Knob
import org.openstrap.app.data.selfcheck.SelfLearning

/** The words the model is given: written from the knobs and the gate, so the prompt cannot drift from what the app enforces. */
class LearnPromptTest {
    private val system = LearnPrompt.system

    @Test fun `every knob the AI may name is in the prompt with its bounds`() {
        for (k in Knob.entries) assertTrue(k.key, system.contains("${k.key} :"))
        assertTrue(system.contains(ProposalGate.SLEEP_NEED_KEY))
        assertTrue(system.contains("${ProposalGate.MIN_CONFIDENCE}") && system.contains("${ProposalGate.MAX_PROPOSALS}"))
        assertTrue(system.contains("${SelfLearning.DECAY_DAYS}"))
    }

    @Test fun `every profile section is described and the answer's shape is shown`() {
        for (s in ProfileSection.entries) assertTrue(s.key, system.contains("${s.key} :"))
        for (k in listOf(LearnAnswer.LEARNED, LearnAnswer.PROFILE, LearnAnswer.PROPOSALS)) assertTrue(k, system.contains("\"$k\""))
    }

    @Test fun `the model is told it cannot touch stored data, scores or weights, and is no doctor`() {
        assertTrue(system.contains("لا تقترح تعديل أي درجة نوم أو تعافٍ أو بيانات مخزنة أو أوزان"))
        assertTrue(system.contains("لست طبيباً"))
    }

    @Test fun `the prompt carries no personal text and the user turn is the payload`() {
        assertFalse(system.contains("@") || system.contains("http"))
        val payload = LearnPayload.build(learnInput(days = 20)).json
        val user = LearnPrompt.userMessage(payload)
        assertTrue(user.endsWith(payload))
    }

    @Test fun `a profile is sent with the analysis, whole, without counting against its cap`() {
        val stored = BodyProfile.encode(sampleProfile())
        val with = AiPayload.build(org.openstrap.app.data.ai.aiInput(), stored)
        val without = AiPayload.build(org.openstrap.app.data.ai.aiInput())
        assertTrue(with.json.contains("\"profile\""))
        assertFalse(without.json.contains("\"profile\""))
        assertEquals(without.days, with.days)
        assertEquals(without.nights, with.nights)
        assertEquals(org.json.JSONObject(stored).toString(), org.json.JSONObject(with.json).getJSONObject("profile").toString())
        assertFalse(AiPayload.build(org.openstrap.app.data.ai.aiInput(), "not json").json.contains("\"profile\""))
    }

    @Test fun `every key of a payload with a profile is on the analysis whitelist`() {
        val json = org.json.JSONObject(AiPayload.build(org.openstrap.app.data.ai.aiInput(), BodyProfile.encode(sampleProfile())).json)
        fun keys(v: Any?, into: MutableSet<String> = linkedSetOf()): Set<String> {
            when (v) {
                is org.json.JSONObject -> v.keys().forEach { k -> into += k; keys(v.get(k), into) }
                is org.json.JSONArray -> for (i in 0 until v.length()) keys(v.get(i), into)
            }
            return into
        }
        assertEquals(emptySet<String>(), keys(json) - AiPayload.KEYS)
    }
}

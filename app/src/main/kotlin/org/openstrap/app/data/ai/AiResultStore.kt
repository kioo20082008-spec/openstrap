package org.openstrap.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One analysis kept: the [day] it was asked on, [at] when it came (epoch seconds) and the [answer]. */
data class AiCached(
    val day: LocalDate,
    val at: Long,
    val answer: AiAnswer,
    /** How much history the analysis was made from: days, nights and workouts sent. */
    val days: Int = 0,
    val nights: Int = 0,
    val workouts: Int = 0,
)

/**
 * The last analyses, one per day, kept in one small file in the app's own folder (no database, no Room migration): at
 * most [KEEP] days, a new analysis on the same day replacing that day's. Only what the model answered is kept, never what
 * was sent. A damaged file reads as empty. Writes go to a temporary file first, so a crash can't leave half a file.
 */
// The IO dispatcher is chosen here, where the file is.
@Suppress("InjectDispatcher")
class AiResultStore(private val file: File) {
    suspend fun all(): List<AiCached> = withContext(Dispatchers.IO) { read() }

    /** The newest analysis, from today or an earlier day, or null before the first. */
    suspend fun latest(): AiCached? = all().maxByOrNull { it.at }

    /** Keeps [cached], in place of the one for its day. */
    suspend fun save(cached: AiCached) {
        withContext(Dispatchers.IO) {
            val kept = (read().filter { it.day != cached.day } + cached).sortedByDescending { it.day }.take(KEEP)
            val tmp = File(file.parentFile, file.name + TEMP)
            tmp.writeText(encode(kept))
            if (!tmp.renameTo(file)) {
                file.writeText(encode(kept))
                tmp.delete()
            }
        }
    }

    /** Deletes what is kept. */
    suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun read(): List<AiCached> {
        if (!file.isFile) return emptyList()
        return try {
            decode(file.readText())
        } catch (e: java.io.IOException) {
            emptyList()
        }
    }

    companion object {
        const val KEEP = 7
        private const val TEMP = ".tmp"
        private const val VERSION = 1

        fun encode(entries: List<AiCached>): String = JSONObject().apply {
            put("v", VERSION)
            put(
                "entries",
                JSONArray().apply {
                    entries.forEach { e ->
                        put(
                            JSONObject().put("day", e.day.toString()).put("at", e.at).put("answer", e.answer.toJson())
                                .put("days", e.days).put("nights", e.nights).put("workouts", e.workouts),
                        )
                    }
                },
            )
        }.toString()

        /** The entries in [text], those that read; a damaged file or entry is skipped, never an error. */
        fun decode(text: String): List<AiCached> = try {
            val list = JSONObject(text).optJSONArray("entries") ?: JSONArray()
            (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val day = runCatching { LocalDate.parse(o.optString("day")) }.getOrNull() ?: return@mapNotNull null
                val answer = AiAnswer.fromJson(o.optString("answer")) ?: return@mapNotNull null
                AiCached(day, o.optLong("at", 0L), answer, o.optInt("days", 0), o.optInt("nights", 0), o.optInt("workouts", 0))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

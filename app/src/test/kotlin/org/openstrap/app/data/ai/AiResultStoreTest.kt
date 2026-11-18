package org.openstrap.app.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** The last analyses, kept per day in a small file. */
class AiResultStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val file: File by lazy { File(folder.root, "ai_analysis.json") }
    private val store by lazy { AiResultStore(file) }

    private fun cached(day: LocalDate, at: Long = day.toEpochDay() * 86_400, summary: String = "ملخص $day") =
        AiCached(day, at, AiAnswer(summary, listOf("أ"), listOf("ب"), listOf("ج"), "د"), days = 30, nights = 14, workouts = 9)

    @Test fun `nothing kept is nothing`() = runBlocking {
        assertNull(store.latest())
        assertTrue(store.all().isEmpty())
    }

    @Test fun `an analysis is kept and read back whole`() = runBlocking {
        val c = cached(LocalDate.of(2026, 11, 17))
        store.save(c)
        assertEquals(c, AiResultStore(file).latest())
    }

    @Test fun `a plain-text answer is read back as plain text`() = runBlocking {
        val c = AiCached(LocalDate.of(2026, 11, 17), 1_000, AiAnswer.parse("نص بلا هيكل")!!)
        store.save(c)
        assertEquals(c, store.latest())
    }

    @Test fun `a second analysis the same day replaces the first`() = runBlocking {
        val day = LocalDate.of(2026, 11, 17)
        store.save(cached(day, at = 100, summary = "الأول"))
        store.save(cached(day, at = 200, summary = "الثاني"))
        assertEquals(1, store.all().size)
        assertEquals("الثاني", store.latest()!!.answer.summary)
    }

    @Test fun `the newest is the latest, and only a week is kept`() = runBlocking {
        val first = LocalDate.of(2026, 11, 1)
        for (i in 0 until 10) store.save(cached(first.plusDays(i.toLong())))
        assertEquals(AiResultStore.KEEP, store.all().size)
        assertEquals(first.plusDays(9), store.latest()!!.day)
        assertFalse(store.all().any { it.day == first })
    }

    @Test fun `a damaged file reads as empty and the next save repairs it`() = runBlocking {
        file.writeText("{ this is not json")
        assertNull(store.latest())
        store.save(cached(LocalDate.of(2026, 11, 17)))
        assertEquals(1, store.all().size)
    }

    @Test fun `a damaged entry is skipped, the others kept`() {
        val good = cached(LocalDate.of(2026, 11, 17))
        val text = AiResultStore.encode(listOf(good)).replace("\"entries\":[", "\"entries\":[{\"day\":\"nope\"},{\"day\":\"2026-11-16\",\"answer\":\"x\"},")
        assertEquals(listOf(good), AiResultStore.decode(text))
    }

    @Test fun `no temporary file is left behind`() = runBlocking {
        store.save(cached(LocalDate.of(2026, 11, 17)))
        assertEquals(listOf("ai_analysis.json"), folder.root.list()!!.toList())
    }

    @Test fun `clear deletes it all`() = runBlocking {
        store.save(cached(LocalDate.of(2026, 11, 17)))
        store.clear()
        assertNull(store.latest())
    }

    @Test fun `an analysis with a detailed part is kept and read back whole`() = runBlocking {
        val deep = listOf(AiDeepSection("النوم", "نص", listOf("6 ساعات")), AiDeepSection("التعافي", "نص ثان"))
        val c = AiCached(LocalDate.of(2026, 11, 17), 1_000, AiAnswer("ملخص", listOf("أ"), listOf("ب"), listOf("ج"), "د", deep = deep))
        store.save(c)
        assertEquals(c, AiResultStore(file).latest())
        assertEquals(deep, store.latest()!!.answer.deep)
    }

    @Test fun `a file kept before the detailed part existed still loads`() {
        val old = """{"v":1,"entries":[{"day":"2026-11-16","at":5,"days":30,"nights":14,"workouts":9,"answer":"{\"summary\":\"قديم\",\"patterns\":[\"أ\"],\"doThis\":[\"ب\"],\"adjust\":[\"ج\"],\"outlook\":\"د\",\"partial\":false}"}]}"""
        val read = AiResultStore.decode(old).single()
        assertEquals("قديم", read.answer.summary)
        assertTrue(read.answer.deep.isEmpty())
        assertEquals(30, read.days)
    }
}

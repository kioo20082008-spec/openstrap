package org.openstrap.app.data

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * The morning check-in: how rested you feel, 1 to 5, tapped before looking at any score. It is the one reading in the app
 * that is not computed from the strap, so it is what Energy and Recovery can be held against. Each answer keeps the Energy
 * at wake and the Recovery score shown that morning, and [fit] says how well they follow how you felt. Stored as one line
 * of text (`2026-11-07=4,82,76;2026-11-06=2,,`), the last [KEEP_DAYS] days.
 */
object CheckIn {
    /** [rating] 1 (wrecked) to 5 (fresh); [energy] and [readiness] as shown that morning, when there was one. */
    data class Entry(val rating: Int, val energy: Int?, val readiness: Int?)

    const val KEEP_DAYS = 180
    const val MIN_DAYS = 14
    const val MIN_RATING = 1
    const val MAX_RATING = 5

    /** Spearman's r between the feeling and the number, from [MIN_DAYS] answers on; below that, null. */
    data class Fit(val days: Int, val energyR: Float?, val readinessR: Float?)

    fun encode(all: Map<LocalDate, Entry>): String = all.toSortedMap().entries
        .joinToString(";") { (d, e) -> "$d=${e.rating},${e.energy ?: ""},${e.readiness ?: ""}" }

    /** What [encode] wrote; a damaged piece is skipped rather than failing the rest. */
    fun decode(raw: String?): Map<LocalDate, Entry> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<LocalDate, Entry>()
        raw.split(';').forEach { piece ->
            val parts = piece.split('=', limit = 2)
            val day = runCatching { LocalDate.parse(parts[0].trim()) }.getOrNull() ?: return@forEach
            val f = parts.getOrNull(1).orEmpty().split(',')
            val rating = f.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in MIN_RATING..MAX_RATING } ?: return@forEach
            out[day] = Entry(rating, f.getOrNull(1)?.trim()?.toIntOrNull(), f.getOrNull(2)?.trim()?.toIntOrNull())
        }
        return out
    }

    /** [all] with [day] set to [entry], and the days older than [KEEP_DAYS] before [today] dropped. */
    fun with(all: Map<LocalDate, Entry>, day: LocalDate, entry: Entry, today: LocalDate): Map<LocalDate, Entry> {
        val oldest = today.minusDays(KEEP_DAYS.toLong())
        return (all + (day to entry)).filterKeys { it >= oldest }
    }

    fun fit(all: Map<LocalDate, Entry>): Fit {
        fun r(pick: (Entry) -> Int?): Float? {
            val pairs = all.values.mapNotNull { e -> pick(e)?.let { e.rating.toFloat() to it.toFloat() } }
            if (pairs.size < MIN_DAYS) return null
            return spearman(pairs.map { it.first }, pairs.map { it.second })
        }
        return Fit(all.size, r { it.energy }, r { it.readiness })
    }

    /** Pearson on ranks, ties sharing their mean rank; null when either side never varies. */
    fun spearman(x: List<Float>, y: List<Float>): Float? {
        if (x.size != y.size || x.size < 3) return null
        val a = ranks(x)
        val b = ranks(y)
        val ma = a.average()
        val mb = b.average()
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (i in a.indices) {
            sab += (a[i] - ma) * (b[i] - mb)
            saa += (a[i] - ma) * (a[i] - ma)
            sbb += (b[i] - mb) * (b[i] - mb)
        }
        if (saa == 0.0 || sbb == 0.0) return null
        return (sab / sqrt(saa * sbb)).toFloat()
    }

    private fun ranks(v: List<Float>): List<Double> {
        val order = v.indices.sortedBy { v[it] }
        val out = DoubleArray(v.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && v[order[j + 1]] == v[order[i]]) j++
            val mean = (i + j) / 2.0 + 1
            for (k in i..j) out[order[k]] = mean
            i = j + 1
        }
        return out.toList()
    }
}

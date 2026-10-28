package com.rangseung.goal100m

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

const val OUT = "out"
const val IN = "in"

data class Entry(
    val id: String, val t: String, val amt: Long, val cat: String,
    val memo: String, val d: String, val ts: Long, val fid: String? = null,
)

data class Cat(val name: String, val save: Boolean = false)

data class Fixed(val id: String, val name: String, val amt: Long, val day: Int, val cat: String)

data class Settings(
    val startSaved: Long = 0,
    val goal: Long = 100_000_000,
    val goalYm: String = "2030-10",
    val monthly: Long = 1_500_000,
    val budget: Long = 600_000,
)

data class AppData(
    val entries: List<Entry> = emptyList(),
    val outCats: List<Cat> = emptyList(),
    val inCats: List<Cat> = emptyList(),
    val fixed: List<Fixed> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun cats(t: String) = if (t == OUT) outCats else inCats
}

data class MonthSum(val inc: Long, val out: Long, val save: Long, val byCat: List<Pair<String, Long>>)

/** All app data lives in one JSON blob in SharedPreferences; the UI reads [data] as Compose state. */
object Store {
    private const val PREF = "goal100m"
    private const val KEY = "data"
    var data by mutableStateOf(AppData())
        private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        data = raw?.let { runCatching { decode(it) }.getOrNull() } ?: AppData()
        loaded = true
    }

    private fun update(ctx: Context, f: (AppData) -> AppData) {
        load(ctx)
        data = f(data)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, encode(data)).apply()
        GoalWidget.refresh(ctx.applicationContext)
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) +
        java.lang.Long.toString((Math.random() * 1_000_000).toLong(), 36)

    fun addEntry(ctx: Context, e: Entry) = update(ctx) { it.copy(entries = it.entries + e) }
    fun deleteEntry(ctx: Context, id: String) = update(ctx) { d -> d.copy(entries = d.entries.filterNot { it.id == id }) }
    fun saveSettings(ctx: Context, s: Settings) = update(ctx) { it.copy(settings = s) }

    fun upsertFixed(ctx: Context, f: Fixed) = update(ctx) { d ->
        val exists = d.fixed.any { it.id == f.id }
        d.copy(fixed = if (exists) d.fixed.map { if (it.id == f.id) f else it } else d.fixed + f)
    }

    fun deleteFixed(ctx: Context, id: String) = update(ctx) { d -> d.copy(fixed = d.fixed.filterNot { it.id == id }) }

    /** Records every fixed cost not yet recorded in [ym]; returns how many were added. */
    fun applyFixed(ctx: Context, ym: String): Int {
        val todo = data.fixed.filterNot { f -> Calc.fixedDone(data, f.id, ym) }
        if (todo.isEmpty()) return 0
        val last = YearMonth.parse(ym).lengthOfMonth()
        val now = System.currentTimeMillis()
        val added = todo.mapIndexed { i, f ->
            Entry(newId() + i, OUT, f.amt, f.cat, f.name, "%s-%02d".format(ym, minOf(f.day, last)), now + i, f.id)
        }
        update(ctx) { it.copy(entries = it.entries + added) }
        return added.size
    }

    /** Returns an error message, or null on success. */
    fun addCat(ctx: Context, t: String, c: Cat): String? {
        if (c.name.isBlank()) return "이름을 입력해줘"
        if (data.cats(t).any { it.name == c.name }) return "이미 있는 카테고리야"
        update(ctx) { if (t == OUT) it.copy(outCats = it.outCats + c) else it.copy(inCats = it.inCats + c) }
        return null
    }

    /** Edits a category; a rename also renames past records and fixed costs. */
    fun editCat(ctx: Context, t: String, old: String, c: Cat): String? {
        if (c.name.isBlank()) return "이름을 입력해줘"
        if (c.name != old && data.cats(t).any { it.name == c.name }) return "이미 있는 카테고리야"
        update(ctx) { d ->
            val list = d.cats(t).map { if (it.name == old) c else it }
            val entries = if (c.name == old) d.entries else
                d.entries.map { if (it.t == t && it.cat == old) it.copy(cat = c.name) else it }
            val fixed = if (c.name == old || t != OUT) d.fixed else
                d.fixed.map { if (it.cat == old) it.copy(cat = c.name) else it }
            if (t == OUT) d.copy(outCats = list, entries = entries, fixed = fixed)
            else d.copy(inCats = list, entries = entries, fixed = fixed)
        }
        return null
    }

    fun deleteCat(ctx: Context, t: String, name: String) = update(ctx) { d ->
        if (t == OUT) d.copy(outCats = d.outCats.filterNot { it.name == name })
        else d.copy(inCats = d.inCats.filterNot { it.name == name })
    }

    // ---- JSON ----
    private fun encode(d: AppData): String {
        val o = JSONObject().put("v", 1)
        o.put("entries", JSONArray().apply {
            d.entries.forEach { e ->
                put(JSONObject().put("id", e.id).put("t", e.t).put("amt", e.amt).put("cat", e.cat)
                    .put("memo", e.memo).put("d", e.d).put("ts", e.ts).apply { e.fid?.let { put("fid", it) } })
            }
        })
        fun cats(l: List<Cat>) = JSONArray().apply { l.forEach { put(JSONObject().put("name", it.name).put("save", it.save)) } }
        o.put("outCats", cats(d.outCats)).put("inCats", cats(d.inCats))
        o.put("fixed", JSONArray().apply {
            d.fixed.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("amt", it.amt).put("day", it.day).put("cat", it.cat)) }
        })
        val s = d.settings
        o.put("settings", JSONObject().put("startSaved", s.startSaved).put("goal", s.goal).put("goalYm", s.goalYm)
            .put("monthly", s.monthly).put("budget", s.budget))
        return o.toString()
    }

    private fun decode(raw: String): AppData {
        val o = JSONObject(raw)
        fun <T> arr(key: String, f: (JSONObject) -> T): List<T> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        val s = o.optJSONObject("settings") ?: JSONObject()
        val def = Settings()
        return AppData(
            entries = arr("entries") {
                Entry(it.getString("id"), it.getString("t"), it.getLong("amt"), it.getString("cat"),
                    it.optString("memo"), it.getString("d"), it.optLong("ts"), if (it.has("fid")) it.getString("fid") else null)
            },
            outCats = arr("outCats") { Cat(it.getString("name"), it.optBoolean("save")) },
            inCats = arr("inCats") { Cat(it.getString("name")) },
            fixed = arr("fixed") { Fixed(it.getString("id"), it.getString("name"), it.getLong("amt"), it.getInt("day"), it.getString("cat")) },
            settings = Settings(
                s.optLong("startSaved", def.startSaved), s.optLong("goal", def.goal), s.optString("goalYm", def.goalYm),
                s.optLong("monthly", def.monthly), s.optLong("budget", def.budget),
            ),
        )
    }
}

object Calc {
    private val nf: NumberFormat = NumberFormat.getInstance(Locale.KOREA)
    fun fmt(n: Long): String = nf.format(n)
    fun man(n: Long): String {
        val m = n / 10_000.0
        return if (abs(m) >= 1) nf.format(m.roundToLong()) + "만원" else fmt(n) + "원"
    }

    fun today(): String = LocalDate.now().toString()
    fun curYm(): String = YearMonth.now().toString()
    fun shift(ym: String, d: Long): String = YearMonth.parse(ym).plusMonths(d).toString()

    fun saveNames(d: AppData): Set<String> = d.outCats.filter { it.save }.map { it.name }.toSet()
    fun isSave(e: Entry, names: Set<String>) = e.t == OUT && e.cat in names

    fun month(d: AppData, ym: String): MonthSum {
        val names = saveNames(d)
        var inc = 0L; var out = 0L; var save = 0L
        val by = LinkedHashMap<String, Long>()
        d.entries.filter { it.d.startsWith(ym) }.forEach { e ->
            when {
                e.t == IN -> inc += e.amt
                isSave(e, names) -> save += e.amt
                else -> { out += e.amt; by[e.cat] = (by[e.cat] ?: 0) + e.amt }
            }
        }
        return MonthSum(inc, out, save, by.entries.map { it.key to it.value }.sortedByDescending { it.second })
    }

    fun totalSaved(d: AppData): Long {
        val names = saveNames(d)
        return d.settings.startSaved + d.entries.filter { isSave(it, names) }.sumOf { it.amt }
    }

    fun monthsLeft(goalYm: String): Int = runCatching {
        val g = YearMonth.parse(goalYm); val c = YearMonth.now()
        max(0, (g.year - c.year) * 12 + (g.monthValue - c.monthValue) + 1)
    }.getOrDefault(0)

    fun fixedDone(d: AppData, fid: String, ym: String) = d.entries.any { it.fid == fid && it.d.startsWith(ym) }
}

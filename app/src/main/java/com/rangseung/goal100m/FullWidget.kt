package com.rangseung.goal100m

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.YearMonth

/**
 * 5x5 home-screen ledger. Every tap is a broadcast back to this receiver, which updates
 * a small UI state (tab, typed amount, selections) and redraws. Only free-text input
 * (category names, fixed-cost details) opens [WidgetEditActivity] as a popup.
 */
class FullWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.load(ctx)
        Store.syncFixed(ctx)
        render(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACT) {
            Store.load(ctx)
            handle(ctx, intent.getStringExtra("op") ?: return, intent.getStringExtra("arg") ?: "")
            render(ctx)
            return
        }
        super.onReceive(ctx, intent)
    }

    companion object {
        const val ACT = "com.rangseung.goal100m.WIDGET"

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFF9FB5AC.toInt()
        private const val DARK = 0xFF0B1A15.toInt()
        private const val C_OUT = 0xFFF08A5D.toInt()
        private const val C_IN = 0xFF7EB3F0.toInt()
        private const val C_ACC = 0xFF4FC3A1.toInt()

        private val TABS = intArrayOf(R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3)
        private val PANES = intArrayOf(R.id.paneRec, R.id.paneHist, R.id.paneCat, R.id.paneFix)
        private val KEYS = listOf(
            R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3", R.id.k4 to "4", R.id.k5 to "5",
            R.id.k6 to "6", R.id.k7 to "7", R.id.k8 to "8", R.id.k9 to "9",
            R.id.k0 to "0", R.id.k00 to "00", R.id.kBack to "back",
        )

        // ---------- UI state ----------
        private class WS(ctx: Context) {
            val p: SharedPreferences = ctx.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
            fun s(k: String): String? = p.getString(k, null)
            fun i(k: String) = p.getInt(k, 0)
            fun l(k: String) = p.getLong(k, 0)
            fun b(k: String) = p.getBoolean(k, false)
            fun put(vararg kv: Pair<String, Any?>) {
                val e = p.edit()
                kv.forEach { (k, v) ->
                    when (v) {
                        null -> e.remove(k)
                        is Int -> e.putInt(k, v)
                        is Long -> e.putLong(k, v)
                        is Boolean -> e.putBoolean(k, v)
                        else -> e.putString(k, v.toString())
                    }
                }
                e.commit()
            }
        }

        private fun recCat(w: WS, d: AppData, type: String): String? =
            w.s("cat")?.takeIf { c -> d.cats(type).any { it.name == c } } ?: d.cats(type).firstOrNull()?.name

        /** Shows a short line at the bottom of the widget. Prefix "!" marks an error. */
        fun message(ctx: Context, msg: String) {
            WS(ctx).put("msg" to msg)
            render(ctx)
        }

        /** Called by the popup after an edit so stale selections don't point at renamed items. */
        fun clearSelections(ctx: Context) {
            WS(ctx).put("csel" to null, "cconf" to false, "fsel" to null, "fconf" to false)
        }

        private fun handle(ctx: Context, op: String, arg: String) {
            val w = WS(ctx)
            val d = Store.data
            w.put("msg" to null)
            when (op) {
                "tab" -> w.put(
                    "tab" to (arg.toIntOrNull() ?: 0),
                    "esel" to null, "econf" to false, "csel" to null, "cconf" to false, "fsel" to null, "fconf" to false,
                )
                // record
                "type" -> w.put("type" to arg, "cat" to null)
                "day" -> {
                    val cur = w.s("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                    val n = cur.plusDays(arg.toLongOrNull() ?: 0)
                    w.put("date" to if (n == LocalDate.now()) null else n.toString())
                }
                "key" -> {
                    val a = w.l("amt")
                    val n = when (arg) {
                        "back" -> a / 10
                        "clear" -> 0L
                        "00" -> a * 100
                        else -> a * 10 + (arg.toLongOrNull() ?: 0)
                    }
                    if (n <= 99_999_999_999L) w.put("amt" to n)
                }
                "cat" -> w.put("cat" to arg)
                "save" -> {
                    val type = w.s("type") ?: OUT
                    val amt = w.l("amt")
                    val cat = recCat(w, d, type)
                    when {
                        amt <= 0 -> w.put("msg" to "!금액을 입력해줘")
                        cat == null -> w.put("msg" to "!카테고리 탭에서 먼저 추가해줘")
                        else -> {
                            val date = w.s("date") ?: Calc.today()
                            Store.addEntry(ctx, Entry(Store.newId(), type, amt, cat, "", date, System.currentTimeMillis()))
                            w.put("amt" to 0L, "msg" to "$cat ${Calc.fmt(amt)}원 저장했어")
                        }
                    }
                }
                // history
                "hm" -> w.put("hym" to Calc.shift(w.s("hym") ?: Calc.curYm(), arg.toLongOrNull() ?: 0), "esel" to null, "econf" to false)
                "esel" -> w.put("esel" to if (w.s("esel") == arg) null else arg, "econf" to false)
                "ecancel" -> w.put("esel" to null, "econf" to false)
                "edel" -> if (!w.b("econf")) w.put("econf" to true) else {
                    w.s("esel")?.let { Store.deleteEntry(ctx, it) }
                    w.put("esel" to null, "econf" to false, "msg" to "삭제했어")
                }
                // categories
                "ctype" -> w.put("ctype" to arg, "csel" to null, "cconf" to false)
                "csel" -> w.put("csel" to if (w.s("csel") == arg) null else arg, "cconf" to false)
                "ccancel" -> w.put("csel" to null, "cconf" to false)
                "csave" -> {
                    val t = w.s("ctype") ?: OUT
                    val c = d.cats(t).find { it.name == w.s("csel") }
                    if (c != null) {
                        Store.editCat(ctx, t, c.name, c.copy(save = !c.save))
                        w.put("msg" to if (!c.save) "'${c.name}' 저축으로 집계해" else "'${c.name}' 저축 집계 껐어")
                    }
                }
                "cdel" -> if (!w.b("cconf")) w.put("cconf" to true) else {
                    val name = w.s("csel")
                    if (name != null) Store.deleteCat(ctx, w.s("ctype") ?: OUT, name)
                    w.put("csel" to null, "cconf" to false, "msg" to "'$name' 삭제했어")
                }
                // fixed
                "fsel" -> w.put("fsel" to if (w.s("fsel") == arg) null else arg, "fconf" to false)
                "fcancel" -> w.put("fsel" to null, "fconf" to false)
                "fdel" -> if (!w.b("fconf")) w.put("fconf" to true) else {
                    w.s("fsel")?.let { Store.deleteFixed(ctx, it) }
                    w.put("fsel" to null, "fconf" to false, "msg" to "고정 지출 삭제했어")
                }
            }
        }

        // ---------- drawing ----------
        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FullWidget::class.java))
            if (ids.isEmpty()) return
            val v = build(ctx)
            ids.forEach { mgr.updateAppWidget(it, v) }
        }

        private fun bcast(ctx: Context, op: String, arg: String = ""): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, "$op|$arg".hashCode(),
                Intent(ctx, FullWidget::class.java).setAction(ACT).putExtra("op", op).putExtra("arg", arg),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun template(ctx: Context, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, FullWidget::class.java).setAction(ACT),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun popup(ctx: Context, code: Int, mode: String, type: String = OUT, name: String? = null): PendingIntent =
            PendingIntent.getActivity(
                ctx, code,
                Intent(ctx, WidgetEditActivity::class.java)
                    .putExtra(WidgetEditActivity.MODE, mode)
                    .putExtra(WidgetEditActivity.TYPE, type)
                    .putExtra(WidgetEditActivity.NAME, name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun fill(op: String, arg: String) = Intent().putExtra("op", op).putExtra("arg", arg)

        private fun toggle(v: RemoteViews, id: Int, on: Boolean) {
            v.setInt(id, "setBackgroundResource", if (on) R.drawable.wf_sel else R.drawable.wf_card)
            v.setTextColor(id, if (on) DARK else WHITE)
        }

        private fun wd(d: LocalDate) = "월화수목금토일"[d.dayOfWeek.value - 1]

        private fun row(ctx: Context, title: String, sub: String, right: String, rightColor: Int, selected: Boolean, op: String, arg: String) =
            RemoteViews(ctx.packageName, R.layout.wf_row).apply {
                setTextViewText(R.id.title, title)
                setTextViewText(R.id.sub, sub)
                setViewVisibility(R.id.sub, if (sub.isEmpty()) View.GONE else View.VISIBLE)
                setTextViewText(R.id.right, right)
                setTextColor(R.id.right, rightColor)
                setInt(R.id.row, "setBackgroundResource", if (selected) R.drawable.wf_row_sel else R.drawable.wf_row)
                setOnClickFillInIntent(R.id.row, fill(op, arg))
            }

        private fun items(list: List<Pair<Long, RemoteViews>>): RemoteViews.RemoteCollectionItems {
            val b = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            list.forEach { (id, rv) -> b.addItem(id, rv) }
            return b.build()
        }

        private fun build(ctx: Context): RemoteViews {
            val d = Store.data
            val w = WS(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_full)

            // header
            val month = Calc.month(d, Calc.curYm())
            val bud = d.settings.budget
            v.setTextViewText(
                R.id.hRemain,
                when {
                    bud <= 0 -> "이번 달 생활비 ${Calc.fmt(month.living)}원"
                    month.living <= bud -> "생활비 ${Calc.fmt(bud - month.living)}원 남음"
                    else -> "생활비 ${Calc.fmt(month.living - bud)}원 초과"
                },
            )
            v.setTextColor(R.id.hRemain, if (bud > 0 && month.living > bud) C_OUT else WHITE)
            val saved = Calc.totalSaved(d)
            val pct = if (d.settings.goal > 0) (saved.toDouble() / d.settings.goal).coerceIn(0.0, 1.0) else 0.0
            v.setTextViewText(R.id.hGoal, "${Calc.man(d.settings.goal).removeSuffix("원")} · ${"%.1f".format(pct * 100)}%")
            v.setProgressBar(R.id.gBar, 1000, (pct * 1000).toInt(), false)
            v.setOnClickPendingIntent(R.id.hRemain, PendingIntent.getActivity(
                ctx, 700, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))

            // tabs
            val tab = w.i("tab").coerceIn(0, 3)
            TABS.forEachIndexed { i, id ->
                v.setInt(id, "setBackgroundResource", if (i == tab) R.drawable.wf_sel else android.R.color.transparent)
                v.setTextColor(id, if (i == tab) DARK else MUTED)
                v.setOnClickPendingIntent(id, bcast(ctx, "tab", i.toString()))
            }
            PANES.forEachIndexed { i, id -> v.setViewVisibility(id, if (i == tab) View.VISIBLE else View.GONE) }

            when (tab) {
                0 -> buildRecord(ctx, v, w, d)
                1 -> buildHistory(ctx, v, w, d)
                2 -> buildCats(ctx, v, w, d)
                3 -> buildFixed(ctx, v, w, d)
            }

            // message line
            val msg = w.s("msg")
            v.setViewVisibility(R.id.gMsg, if (msg.isNullOrEmpty()) View.GONE else View.VISIBLE)
            if (!msg.isNullOrEmpty()) {
                val err = msg.startsWith("!")
                v.setTextViewText(R.id.gMsg, msg.removePrefix("!"))
                v.setTextColor(R.id.gMsg, if (err) C_OUT else C_ACC)
            }
            return v
        }

        private fun buildRecord(ctx: Context, v: RemoteViews, w: WS, d: AppData) {
            val type = w.s("type") ?: OUT
            toggle(v, R.id.rOut, type == OUT)
            toggle(v, R.id.rIn, type == IN)
            v.setOnClickPendingIntent(R.id.rOut, bcast(ctx, "type", OUT))
            v.setOnClickPendingIntent(R.id.rIn, bcast(ctx, "type", IN))

            val today = LocalDate.now()
            val date = w.s("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            v.setTextViewText(R.id.rDate, (if (date == today) "오늘 " else "") + "${date.monthValue}.${date.dayOfMonth} (${wd(date)})")
            v.setTextColor(R.id.rDate, if (date == today) WHITE else C_ACC)
            v.setOnClickPendingIntent(R.id.rPrev, bcast(ctx, "day", "-1"))
            v.setOnClickPendingIntent(R.id.rNext, bcast(ctx, "day", "1"))

            val amt = w.l("amt")
            v.setTextViewText(R.id.rAmt, "${Calc.fmt(amt)}원")
            v.setTextColor(R.id.rAmt, if (amt == 0L) MUTED else if (type == IN) C_IN else WHITE)

            val cats = d.cats(type)
            val cur = recCat(w, d, type)
            v.setRemoteAdapter(R.id.rCats, items(cats.map { c ->
                c.name.hashCode().toLong() to RemoteViews(ctx.packageName, R.layout.wf_chip).apply {
                    setTextViewText(R.id.chip, c.name)
                    toggle(this, R.id.chip, c.name == cur)
                    setOnClickFillInIntent(R.id.chip, fill("cat", c.name))
                }
            }))
            v.setPendingIntentTemplate(R.id.rCats, template(ctx, 901))
            v.setEmptyView(R.id.rCats, R.id.rCatsEmpty)
            v.setOnClickPendingIntent(R.id.rCatsEmpty, bcast(ctx, "tab", "2"))

            KEYS.forEach { (id, k) -> v.setOnClickPendingIntent(id, bcast(ctx, "key", k)) }
            v.setOnClickPendingIntent(R.id.rClear, bcast(ctx, "key", "clear"))
            v.setTextViewText(R.id.rSave, if (type == IN) "수입 저장" else "지출 저장")
            v.setOnClickPendingIntent(R.id.rSave, bcast(ctx, "save"))
        }

        private fun buildHistory(ctx: Context, v: RemoteViews, w: WS, d: AppData) {
            val ym = w.s("hym") ?: Calc.curYm()
            val m = YearMonth.parse(ym)
            v.setTextViewText(R.id.hMonth, "${m.year}년 ${m.monthValue}월")
            v.setOnClickPendingIntent(R.id.hPrevM, bcast(ctx, "hm", "-1"))
            v.setOnClickPendingIntent(R.id.hNextM, bcast(ctx, "hm", "1"))
            val sum = Calc.month(d, ym)
            v.setTextViewText(R.id.hSum, "수입 ${Calc.fmt(sum.inc)} · 지출 ${Calc.fmt(sum.out)} · 저축 ${Calc.fmt(sum.save)}")

            val names = Calc.saveNames(d)
            val sel = w.s("esel")
            val list = d.entries.filter { it.d.startsWith(ym) }.sortedWith(compareByDescending<Entry> { it.d }.thenByDescending { it.ts })
            v.setRemoteAdapter(R.id.hList, items(list.map { e ->
                val dt = runCatching { LocalDate.parse(e.d) }.getOrNull()
                val save = Calc.isSave(e, names)
                e.id.hashCode().toLong() to row(
                    ctx,
                    title = if (e.memo.isBlank()) e.cat else "${e.cat} · ${e.memo}",
                    sub = dt?.let { "${it.monthValue}.${it.dayOfMonth} (${wd(it)})" } ?: e.d,
                    right = (if (e.t == IN) "+" else "-") + Calc.fmt(e.amt),
                    rightColor = if (e.t == IN) C_IN else if (save) C_ACC else C_OUT,
                    selected = e.id == sel, op = "esel", arg = e.id,
                )
            }))
            v.setPendingIntentTemplate(R.id.hList, template(ctx, 902))
            v.setEmptyView(R.id.hList, R.id.hEmpty)

            val has = sel != null && list.any { it.id == sel }
            v.setViewVisibility(R.id.eBar, if (has) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.eDel, if (w.b("econf")) "정말 삭제?" else "삭제")
            v.setOnClickPendingIntent(R.id.eCancel, bcast(ctx, "ecancel"))
            v.setOnClickPendingIntent(R.id.eDel, bcast(ctx, "edel"))
        }

        private fun buildCats(ctx: Context, v: RemoteViews, w: WS, d: AppData) {
            val t = w.s("ctype") ?: OUT
            toggle(v, R.id.cOut, t == OUT)
            toggle(v, R.id.cIn, t == IN)
            v.setOnClickPendingIntent(R.id.cOut, bcast(ctx, "ctype", OUT))
            v.setOnClickPendingIntent(R.id.cIn, bcast(ctx, "ctype", IN))
            v.setOnClickPendingIntent(R.id.cAdd, popup(ctx, 801, WidgetEditActivity.CAT_ADD, t))

            val cats = d.cats(t)
            val sel = w.s("csel")?.takeIf { s -> cats.any { it.name == s } }
            val used = d.entries.groupingBy { it.t + "|" + it.cat }.eachCount()
            v.setRemoteAdapter(R.id.cList, items(cats.map { c ->
                val n = used["$t|${c.name}"] ?: 0
                c.name.hashCode().toLong() to row(
                    ctx, c.name,
                    sub = listOfNotNull(if (c.save) "저축으로 집계" else null, if (n > 0) "기록 ${n}건" else null).joinToString(" · "),
                    right = if (c.name == sel) "선택됨" else "", rightColor = C_ACC,
                    selected = c.name == sel, op = "csel", arg = c.name,
                )
            }))
            v.setPendingIntentTemplate(R.id.cList, template(ctx, 903))
            v.setEmptyView(R.id.cList, R.id.cEmpty)
            v.setOnClickPendingIntent(R.id.cEmpty, popup(ctx, 801, WidgetEditActivity.CAT_ADD, t))

            v.setViewVisibility(R.id.cBar, if (sel != null) View.VISIBLE else View.GONE)
            if (sel != null) {
                val c = cats.first { it.name == sel }
                v.setViewVisibility(R.id.cSave, if (t == OUT) View.VISIBLE else View.GONE)
                v.setTextViewText(R.id.cSave, if (c.save) "저축 집계 끄기" else "저축으로 집계")
                v.setOnClickPendingIntent(R.id.cSave, bcast(ctx, "csave"))
                v.setOnClickPendingIntent(R.id.cRename, popup(ctx, 802, WidgetEditActivity.CAT_RENAME, t, sel))
                v.setTextViewText(R.id.cDel, if (w.b("cconf")) "정말 삭제?" else "삭제")
                v.setOnClickPendingIntent(R.id.cDel, bcast(ctx, "cdel"))
            }
        }

        private fun buildFixed(ctx: Context, v: RemoteViews, w: WS, d: AppData) {
            val list = d.fixed.sortedBy { it.day }
            v.setTextViewText(R.id.fTotal, if (list.isEmpty()) "고정 지출" else "매달 ${Calc.fmt(list.sumOf { it.amt })}원")
            v.setOnClickPendingIntent(R.id.fAdd, popup(ctx, 803, WidgetEditActivity.FIXED))

            val sel = w.s("fsel")?.takeIf { s -> list.any { it.id == s } }
            v.setRemoteAdapter(R.id.fList, items(list.map { f ->
                f.id.hashCode().toLong() to row(
                    ctx, f.name,
                    sub = "매달 ${f.day}일 · ${f.cat} · 자동 기록",
                    right = "${Calc.fmt(f.amt)}원", rightColor = WHITE,
                    selected = f.id == sel, op = "fsel", arg = f.id,
                )
            }))
            v.setPendingIntentTemplate(R.id.fList, template(ctx, 904))
            v.setEmptyView(R.id.fList, R.id.fEmpty)
            v.setOnClickPendingIntent(R.id.fEmpty, popup(ctx, 803, WidgetEditActivity.FIXED))

            v.setViewVisibility(R.id.fBar, if (sel != null) View.VISIBLE else View.GONE)
            if (sel != null) {
                v.setOnClickPendingIntent(R.id.fCancel, bcast(ctx, "fcancel"))
                v.setOnClickPendingIntent(R.id.fEdit, popup(ctx, 804, WidgetEditActivity.FIXED, name = sel))
                v.setTextViewText(R.id.fDel, if (w.b("fconf")) "정말 삭제?" else "삭제")
                v.setOnClickPendingIntent(R.id.fDel, bcast(ctx, "fdel"))
            }
        }
    }
}

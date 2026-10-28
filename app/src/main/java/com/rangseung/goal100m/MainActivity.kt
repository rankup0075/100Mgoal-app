package com.rangseung.goal100m

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.load(this)
        setContent { AppTheme { MainScreen() } }
    }

    // A new month may have started while the app sat in the background.
    override fun onResume() {
        super.onResume()
        Store.syncFixed(this)
    }
}

private const val PAGE = 3

private sealed interface Sheet {
    data object Add : Sheet
    data object Goal : Sheet
    data class EditFixed(val f: Fixed?) : Sheet
    data object AllFixed : Sheet
    data object AllEntries : Sheet
    data class Day(val date: String) : Sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val ctx = LocalContext.current
    val data = Store.data
    var ym by rememberSaveable { mutableStateOf(Calc.curYm()) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var catsFor by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var fixedShown by rememberSaveable { mutableStateOf(PAGE) }
    var entriesShown by rememberSaveable(ym) { mutableStateOf(PAGE) }
    val sum = Calc.month(data, ym)
    val monthEntries = data.entries.filter { it.d.startsWith(ym) }
        .sortedWith(compareByDescending<Entry> { it.d }.thenByDescending { it.ts })
    val fixed = data.fixed.sortedBy { it.day }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    val toggle: (String) -> Unit = { openId = if (openId == it) null else it }
    val delete: (String) -> Unit = { Store.deleteEntry(ctx, it); openId = null; toast("삭제했어") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(16.dp, 8.dp, 16.dp, 12.dp)) {
                Button(
                    onClick = { sheet = Sheet.Add },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
                ) { Text("+ 기록하기", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("가계부", fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("이 폰에만 저장 · 광고 없음", fontSize = 12.sp, color = muted)
                    }
                    OutlinedButton({ catsFor = OUT }, shape = RoundedCornerShape(10.dp)) { Text("카테고리", fontSize = 13.sp) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton({ sheet = Sheet.Goal }, shape = RoundedCornerShape(10.dp)) { Text("목표", fontSize = 13.sp) }
                }
            }
            item { GoalCard(data) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ ym = Calc.shift(ym, -1); openId = null }) { Text("‹", fontSize = 22.sp, color = muted) }
                    val m = java.time.YearMonth.parse(ym)
                    Text("${m.year}년 ${m.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton({ ym = Calc.shift(ym, 1); openId = null }) { Text("›", fontSize = 22.sp, color = muted) }
                }
            }
            item { SummaryRow(sum) }
            item { BudgetCard(sum, data.settings) }
            item {
                Card(
                    "고정 지출" + if (fixed.isNotEmpty()) " · 월 ${Calc.fmt(fixed.sumOf { it.amt })}원" else "",
                    action = if (fixed.isNotEmpty()) "전체보기" else null, onAction = { sheet = Sheet.AllFixed },
                ) {
                    if (fixed.isEmpty()) Text("통신비, 구독료, 적금처럼 매달 나가는 돈을 등록해둬. 매달 결제일에 맞춰 자동으로 기록돼.", fontSize = 14.sp, color = muted)
                    FixedRows(fixed.take(fixedShown)) { sheet = Sheet.EditFixed(it) }
                    MoreButton(fixed.size, fixedShown) { fixedShown = it }
                    OutlinedButton({ sheet = Sheet.EditFixed(null) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Text("+ 고정 지출 추가")
                    }
                }
            }
            item {
                Card("카테고리별 지출") {
                    if (sum.byCat.isEmpty()) Text("아직 지출이 없어", color = muted, fontSize = 14.sp)
                    val max = sum.byCat.firstOrNull()?.second ?: 1L
                    sum.byCat.forEach { (c, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(c, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp))
                            Bar(v.toFloat() / max, outColor.copy(alpha = 0.75f), Modifier.weight(1f))
                            Text(Calc.fmt(v), fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                Card("내역", action = if (monthEntries.isNotEmpty()) "전체보기" else null, onAction = { sheet = Sheet.AllEntries }) {
                    if (monthEntries.isEmpty()) Text("이번 달 기록이 없어.\n아래 + 기록하기로 첫 기록을 남겨봐.", color = muted, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    EntryRows(data, monthEntries.take(entriesShown), openId, toggle, delete)
                    MoreButton(monthEntries.size, entriesShown) { entriesShown = it }
                }
            }
            item { CalendarCard(data, ym) { sheet = Sheet.Day(it) } }
        }
    }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    sheet?.let { s ->
        ModalBottomSheet(onDismissRequest = { sheet = null; openId = null }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
            when (s) {
                Sheet.Add -> AddEntryForm(
                    initialType = OUT,
                    defaultDate = if (ym == Calc.curYm()) Calc.today() else "$ym-01",
                    onSaved = { e -> ym = e.d.substring(0, 7); sheet = null; toast("${e.cat} ${Calc.fmt(e.amt)}원 저장") },
                    onClose = { sheet = null },
                )
                Sheet.Goal -> GoalForm(data.settings, onClose = { sheet = null }) {
                    Store.saveSettings(ctx, it); sheet = null; toast("목표 저장했어")
                }
                is Sheet.EditFixed -> FixedForm(s.f, onEditCats = { catsFor = OUT }, onClose = { sheet = null }) { msg ->
                    sheet = null; toast(msg)
                }
                Sheet.AllFixed -> SheetList("고정 지출 전체 (${fixed.size})", "매달 합계 ${Calc.fmt(fixed.sumOf { it.amt })}원") {
                    FixedRows(fixed) { sheet = Sheet.EditFixed(it) }
                }
                Sheet.AllEntries -> {
                    val m = java.time.YearMonth.parse(ym)
                    SheetList("${m.monthValue}월 내역 전체 (${monthEntries.size})", "수입 ${Calc.fmt(sum.inc)} · 지출 ${Calc.fmt(sum.out)} · 저축 ${Calc.fmt(sum.save)}") {
                        EntryRows(data, monthEntries, openId, toggle, delete)
                    }
                }
                is Sheet.Day -> {
                    val list = data.entries.filter { it.d == s.date }.sortedByDescending { it.ts }
                    val dt = LocalDate.parse(s.date)
                    val inc = list.filter { it.t == IN }.sumOf { it.amt }
                    val names = Calc.saveNames(data)
                    val out = list.filter { it.t == OUT && !Calc.isSave(it, names) }.sumOf { it.amt }
                    SheetList("${dt.monthValue}월 ${dt.dayOfMonth}일 (${"월화수목금토일"[dt.dayOfWeek.value - 1]})", "수입 ${Calc.fmt(inc)} · 지출 ${Calc.fmt(out)}") {
                        if (list.isEmpty()) Text("이 날은 기록이 없어.", color = muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                        EntryRows(data, list, openId, toggle, delete, showDate = false)
                    }
                }
            }
        }
    }
    catsFor?.let { CategoryManager(it) { catsFor = null } }
}

// ---------- cards ----------
@Composable
private fun Card(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (action != null) Text(
                    action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            content()
        }
    }
}

/** Shows [PAGE] more each tap; once everything is shown it collapses back to [PAGE]. Hidden when there are [PAGE] or fewer. */
@Composable
private fun MoreButton(total: Int, shown: Int, onChange: (Int) -> Unit) {
    if (total <= PAGE) return
    val all = shown >= total
    TextButton(
        onClick = { onChange(if (all) PAGE else shown + PAGE) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (all) "접기 ▲" else "더보기 (${total - shown}개 남음) ▼", color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun SheetList(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.outlineVariant, h: Int = 8) {
    Box(modifier.height(h.dp).clip(RoundedCornerShape(99.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

@Composable
private fun GoalCard(d: AppData) {
    val s = d.settings
    val saved = Calc.totalSaved(d)
    val pct = if (s.goal > 0) (saved.toDouble() / s.goal).coerceIn(0.0, 1.0) else 0.0
    val left = Calc.monthsLeft(s.goalYm)
    val remain = (s.goal - saved).coerceAtLeast(0)
    val fg = MaterialTheme.colorScheme.onPrimary
    val gy = s.goalYm.split("-")
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Calc.man(s.goal).removeSuffix("원")} 모으기 · ${gy.getOrNull(0)}년 ${gy.getOrNull(1)?.toIntOrNull()}월까지", fontSize = 12.sp, color = fg.copy(alpha = .85f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Calc.man(saved), fontSize = 30.sp, fontWeight = FontWeight.Black, color = fg)
                Text("  ${"%.1f".format(pct * 100)}%", fontSize = 14.sp, color = fg.copy(alpha = .85f), modifier = Modifier.padding(bottom = 4.dp))
            }
            Bar(pct.toFloat(), fg, track = fg.copy(alpha = .25f), h = 10)
            Row {
                Text("남은 금액 ${Calc.man(remain)}", fontSize = 13.sp, color = fg, modifier = Modifier.weight(1f))
                Text(if (left > 0) "월 ${Calc.man(remain / left)}씩 · ${left}개월" else "목표 달 지남", fontSize = 13.sp, color = fg)
            }
        }
    }
}

@Composable
private fun SummaryRow(sum: MonthSum) {
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            listOf(Triple("수입", sum.inc, inColor), Triple("지출", sum.out, outColor), Triple("저축", sum.save, MaterialTheme.colorScheme.primary))
                .forEachIndexed { i, (label, v, c) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Column(Modifier.weight(1f).padding(12.dp)) {
                        Text(label, fontSize = 12.sp, color = muted)
                        Text(Calc.fmt(v), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
        }
    }
}

@Composable
private fun BudgetCard(sum: MonthSum, s: Settings) {
    Card("생활비 예산 · 고정 지출 제외") {
        val living = sum.living
        val ratio = if (s.budget > 0) living.toFloat() / s.budget else 0f
        Bar(ratio, if (ratio > 1) outColor else MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())
        Text(
            when {
                s.budget <= 0 -> "예산 미설정"
                living <= s.budget -> "${Calc.fmt(living)} / ${Calc.fmt(s.budget)}원 · ${Calc.fmt(s.budget - living)}원 남음"
                else -> "${Calc.fmt(living - s.budget)}원 초과"
            }, fontSize = 13.sp, color = muted,
        )
        if (sum.fixedOut > 0) Text("고정 지출 ${Calc.fmt(sum.fixedOut)}원은 따로 계산돼", fontSize = 13.sp, color = muted)
        Text("이번 달 저축 ${Calc.fmt(sum.save)} / 목표 ${Calc.fmt(s.monthly)}원" +
            if (sum.inc > 0) " · 남은 돈 ${Calc.fmt(sum.inc - sum.out - sum.save)}원" else "", fontSize = 13.sp, color = muted)
    }
}

@Composable
private fun FixedRows(list: List<Fixed>, onOpen: (Fixed) -> Unit) {
    list.forEach { f ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("매달 ${f.day}일 · ${f.cat}", fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${Calc.fmt(f.amt)}원")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Pill(text: String, on: Boolean) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, fontSize = 11.sp, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else muted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp), maxLines = 1)
    }
}

/** Entries grouped under date headers, in the order given. Tap a row to reveal delete. */
@Composable
private fun EntryRows(
    d: AppData, entries: List<Entry>, openId: String?, onToggle: (String) -> Unit, onDelete: (String) -> Unit,
    showDate: Boolean = true,
) {
    val names = Calc.saveNames(d)
    val groups = LinkedHashMap<String, MutableList<Entry>>()
    entries.forEach { groups.getOrPut(it.d) { mutableListOf() } += it }
    groups.forEach { (day, list) ->
        Column {
            if (showDate) {
                val dt = runCatching { LocalDate.parse(day) }.getOrNull()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    Text(dt?.let { "${it.monthValue}.${it.dayOfMonth} (${"월화수목금토일"[it.dayOfWeek.value - 1]})" } ?: day,
                        fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            list.forEach { e ->
                val save = Calc.isSave(e, names)
                val color = when { e.t == IN -> inColor; save -> MaterialTheme.colorScheme.primary; else -> outColor }
                Row(Modifier.fillMaxWidth().clickable { onToggle(e.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(e.cat, save)
                    Text(e.memo + if (e.fid != null) " · 고정" else "", modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (e.t == IN) "+" else "-") + Calc.fmt(e.amt), color = color, fontWeight = FontWeight.SemiBold)
                }
                if (openId == e.id) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton({ onToggle(e.id) }) { Text("취소") }
                    OutlinedButton({ onDelete(e.id) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = outColor),
                        border = BorderStroke(1.dp, outColor)) { Text("삭제") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Month grid; each day shows its income (+) and spending (-). Savings are left out of spending. Tap a day for its list. */
@Composable
private fun CalendarCard(d: AppData, ym: String, onDay: (String) -> Unit) {
    val m = java.time.YearMonth.parse(ym)
    val names = Calc.saveNames(d)
    val inc = HashMap<String, Long>()
    val out = HashMap<String, Long>()
    d.entries.filter { it.d.startsWith(ym) }.forEach { e ->
        when {
            e.t == IN -> inc[e.d] = (inc[e.d] ?: 0) + e.amt
            !Calc.isSave(e, names) -> out[e.d] = (out[e.d] ?: 0) + e.amt
        }
    }
    val today = LocalDate.now()
    val lead = m.atDay(1).dayOfWeek.value % 7 // Sunday first
    val cells: List<Int?> = List(lead) { null } + (1..m.lengthOfMonth()).toList()
    Card("${m.monthValue}월 달력") {
        Row {
            "일월화수목금토".forEachIndexed { i, c ->
                Text(c.toString(), fontSize = 11.sp, color = if (i == 0) outColor.copy(alpha = .8f) else muted,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    if (day == null) Spacer(Modifier.weight(1f).height(56.dp))
                    else {
                        val date = m.atDay(day)
                        val key = date.toString()
                        val isToday = date == today
                        Column(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { onDay(key) }.padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(day.toString(), fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else if (i == 0) outColor.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurface)
                            inc[key]?.let { Text("+" + Calc.short(it), fontSize = 9.sp, color = inColor, maxLines = 1, lineHeight = 11.sp) }
                            out[key]?.let { Text("-" + Calc.short(it), fontSize = 9.sp, color = outColor, maxLines = 1, lineHeight = 11.sp) }
                        }
                    }
                }
            }
        }
        val mi = inc.values.sum(); val mo = out.values.sum()
        Text("이번 달 수입 ${Calc.fmt(mi)} · 지출 ${Calc.fmt(mo)}", fontSize = 12.sp, color = muted)
    }
}

// ---------- sheet forms ----------
@Composable
private fun GoalForm(s: Settings, onClose: () -> Unit, onSave: (Settings) -> Unit) {
    var start by remember { mutableStateOf(s.startSaved) }
    var goal by remember { mutableStateOf(s.goal) }
    var goalYm by remember { mutableStateOf(s.goalYm) }
    var monthly by remember { mutableStateOf(s.monthly) }
    var budget by remember { mutableStateOf(s.budget) }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("목표 설정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        MoneyField(start, { start = it }, "이미 모아둔 돈")
        MoneyField(goal, { goal = it }, "목표 금액")
        OutlinedTextField(goalYm, { goalYm = it.take(7); err = null }, label = { Text("목표 달 (예: 2030-10)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        MoneyField(monthly, { monthly = it }, "월 저축 목표")
        MoneyField(budget, { budget = it }, "월 생활비 예산")
        Text("카테고리 편집에서 '저축으로 집계'를 켠 지출 카테고리가 목표 금액에 쌓여.", fontSize = 13.sp, color = muted)
        err?.let { Text(it, color = outColor, fontSize = 13.sp) }
        Actions("닫기", onClose, "저장") {
            if (!Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(goalYm)) err = "목표 달은 2030-10 형식으로 써줘"
            else onSave(Settings(start, if (goal > 0) goal else 100_000_000, goalYm, monthly, budget))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun FixedForm(f: Fixed?, onEditCats: () -> Unit, onClose: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(f?.name ?: "") }
    var amt by remember { mutableStateOf(f?.amt ?: 0L) }
    var day by remember { mutableStateOf(f?.day?.toString() ?: "") }
    var cat by remember { mutableStateOf(f?.cat) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val cats = Store.data.outCats
    val selected = cat?.takeIf { c -> cats.any { it.name == c } } ?: cats.firstOrNull()?.name

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (f == null) "고정 지출 추가" else "고정 지출 수정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it.take(20); err = null }, label = { Text("이름 (예: 휴대폰 요금)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MoneyField(amt, { amt = it; err = null }, "금액")
        Block("카테고리", action = "편집", onAction = onEditCats) { CatChips(cats, selected) { cat = it } }
        OutlinedTextField(day, { v -> day = v.filter { it.isDigit() }.take(2); err = null }, label = { Text("매달 결제일 (1~31)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        err?.let { Text(it, color = outColor, fontSize = 13.sp) }
        Actions("닫기", onClose, "저장") {
            val dn = day.toIntOrNull()
            err = when {
                name.isBlank() -> "이름을 입력해줘"
                amt <= 0 -> "금액을 입력해줘"
                selected == null -> "카테고리를 골라줘"
                dn == null || dn !in 1..31 -> "결제일은 1~31 사이로"
                else -> null
            }
            if (err == null) {
                Store.upsertFixed(ctx, Fixed(f?.id ?: ("f" + Store.newId()), name.trim(), amt, dn!!, selected!!))
                onDone(if (f == null) "고정 지출 추가했어" else "수정했어")
            }
        }
        if (f != null) {
            if (!confirm) OutlinedButton({ confirm = true }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = outColor), border = BorderStroke(1.dp, outColor)) { Text("이 고정 지출 삭제") }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ confirm = false }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("취소") }
                OutlinedButton({ Store.deleteFixed(ctx, f.id); onDone("고정 지출 삭제했어") }, Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = outColor),
                    border = BorderStroke(1.dp, outColor)) { Text("정말 삭제") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

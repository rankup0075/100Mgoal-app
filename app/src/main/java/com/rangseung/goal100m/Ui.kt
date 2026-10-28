package com.rangseung.goal100m

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------- theme ----------
private val Light = lightColorScheme(
    primary = Color(0xFF0E6B57), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE7), onPrimaryContainer = Color(0xFF0E6B57),
    secondaryContainer = Color(0xFF1B2420), onSecondaryContainer = Color(0xFFF3F4EF),
    background = Color(0xFFF3F4EF), onBackground = Color(0xFF1B2420),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B2420),
    surfaceVariant = Color(0xFFF3F4EF), onSurfaceVariant = Color(0xFF69736E),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFC9CEC6), outlineVariant = Color(0xFFDDE1DA),
    error = Color(0xFFB4441C),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF4FC3A1), onPrimary = Color(0xFF0B1A15),
    primaryContainer = Color(0xFF1E3A31), onPrimaryContainer = Color(0xFF4FC3A1),
    secondaryContainer = Color(0xFFE6ECE8), onSecondaryContainer = Color(0xFF121815),
    background = Color(0xFF121815), onBackground = Color(0xFFE6ECE8),
    surface = Color(0xFF1A221E), onSurface = Color(0xFFE6ECE8),
    surfaceVariant = Color(0xFF121815), onSurfaceVariant = Color(0xFF93A099),
    surfaceContainerLow = Color(0xFF1A221E), surfaceContainerHigh = Color(0xFF1A221E),
    surfaceContainer = Color(0xFF1A221E),
    outline = Color(0xFF3A4540), outlineVariant = Color(0xFF2B3530),
    error = Color(0xFFF08A5D),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)

val outColor: Color @Composable get() = MaterialTheme.colorScheme.error
val inColor: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7EB3F0) else Color(0xFF1F5FA8)
val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// ---------- shared pieces ----------
@Composable
fun Block(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.weight(1f))
                if (action != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeToggle(type: String, onChange: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(OUT to "지출", IN to "수입").forEachIndexed { i, (k, label) ->
            SegmentedButton(
                selected = type == k, onClick = { onChange(k) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(label) }
        }
    }
}

/** Digits-only money field that shows thousands separators. */
@Composable
fun MoneyField(value: Long, onChange: (Long) -> Unit, label: String? = null, big: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    val text = if (value > 0) Calc.fmt(value) else ""
    OutlinedTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { tv -> onChange(tv.text.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0) },
        label = label?.let { { Text(it) } },
        placeholder = { Text("0") },
        suffix = { Text("원") },
        singleLine = true,
        textStyle = if (big) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatChips(cats: List<Cat>, selected: String?, onSelect: (String) -> Unit) {
    if (cats.isEmpty()) {
        Text("카테고리가 없어. 오른쪽 위 편집에서 먼저 추가해줘.", fontSize = 13.sp, color = muted)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cats.forEach { c ->
            FilterChip(selected = c.name == selected, onClick = { onSelect(c.name) },
                label = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
fun DateButton(date: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
            DatePickerDialog(ctx, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
                d.year, d.monthValue - 1, d.dayOfMonth).show()
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val d = runCatching { LocalDate.parse(date) }.getOrNull()
        Text(d?.let { "${it.year}년 ${it.monthValue}월 ${it.dayOfMonth}일 (${"월화수목금토일"[it.dayOfWeek.value - 1]})" } ?: date)
    }
}

@Composable
fun Actions(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text(left) }
        Button(onClick = onRight, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
            Text(right, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- add entry (used by the app sheet and the widget popup) ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEntryForm(initialType: String, defaultDate: String, onSaved: (Entry) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val data = Store.data
    var type by remember { mutableStateOf(initialType) }
    var amt by remember { mutableStateOf(0L) }
    var cat by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(defaultDate) }
    var memo by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var editCats by remember { mutableStateOf(false) }
    val cats = data.cats(type)
    val selected = cat?.takeIf { c -> cats.any { it.name == c } } ?: cats.firstOrNull()?.name

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TypeToggle(type) { type = it; cat = null }

        Block("금액") {
            MoneyField(amt, { amt = it; err = null }, big = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1_000L to "+1천", 10_000L to "+1만", 50_000L to "+5만", 100_000L to "+10만", 0L to "지우기").forEach { (v, l) ->
                    OutlinedButton(onClick = { amt = if (v == 0L) 0 else amt + v }, shape = RoundedCornerShape(99.dp)) { Text(l, fontSize = 13.sp) }
                }
            }
        }

        Block("카테고리", action = "편집", onAction = { editCats = true }) {
            CatChips(cats, selected) { cat = it; err = null }
        }

        Block("날짜 · 메모") {
            DateButton(date) { date = it }
            OutlinedTextField(
                value = memo, onValueChange = { memo = it.take(40) },
                placeholder = { Text("메모 (예: 점심 김밥)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        err?.let { Text(it, color = outColor, fontSize = 13.sp) }
        Actions("닫기", onClose, "저장") {
            when {
                amt <= 0 -> err = "금액을 입력해줘"
                selected == null -> err = "카테고리를 골라줘"
                else -> {
                    val e = Entry(Store.newId(), type, amt, selected, memo.trim(), date, System.currentTimeMillis())
                    Store.addEntry(ctx, e)
                    onSaved(e)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (editCats) CategoryManager(type) { editCats = false }
}

// ---------- category manager ----------
@Composable
fun CategoryManager(initialType: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(initialType) }
    var newName by remember { mutableStateOf("") }
    var newSave by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSave by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val list = Store.data.cats(type)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("카테고리 편집", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TypeToggle(type) { type = it; editing = null; err = null }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it.take(12); err = null },
                        placeholder = { Text("새 카테고리 이름") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        err = Store.addCat(ctx, type, Cat(newName.trim(), type == OUT && newSave))
                        if (err == null) { newName = ""; newSave = false }
                    }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(56.dp)) { Text("추가") }
                }
                if (type == OUT) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { newSave = !newSave }) {
                    Checkbox(checked = newSave, onCheckedChange = { newSave = it })
                    Text("저축으로 집계 (목표 금액에 쌓임)", fontSize = 13.sp, color = muted)
                }
                err?.let { Text(it, color = outColor, fontSize = 13.sp) }

                if (list.isEmpty()) Text("아직 카테고리가 없어. 위에서 추가해줘.", color = muted, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp))
                Column {
                    list.forEach { c ->
                        if (editing == c.name) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(editName, { editName = it.take(12) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    if (type == OUT) Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = editSave, onCheckedChange = { editSave = it })
                                        Text("저축으로 집계", fontSize = 13.sp)
                                    }
                                    if (confirmDel) {
                                        Text("삭제해도 이미 기록된 내역은 그대로 남아.", fontSize = 13.sp, color = muted)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton({ confirmDel = false }, Modifier.weight(1f)) { Text("취소") }
                                            OutlinedButton(
                                                { Store.deleteCat(ctx, type, c.name); editing = null; confirmDel = false },
                                                Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = outColor),
                                                border = BorderStroke(1.dp, outColor),
                                            ) { Text("정말 삭제") }
                                        }
                                    } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton({ confirmDel = true }, Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = outColor),
                                            border = BorderStroke(1.dp, outColor)) { Text("삭제") }
                                        OutlinedButton({ editing = null; err = null }, Modifier.weight(1f)) { Text("취소") }
                                        Button({
                                            err = Store.editCat(ctx, type, c.name, Cat(editName.trim(), type == OUT && editSave))
                                            if (err == null) editing = null
                                        }, Modifier.weight(1f)) { Text("저장") }
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    editing = c.name; editName = c.name; editSave = c.save; confirmDel = false; err = null
                                }.padding(vertical = 14.dp, horizontal = 2.dp),
                            ) {
                                Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.save) {
                                    Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("저축", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("수정", fontSize = 12.sp, color = muted)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                OutlinedButton(onClose, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("완료") }
            }
        }
    }
}

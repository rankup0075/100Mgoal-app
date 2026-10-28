package com.rangseung.goal100m

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small popup over the home screen for the parts of the 5x5 widget that need typing. */
class WidgetEditActivity : ComponentActivity() {
    companion object {
        const val MODE = "mode"
        const val TYPE = "type"
        const val NAME = "name"
        const val CAT_ADD = "cat_add"
        const val CAT_RENAME = "cat_rename"
        const val FIXED = "fixed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.load(this)
        val mode = intent.getStringExtra(MODE) ?: CAT_ADD
        val type = intent.getStringExtra(TYPE) ?: OUT
        val name = intent.getStringExtra(NAME)

        fun done(msg: String) {
            FullWidget.clearSelections(this)
            FullWidget.message(this, msg)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            finish()
        }

        setContent {
            AppTheme {
                var editCats by remember { mutableStateOf(false) }
                PopupSheet(onDismiss = { finish() }) {
                    when (mode) {
                        CAT_ADD -> CatForm(type, null, onClose = { finish() }, onDone = ::done)
                        CAT_RENAME -> {
                            val old = Store.data.cats(type).find { it.name == name }
                            if (old == null) LaunchedEffect(Unit) { finish() }
                            else CatForm(type, old, onClose = { finish() }, onDone = ::done)
                        }
                        FIXED -> FixedForm(
                            Store.data.fixed.find { it.id == name },
                            onEditCats = { editCats = true },
                            onClose = { finish() },
                            onDone = ::done,
                        )
                    }
                }
                if (editCats) CategoryManager(OUT) { editCats = false }
            }
        }
    }
}

@Composable
private fun PopupSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) {}
                .imePadding().heightIn(max = maxH),
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun CatForm(type: String, old: Cat?, onClose: () -> Unit, onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(old?.name ?: "") }
    var save by remember { mutableStateOf(old?.save ?: false) }
    var err by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            (if (type == OUT) "지출" else "수입") + if (old == null) " 카테고리 추가" else " 카테고리 수정",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(12); err = null },
            label = { Text("카테고리 이름") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (type == OUT) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { save = !save }) {
            Checkbox(checked = save, onCheckedChange = { save = it })
            Text("저축으로 집계 (목표 금액에 쌓임)", fontSize = 13.sp, color = muted)
        }
        if (old != null) Text("이름을 바꾸면 지난 내역과 고정 지출에도 같이 바뀌어.", fontSize = 13.sp, color = muted)
        err?.let { Text(it, color = outColor, fontSize = 13.sp) }
        Actions("닫기", onClose, "저장") {
            val c = Cat(name.trim(), type == OUT && save)
            err = if (old == null) Store.addCat(ctx, type, c) else Store.editCat(ctx, type, old.name, c)
            if (err == null) onDone(if (old == null) "'${c.name}' 추가했어" else "수정했어")
        }
        Spacer(Modifier.height(8.dp))
    }
}

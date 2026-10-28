package com.rangseung.goal100m

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The widget's "+ 지출 / + 수입" popup: a bottom sheet over the home screen, no full app. */
class QuickAddActivity : ComponentActivity() {
    companion object { const val EXTRA_TYPE = "type" }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.load(this)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: OUT
        setContent {
            AppTheme {
                val maxH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(remember { MutableInteractionSource() }, null) { finish() },
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clickable(remember { MutableInteractionSource() }, null) {}
                            .imePadding().heightIn(max = maxH),
                    ) {
                        Column(Modifier.navigationBarsPadding().padding(top = 16.dp)) {
                            Text(
                                if (type == OUT) "지출 기록" else "수입 기록",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            AddEntryForm(
                                initialType = type,
                                defaultDate = Calc.today(),
                                onSaved = { e ->
                                    Toast.makeText(this@QuickAddActivity, "${e.cat} ${Calc.fmt(e.amt)}원 저장", Toast.LENGTH_SHORT).show()
                                    finish()
                                },
                                onClose = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }
}

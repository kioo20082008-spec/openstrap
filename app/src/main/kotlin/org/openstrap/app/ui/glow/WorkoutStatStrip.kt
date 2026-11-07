package org.openstrap.app.ui.glow

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.R
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.AppColors

/**
 * The workout's three headline figures in one quiet strip, under the heart rate (board "Heart first"): effort, active
 * kcal and the highest heart rate, in thin numerals that count up from zero when the page opens. Needs two of them.
 */
@Composable
internal fun WorkoutStatStrip(effort: Int?, kcal: Int?, maxHeartRate: Int?, modifier: Modifier = Modifier) {
    val figures = listOfNotNull(
        effort?.let { R.string.glow_strip_effort to it },
        kcal?.let { R.string.glow_strip_kcal to it },
        maxHeartRate?.let { R.string.glow_strip_max to it },
    )
    if (figures.size < 2) return
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    QuietPanel(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            figures.forEach { (label, value) ->
                val shown by animateIntAsState(if (go) value else 0, tween(900), label = "strip count")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ltr {
                        Text(
                            Units.count(shown), color = AppColors.ink,
                            style = GlowType.rowValue.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraLight),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        stringResource(label), color = AppColors.ink.copy(alpha = GlowType.SECONDARY_ALPHA),
                        style = GlowType.secondary, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

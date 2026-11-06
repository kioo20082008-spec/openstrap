package org.openstrap.app.ui.glow

import androidx.compose.animation.core.Animatable
import org.openstrap.app.ui.theme.AppColors
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.openstrap.app.R
import org.openstrap.app.ui.theme.AppPalette
import org.openstrap.app.ui.theme.NumberFamily

private val Muted: Color get() = AppColors.muted
private const val INHALE_MS = 4_000
private const val EXHALE_MS = 6_000

/** The session lengths offered, in minutes. */
private val Lengths = listOf(1, 2, 5)

/**
 * A breathing session: six breaths a minute (in for 4 s, out for 6 s), a circle that grows and shrinks with you, the
 * time left round it, and your heart rate live from the strap, with how far it fell when the session ends. Live heart
 * rate is turned on for the session and back off after if it was off.
 */
@Composable
fun BreatheScreen(liveBpm: Int?, liveWasOn: Boolean, setLive: (Boolean) -> Unit, onClose: () -> Unit) {
    var minutes by remember { mutableIntStateOf(2) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    var startBpm by remember { mutableStateOf<Int?>(null) }
    var endBpm by remember { mutableStateOf<Int?>(null) }
    var inhale by remember { mutableStateOf(true) }
    val bpm by rememberUpdatedState(liveBpm)
    val scale = remember { Animatable(0.55f) }

    DisposableEffect(Unit) {
        setLive(true)
        onDispose { if (!liveWasOn) setLive(false) }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        startBpm = bpm
        val total = minutes * 60_000
        val end = System.currentTimeMillis() + total
        while (System.currentTimeMillis() < end) {
            inhale = true
            scale.animateTo(1f, tween(INHALE_MS, easing = FastOutSlowInEasing))
            inhale = false
            scale.animateTo(0.55f, tween(EXHALE_MS, easing = FastOutSlowInEasing))
        }
        endBpm = bpm
        running = false
        done = true
    }
    // The live stream needs a moment after Start: the first reading that comes is the session's start.
    LaunchedEffect(running, liveBpm) { if (running && startBpm == null && liveBpm != null) startBpm = liveBpm }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        left = minutes * 60
        while (left > 0 && running) { delay(1000); left-- }
    }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Full screen in its own window: its bar icons follow the theme as the activity's do (dark on Cream light).
        val view = androidx.compose.ui.platform.LocalView.current
        val light = AppColors.light
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        Column(
            Modifier.fillMaxSize().background(AppColors.page).statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.breathe_title), Modifier.weight(1f), color = AppColors.ink, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(
                    stringResource(R.string.breathe_close), Modifier.clip(CircleShape).clickable(onClick = onClose).padding(10.dp),
                    color = Muted, style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(300.dp)) {
                    val r = size.minDimension / 2
                    val share = if (running) left / (minutes * 60f) else 1f
                    drawArc(AppColors.ink.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(4.dp.toPx()))
                    drawArc(AppPalette.Coral, -90f, 360f * share, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB3A8), AppPalette.Coral, Color(0xFFD9483A))), r * 0.82f * scale.value)
                    drawCircle(AppPalette.Coral.copy(alpha = 0.18f), r * 0.82f * (scale.value + 0.12f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            done -> stringResource(R.string.breathe_done)
                            !running -> stringResource(R.string.breathe_ready)
                            inhale -> stringResource(R.string.breathe_in)
                            else -> stringResource(R.string.breathe_out)
                        },
                        color = AppPalette.Navy, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    )
                    if (running) Text(
                        "%d:%02d".format(java.util.Locale.US, left / 60, left % 60), color = AppPalette.Navy.copy(alpha = 0.7f),
                        style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val heart = when {
                done && startBpm != null && endBpm != null -> stringResource(R.string.breathe_result, startBpm!!, endBpm!!)
                liveBpm != null -> stringResource(R.string.breathe_live, liveBpm)
                else -> stringResource(R.string.breathe_waiting)
            }
            Text(heart, color = AppColors.ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            if (!running) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Lengths.forEach { m ->
                        val on = m == minutes
                        Text(
                            stringResource(R.string.breathe_minutes, m),
                            Modifier.clip(CircleShape).background(if (on) AppColors.ink else AppColors.control)
                                .clickable { minutes = m }.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = if (on) AppColors.onInk else AppColors.ink, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(29.dp)).background(if (running) AppColors.control else AppPalette.Coral)
                    .clickable { if (running) { running = false; done = false } else { done = false; endBpm = null; startBpm = null; running = true } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (running) R.string.breathe_stop else if (done) R.string.breathe_again else R.string.breathe_start),
                    // Cream on coral in every theme; on the control surface while it runs, the theme's ink.
                    color = if (running) AppColors.ink else AppPalette.Cream, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

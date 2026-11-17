package org.openstrap.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import org.openstrap.app.ui.theme.AppColors
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.launch
import org.openstrap.app.ui.glow.bounceClick
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.ui.input.pointer.pointerInput
import org.openstrap.app.ui.glow.GlowType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize

/**
 * A tab cell's shape in the plain bar and the rail: rounded, not a full circle, so the label under the icon in a narrow
 * rail cell isn't cut by the curve.
 */
private val NavCellShape = RoundedCornerShape(18.dp)

/** One destination in [FloatingNavBar]; [tint] colours its capsule while it's the tab showing (none, plain glass, on Today). */
data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon, val tint: Color? = null)

/**
 * The floating tab bar in black glass (spec 3.2): a pill of tabs, the content scrolling under it (marked with
 * `hazeSource` on [glass]) showing through blurred and darkened. Centred with nothing beside it: the bar only switches
 * tabs. The capsule behind the tab showing slides to it and takes that section's colour. Behind the pill a black fade
 * ([TabBarFade]) rises from the bottom edge, so cards slide under the bar softly instead of being cut by its edge. It
 * draws past the bar's own top without taking room, so the pages' bottom padding stays the bar's height.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState,
    /** The redesign's coral "+" in the middle of the bar; null leaves the plain bar. */
    onPlus: (() -> Unit)? = null,
) {
    if (onPlus != null) return DialsNavBar(items, selected, onSelect, onPlus, modifier, glass)
    Box(
        modifier.fillMaxWidth().drawBehind {
            val height = maxOf(TabBarFade.Height.toPx(), size.height)
            drawRect(TabBarFade.brush(size.height - height, size.height), Offset(0f, size.height - height), Size(size.width, height))
        }.navigationBarsPadding().padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // No shadow: it would show through the glass as a dark smudge.
        Box(Modifier.widthIn(max = 440.dp).fillMaxWidth().glassPill(glass)) {
            // Measured width, so the pill knows how wide a cell is (BoxWithConstraints can't size to its row).
            var barWidth by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                Modifier.padding(6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .onSizeChanged { barWidth = it.width },
            ) {
                // One capsule that slides to the selected tab, rather than each cell lighting up on its own.
                val gap = 2.dp
                val cell = (with(density) { barWidth.toDp() } - gap * (items.size - 1)) / items.size
                val x by androidx.compose.animation.core.animateDpAsState(
                    (cell + gap) * selected, tween(Motion.SWIFT, easing = Motion.easing), label = "nav pill",
                )
                Box(
                    Modifier.offset(x = x).width(cell).fillMaxHeight().clip(NavCellShape)
                        .background(AppColors.ink),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(item, i == selected, modifier = Modifier.clip(NavCellShape).clickable { onSelect(i) }
                            .weight(1f)
                            .padding(vertical = 8.dp), onTint = AppColors.onInk)
                    }
                }
            }
        }
    }
}

/**
 * [FloatingNavBar] standing up along the left edge, for windows wider than tall (a phone on its side, an
 * unfolded foldable turned sideways): the same glass pill and sliding capsule, centred, giving the height back to
 * the pages.
 */
@Composable
fun FloatingNavRail(
    items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, glass: HazeState,
) {
    Box(
        modifier.fillMaxHeight().systemBarsPadding().displayCutoutPadding().padding(horizontal = 10.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(NavRailWidth - 20.dp).heightIn(max = 380.dp).fillMaxHeight().glassPill(glass)) {
            var barHeight by remember { mutableIntStateOf(0) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(Modifier.padding(6.dp).onSizeChanged { barHeight = it.height }) {
                val gap = 2.dp
                val cell = (with(density) { barHeight.toDp() } - gap * (items.size - 1)) / items.size
                val y by androidx.compose.animation.core.animateDpAsState(
                    (cell + gap) * selected, tween(Motion.SWIFT, easing = Motion.easing), label = "nav pill",
                )
                Box(
                    Modifier.offset(y = y).height(cell).fillMaxWidth().clip(NavCellShape)
                        .background(AppColors.ink),
                )
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEachIndexed { i, item ->
                        NavCell(
                            item, i == selected,
                            modifier = Modifier.clip(NavCellShape).clickable { onSelect(i) }.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.Center, onTint = AppColors.onInk,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar's black glass pill: what scrolls under it blurred, the glass edge round it. It takes every touch inside it,
 * so a tap between two tabs never lands on the card under the bar.
 */
private fun Modifier.glassPill(glass: HazeState): Modifier =
    // The rings design: a solid pill in the card colour, no blur and no edge. [glass] stays for the callers.
    clip(CircleShape).background(AppColors.glass).pointerInput(Unit) {}

/** The capsule under the tab showing: its section colour, eased as it slides; plain glass white on Today. */
@Composable
private fun capsuleColor(tint: Color?): Color {
    val target = if (tint == null) GlassChip else tint.copy(alpha = 0.22f)
    val eased by androidx.compose.animation.animateColorAsState(target, tween(Motion.SWIFT), label = "nav capsule")
    return eased
}

/**
 * The black fade behind the tab bar (spec 3.2): [Height] tall from the window's bottom edge (or the bar's height, if
 * that is more), clear at its top and solid black for its lower [SOLID] part.
 */
object TabBarFade {
    val Height = 120.dp
    const val SOLID = 0.45f

    /** The fade's colour stops, top to bottom: clear, then black from [SOLID] up from the bottom. */
    val stops: Array<Pair<Float, Color>> get() = AppColors.fade.let { arrayOf(0f to Color.Transparent, (1f - SOLID) to it, 1f to it) }

    fun brush(top: Float, bottom: Float): Brush = Brush.verticalGradient(*stops, startY = top, endY = bottom)
}

/**
 * The black fade behind a page's pinned buttons (Profile's Save, Ready's Start, Done, Choose the sport), in the style
 * of the tab bar's ([TabBarFade]): solid black from the bottom of the screen up to the buttons' top, so nothing shows
 * between them and the tab bar, then clearing over [Ramp] above them, the same ramp as the tab bar's fade. A page
 * leaves [Ramp] clear above its pinned buttons at the end of its list, so its last content scrolls fully clear of
 * both ([listClearance]).
 */
object PinnedFade {
    /** How far the fade reaches above the buttons: the tab bar fade's clear-to-black part, 66 dp. */
    val Ramp: Dp = TabBarFade.Height * (1f - TabBarFade.SOLID)

    /** The fade's colour stops between [top] (clear) and [bottom], solid black from [solidFrom] down. */
    fun stops(top: Float, solidFrom: Float, bottom: Float): Array<Pair<Float, Color>> {
        val solid = if (bottom > top) ((solidFrom - top) / (bottom - top)).coerceIn(0f, 1f) else 0f
        val fade = if (AppColors.light) AppColors.page else Color.Black
        return arrayOf(0f to Color.Transparent, solid to fade, 1f to fade)
    }

    /**
     * The bottom padding a page's list needs so its last content ends [Ramp] above pinned buttons whose top is
     * [buttonsTop] over the bottom edge, with [listEnd] the space the list adds on its own (ScreenList's 24 dp).
     */
    fun listClearance(buttonsTop: Dp, listEnd: Dp = 0.dp): Dp = buttonsTop + Ramp - listEnd
}

/**
 * Draws [PinnedFade] behind a page's pinned buttons. Put it on the box that holds them, reaching from the bottom of
 * the screen to the buttons' top: that box is solid black and the fade rises [PinnedFade.Ramp] above it.
 */
fun Modifier.pinnedFade(): Modifier = drawBehind {
    val ramp = PinnedFade.Ramp.toPx()
    drawRect(
        Brush.verticalGradient(*PinnedFade.stops(-ramp, 0f, size.height), startY = -ramp, endY = size.height),
        Offset(0f, -ramp), Size(size.width, size.height + ramp),
    )
}

/** How much of the window's width [FloatingNavRail] takes, its margins included. */
val NavRailWidth = 92.dp

/** One destination's icon and label, tinted for whether it's [on]. */
/**
 * The rings design's bar: a solid pill in the card colour, the tab showing as a pill filled with ink (its icon and
 * name), the others as icons only, and Energy's "+" in the middle. Flat: no glass edge, no glow.
 */
@Composable
private fun DialsNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, onPlus: () -> Unit, modifier: Modifier, @Suppress("UNUSED_PARAMETER") glass: HazeState) {
    Box(
        modifier.fillMaxWidth().drawBehind {
            val height = maxOf(TabBarFade.Height.toPx(), size.height)
            drawRect(TabBarFade.brush(size.height - height, size.height), Offset(0f, size.height - height), Size(size.width, height))
        }.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Where each tab's cell sits in the bar (window pixels), so the chosen pill can glide from one to the next.
        val quickTitle = androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.dials_quick_title)
        val cells = remember { androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.ui.geometry.Rect>() }
        var origin by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        val pill = rememberGlidingPill(cells[selected]?.translate(-origin))
        Row(
            // Takes every touch inside it, so a tap between two tabs never lands on the card under the bar.
            Modifier.widthIn(max = 480.dp).fillMaxWidth().height(68.dp).clip(CircleShape).background(AppColors.glass)
                .pointerInput(Unit) {}.padding(horizontal = 6.dp)
                .onGloballyPositioned { origin = it.positionInWindow() }
                // The chosen tab's ink pill, drawn under the cells: it slides and stretches to the tab with a spring.
                .drawBehind {
                    val w = pill.width.value
                    if (w > 0f) {
                        val h = 46.dp.toPx()
                        drawRoundRect(
                            AppColors.ink, Offset(pill.left.value, (size.height - h) / 2f), Size(w, h),
                            androidx.compose.ui.geometry.CornerRadius(h / 2f),
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            val half = (items.size + 1) / 2
            items.forEachIndexed { i, item ->
                if (i == half) Box(
                    Modifier.size(50.dp).clip(CircleShape).background(PlusHue)
                        .semantics { contentDescription = quickTitle }
                        .clickable(onClickLabel = quickTitle, onClick = onPlus),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                        val w = 2.6.dp.toPx()
                        drawLine(PlusInk, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(PlusInk, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                RingNavCell(
                    item, i == selected,
                    Modifier.onGloballyPositioned { c ->
                        val p = c.positionInWindow()
                        cells[i] = androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                    },
                ) { onSelect(i) }
            }
        }
    }
}

/** Energy's hue for the bar's "+", with the dark ink on it. */
private val PlusHue = Color(0xFFFF5C39)
private val PlusInk = Color(0xFF0A0A0C)

/** The chosen tab's pill: its start and width in the bar, each on a spring, jumping into place the first time. */
private class GlidingPill(
    val left: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val width: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
)

@Composable
private fun rememberGlidingPill(target: androidx.compose.ui.geometry.Rect?): GlidingPill {
    val pill = remember { GlidingPill(androidx.compose.animation.core.Animatable(0f), androidx.compose.animation.core.Animatable(0f)) }
    androidx.compose.runtime.LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        if (pill.width.value == 0f) {
            pill.left.snapTo(t.left)
            pill.width.snapTo(t.width)
            return@LaunchedEffect
        }
        val glide = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
        kotlinx.coroutines.coroutineScope {
            launch { pill.left.animateTo(t.left, glide) }
            launch { pill.width.animateTo(t.width, glide) }
        }
    }
    return pill
}

/**
 * A tab in the rings bar: the one showing carries its icon and name over the gliding ink pill the bar draws; the rest
 * an icon in a 46 dp cell. The icon pops a little when its tab is chosen, and the cell presses in under the finger.
 */
@Composable
private fun RingNavCell(item: NavItem, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint by androidx.compose.animation.animateColorAsState(
        if (on) AppColors.onInk else AppColors.ink.copy(alpha = 0.6f), tween(Motion.QUICK), label = "nav tint",
    )
    val pop by animateFloatAsState(
        if (on) 1f else 0.92f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f), label = "nav pop",
    )
    Row(
        modifier.height(46.dp).widthIn(min = 46.dp).bounceClick(onClick = onClick)
            .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f))
            .padding(horizontal = if (on) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (on) item.selectedIcon else item.icon, item.label, tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop },
        )
        if (on) {
            Spacer(Modifier.width(8.dp))
            Text(
                item.label, color = tint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = GlowType.caption.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun NavCell(item: NavItem, on: Boolean, modifier: Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, onTint: Color = AppColors.ink) {
    Column(modifier, verticalArrangement = verticalArrangement, horizontalAlignment = Alignment.CenterHorizontally) {
        val tint by androidx.compose.animation.animateColorAsState(
            if (on) onTint else AppColors.ink.copy(alpha = 0.55f),
            tween(Motion.QUICK), label = "nav tint",
        )
        val iconScale by animateFloatAsState(if (on) 1.08f else 1f, tween(Motion.SWIFT, easing = Motion.easing), label = "nav icon")
        Icon(
            if (on) item.selectedIcon else item.icon, item.label, tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Text(item.label, style = GlowType.caption, color = tint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/*
 * The bar's glass, in the theme's nav colour: what passes under is blurred and tinted until only its colour shows; a
 * light catching the top edge, shade along the bottom. One per theme, built once.
 */
private val GlassBlur: HazeBlurStyle get() = glassBlurs.getValue(AppColors.theme)

private val glassBlurs = org.openstrap.app.data.settings.AppTheme.entries.associateWith { theme ->
    val t = AppColors.of(theme)
    HazeBlurStyle {
        blurRadius(24.dp)
        backgroundColor(t.page)
        colorEffects(listOf(HazeColorEffect.tint(t.nav.copy(alpha = t.nav.alpha * 0.82f))))
        // Without blur (before Android 12), a near-opaque tint instead.
        fallbackColorEffect(HazeColorEffect.tint(t.nav.copy(alpha = 0.94f)))
    }
}
private val GlassChip: Color get() = AppColors.ink.copy(alpha = if (AppColors.light) 0.07f else 0.1f)
private val GlassChipEdge: BorderStroke get() = BorderStroke(
    1.dp, Brush.verticalGradient(0f to AppColors.ink.copy(alpha = 0.16f), 1f to Color.Transparent),
)

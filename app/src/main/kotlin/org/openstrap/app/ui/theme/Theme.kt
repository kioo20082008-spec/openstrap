package org.openstrap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.openstrap.app.R
import androidx.compose.ui.unit.sp

/**
 * Each metric's one ink (spec 2.2): its values and status when off your normal, its row icon, its chart marks and its
 * edge light. A metric never takes a second colour, and a level shows as how far the light reaches, never as a hue.
 * Tuned to read well on near-black.
 */
object MetricColors {
    val heart = Color(0xFFFF5A5F)
    val steps = Color(0xFFC3F53C)
    val sleepDeep = Color(0xFF4C6FFF)
    val sleepLight = Color(0xFF8FA8FF)
    val sleepRem = Color(0xFFB37CFF)
    val sleepAwake = Color(0xFFFFB547)
    val spo2 = Color(0xFF38BDF8)
    val stress = Color(0xFFF062C0)
    val hrv = Color(0xFF2DD4BF)
    val temperature = Color(0xFFFF8A3D)
    /** Recovery: one teal on every surface. */
    val readiness = Color(0xFFFF6B5B)
    /** Charge: Ember orange on every surface, whatever the level. */
    val charge = Color(0xFFF66C28)
    /**
     * Calories: Gold (hue 48), between Charge's orange and Steps' lime and lighter than the warning amber, so Calories
     * and Steps read as two metrics side by side on Activity.
     */
    val calories = Color(0xFFFFD23F)
    /** Calories' darker shade, for walking beside workouts in the hourly bars: a part of the same metric, not a second one. */
    val caloriesShade = Color(0xFF8C7314)
    /** Load: violet, apart from Sleep's blues and REM's purple. */
    val load = Color(0xFFA78BFA)
    /** Habit Age: orchid. */
    val habitAge = Color(0xFFD86BEA)
    /** The evening hero's light: a moment, not a metric. Violet, well clear of the night's Sleep blue. */
    val evening = Color(0xFFA274F2)
    /** Green and amber are for the strap and the sync only, never a health value. */
    val good = Color(0xFF3DDC97)
    val warn = Color(0xFFFFB547)

    /** Battery colour: amber up to 40%, green above. */
    fun battery(level: Int?): Color = when {
        level == null -> good
        level <= 40 -> warn
        else -> good
    }
}

/** The OpenStrap palette: navy pages, cream ink, one coral accent. The app draws through [AppColors]; widgets keep these. */
object AppPalette {
    val Navy = Color(0xFF14213D)
    val NavyRaised = Color(0xFF1C2C50)
    val NavyHigh = Color(0xFF243760)
    val Cream = Color(0xFFF4EDE0)
    val Coral = Color(0xFFFF6B5B)
}

/** Material's colours for one theme, for what Material draws itself (menus, pickers, dialogs, text selection). */
private fun schemeOf(t: ThemeColors) = if (t.light) {
    lightColorScheme(
        primary = AppPalette.Coral,
        onPrimary = Color.White,
        primaryContainer = t.control,
        onPrimaryContainer = t.ink,
        secondary = t.ink,
        onSecondary = t.page,
        secondaryContainer = t.control,
        onSecondaryContainer = t.ink,
        tertiary = t.ink,
        onTertiary = t.page,
        tertiaryContainer = t.control,
        onTertiaryContainer = t.ink,
        surfaceTint = Color.Transparent,
        background = t.page,
        onBackground = t.ink,
        surface = t.page,
        onSurface = t.ink,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = t.skyTop,
        surfaceContainer = t.raised,
        surfaceContainerHigh = t.raised,
        surfaceContainerHighest = t.control,
        onSurfaceVariant = t.ink.copy(alpha = 0.65f),
        outline = t.ink.copy(alpha = 0.22f),
        outlineVariant = t.ink.copy(alpha = 0.12f),
        error = Color(0xFFD64545),
    )
} else {
    darkColorScheme(
        // Coral is the one accent: switches, primary buttons and progress. Containers are the raised surfaces.
        primary = AppPalette.Coral,
        onPrimary = t.page,
        primaryContainer = t.raised,
        onPrimaryContainer = t.ink,
        secondary = t.ink,
        onSecondary = t.page,
        secondaryContainer = t.raised,
        onSecondaryContainer = t.ink,
        tertiary = t.ink,
        onTertiary = t.page,
        tertiaryContainer = t.raised,
        onTertiaryContainer = t.ink,
        surfaceTint = Color.Transparent,
        background = t.page,
        onBackground = t.ink,
        surface = t.page,
        onSurface = t.ink,
        surfaceContainerLowest = t.page,
        surfaceContainerLow = lerp(t.page, t.control, 0.3f),
        surfaceContainer = t.control,
        surfaceContainerHigh = lerp(t.control, t.raised, 0.5f),
        surfaceContainerHighest = t.raised,
        onSurfaceVariant = t.ink.copy(alpha = 0.65f),
        outline = t.ink.copy(alpha = 0.2f),
        outlineVariant = t.ink.copy(alpha = 0.14f),
        error = Color(0xFFFF6B6B),
    )
}

private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter for Latin text and numbers. Both Inter and Almarai are SIL OFL 1.1. */
private val InterFamily = FontFamily(inter(200), inter(300), inter(400), inter(500), inter(600), inter(700), inter(800))

/** Almarai when the app runs in Arabic; it carries its own Latin digits so mixed lines stay even. */
private val AlmaraiFamily = FontFamily(
    Font(R.font.almarai_regular, FontWeight.Light),
    Font(R.font.almarai_regular, FontWeight.Normal),
    Font(R.font.almarai_regular, FontWeight.Medium),
    Font(R.font.almarai_bold, FontWeight.SemiBold),
    Font(R.font.almarai_bold, FontWeight.Bold),
    Font(R.font.almarai_extrabold, FontWeight.ExtraBold),
)

private val isArabic: Boolean get() = java.util.Locale.getDefault().language == "ar"

/** Kept under their old names so every caller picks up the new faces. Read on each use, so a language switch takes effect. */
/** Numbers stay in Inter in every language. */
val NumberFamily: FontFamily get() = InterFamily
val FunnelDisplay: FontFamily get() = if (isArabic) AlmaraiFamily else InterFamily
val FunnelSans: FontFamily get() = if (isArabic) AlmaraiFamily else InterFamily

private val Base = Typography()

/** Big numerals use tabular figures so values don't jitter as they update (e.g. live HR). */
private fun display(style: TextStyle, weight: FontWeight, size: Int, tracking: Float) =
    style.copy(fontFamily = FunnelDisplay, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

private fun text(style: TextStyle) = style.copy(fontFamily = FunnelSans)

private val AppTypography get() = Typography(
    displayLarge = display(Base.displayLarge, FontWeight.SemiBold, 64, -1.5f),
    displayMedium = display(Base.displayMedium, FontWeight.SemiBold, 48, -1f),
    displaySmall = display(Base.displaySmall, FontWeight.SemiBold, 34, -0.5f),
    headlineLarge = display(Base.headlineLarge, FontWeight.SemiBold, 30, -0.4f),
    headlineMedium = display(Base.headlineMedium, FontWeight.SemiBold, 26, -0.3f),
    headlineSmall = display(Base.headlineSmall, FontWeight.SemiBold, 22, -0.2f),
    titleLarge = display(Base.titleLarge, FontWeight.SemiBold, 20, 0f),
    titleMedium = text(Base.titleMedium).copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleSmall = text(Base.titleSmall).copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = text(Base.bodyLarge),
    bodyMedium = text(Base.bodyMedium),
    bodySmall = text(Base.bodySmall),
    labelLarge = text(Base.labelLarge).copy(fontWeight = FontWeight.SemiBold),
    // Uppercase tracked labels ("TIME ASLEEP") are styled with labelMedium.
    labelMedium = text(Base.labelMedium).copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, fontSize = 11.sp),
    labelSmall = text(Base.labelSmall),
)

/**
 * The theme picked on the You page ([AppColors.theme]), whatever the system's light mode: Navy, OLED black and Warm
 * dusk are dark, Cream light is light. Read here, so switching composes the app again in the new colours.
 */
@Composable
fun OpenStrapTheme(content: @Composable () -> Unit) {
    val colors = AppColors.current
    val scheme = remember(colors) { schemeOf(colors) }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content,
    )
}

package org.openstrap.app.ui.glow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.theme.AppColors
import org.openstrap.app.ui.theme.AppIcons

/** Opens the Analysis screen of the smart assistant, from the coach card. Provided by the activity. */
val LocalOpenAnalysis = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The coach card's last line: a quiet link to the smart assistant's Analysis screen. It only opens the screen; nothing is
 * sent until the button there is tapped.
 */
@Composable
internal fun CoachAiLink() {
    val open = LocalOpenAnalysis.current
    val hue = AppColors.readable(RingColors.Sleep)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Brain, contentDescription = null, modifier = Modifier.size(20.dp), tint = hue)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.ai_coach_link), Modifier.weight(1f), color = hue,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

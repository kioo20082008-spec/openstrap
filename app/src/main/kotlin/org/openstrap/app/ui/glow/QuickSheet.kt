package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.AppPalette

/** One tile of the quick sheet. */
class QuickAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** The redesign's "+" sheet: the things you reach for most, two to a row. Each tile closes the sheet before acting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(title: String, actions: List<QuickAction>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppPalette.Navy, contentColor = AppPalette.Cream) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, color = AppPalette.Cream, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
            Spacer(Modifier.height(16.dp))
            actions.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { a -> Tile(a, onDismiss, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Tile(a: QuickAction, close: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(Color(0xFF1C2C50)).border(1.dp, Color(0xFF2E4069), shape)
            .clickable { close(); a.onClick() }.padding(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AppPalette.Coral.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(a.icon, null, tint = AppPalette.Coral, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(a.label, color = AppPalette.Cream, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

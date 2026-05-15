package `in`.foodlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Friendly "you've hit the free-tier cap" dialog. v1 just announces that
 *  premium is coming soon — no email capture, no pricing. */
@Composable
fun PaywallDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CiboColors.SurfaceContainer,
        titleContentColor = CiboColors.OnSurface,
        textContentColor = CiboColors.OnSurfaceVariant,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CiboColors.Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = CiboColors.Primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                "Premium is coming soon",
                style = CiboType.DisplaySm,
                color = CiboColors.OnSurface,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                message,
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            CiboPrimaryButton(text = "Got it", onClick = onDismiss)
        },
    )
}

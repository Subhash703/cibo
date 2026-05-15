package `in`.foodlens.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ──────────────── Surfaces

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable () -> Unit,
) {
    val borderColor = if (glow) CiboColors.Primary.copy(alpha = 0.35f)
                      else CiboColors.OnSurface.copy(alpha = 0.04f)
    val bgColor = if (glow) CiboColors.SurfaceContainerLow else CiboColors.SurfaceContainer
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
    ) { content() }
}

// ──────────────── Buttons

@Composable
fun CiboPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CiboColors.Primary,
            contentColor   = CiboColors.OnPrimary,
            disabledContainerColor = CiboColors.Primary.copy(alpha = 0.4f),
            disabledContentColor   = CiboColors.OnPrimary.copy(alpha = 0.7f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = CiboColors.OnPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text, style = CiboType.BodyMd.copy(fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun CiboSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CiboColors.Primary),
        border = androidx.compose.foundation.BorderStroke(1.dp, CiboColors.Primary.copy(alpha = 0.6f)),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = CiboType.BodyMd.copy(fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

// ──────────────── Status pill

enum class StatusTone { Positive, Warning, Danger, Neutral }

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone = StatusTone.Positive,
    pulses: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (textColor, bg, border) = when (tone) {
        StatusTone.Positive -> Triple(CiboColors.Primary,
            CiboColors.Primary.copy(alpha = 0.12f),
            CiboColors.Primary.copy(alpha = 0.30f))
        StatusTone.Warning  -> Triple(CiboColors.Warning,
            CiboColors.Warning.copy(alpha = 0.12f),
            CiboColors.Warning.copy(alpha = 0.30f))
        StatusTone.Danger   -> Triple(CiboColors.Error,
            CiboColors.Error.copy(alpha = 0.16f),
            CiboColors.Error.copy(alpha = 0.30f))
        StatusTone.Neutral  -> Triple(CiboColors.OnSurfaceVariant,
            CiboColors.SurfaceContainerHigh,
            Color.Transparent)
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue  = if (pulses) 1.6f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse-scale",
    )

    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size((8 * if (pulses) scale else 1f).dp)
                .clip(CircleShape)
                .background(textColor),
        )
        Text(
            text.uppercase(),
            color = textColor,
            style = CiboType.LabelSm,
        )
    }
}

// ──────────────── Chips

@Composable
fun CiboChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(CiboColors.Primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = CiboColors.Primary,
            style = CiboType.BodyMd.copy(fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
    }
}

// ──────────────── Brand mark — uses cibo-icon.png from /res/drawable

@Composable
fun CiboMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    painter: Painter,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(CiboColors.SurfaceContainerHigh),
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = "Cibo",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ──────────────── Kcal ring

@Composable
fun KcalRing(
    consumed: Int,
    target: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    stroke: Dp = 14.dp,
) {
    val fraction = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    val animFraction by animateFloatAsState(targetValue = fraction, label = "ring-anim")

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            drawArc(
                color = CiboColors.SurfaceContainerHigh,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = Stroke(width = w),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(CiboColors.Primary, CiboColors.PrimaryFixedDim, CiboColors.Primary)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animFraction,
                useCenter = false,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (target > 0) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = CiboType.DisplayLg.copy(fontSize = 40.sp),
                    color = CiboColors.OnSurface,
                )
                Text("DAILY GOAL",
                    style = CiboType.LabelSm,
                    color = CiboColors.OnSurfaceVariant)
            } else {
                Text("0", style = CiboType.DisplayLg.copy(fontSize = 40.sp), color = CiboColors.OnSurface)
                Text("/ — KCAL", style = CiboType.LabelSm, color = CiboColors.OnSurfaceVariant)
            }
        }
    }
}

@Composable
fun MacroBar(
    label: String,
    grams: Int,
    target: Int = 120,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label,
                style = CiboType.BodyMd.copy(fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = CiboColors.OnSurfaceVariant)
            Text("${grams}g",
                style = CiboType.BodyMd.copy(fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = CiboColors.OnSurface)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(CiboColors.SurfaceContainerHigh),
        ) {
            val frac = (grams.toFloat() / target).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(CiboColors.Primary),
            )
        }
    }
}

// ──────────────── Verdict banner

enum class VerdictSignal { Green, Yellow, Red }

@Composable
fun VerdictBanner(
    signal: VerdictSignal,
    headline: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val accent = when (signal) {
        VerdictSignal.Green  -> CiboColors.Primary
        VerdictSignal.Yellow -> CiboColors.Warning
        VerdictSignal.Red    -> CiboColors.Error
    }
    val icon = when (signal) {
        VerdictSignal.Green  -> Icons.Filled.CheckCircle
        VerdictSignal.Yellow -> Icons.Filled.Warning
        VerdictSignal.Red    -> Icons.Filled.Error
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(headline, color = accent,
                style = CiboType.BodyMd.copy(fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            Text(detail, color = CiboColors.OnSurface, style = CiboType.BodyMd)
        }
    }
}

// ──────────────── AI Insight card

@Composable
fun InsightCard(
    text: String,
    label: String = "CIBO INSIGHT",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CiboColors.SurfaceContainerLow)
            .border(1.dp, CiboColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = CiboColors.Primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(label, style = CiboType.LabelSm, color = CiboColors.Primary)
            }
            Text(
                "“$text”",
                style = CiboType.Italic,
                color = CiboColors.OnSurface,
            )
        }
    }
}

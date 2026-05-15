package `in`.foodlens.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.foodlens.app.R

// ──────────────── Colors (warm humanist editorial)

object CiboColors {
    val Background          = Color(0xFF08132A)
    val SurfaceContainerLow = Color(0xFF101B33)
    val SurfaceContainer    = Color(0xFF151F37)
    val SurfaceContainerHigh    = Color(0xFF1F2942)
    val SurfaceContainerHighest = Color(0xFF2A344D)

    val OnSurface           = Color(0xFFD9E2FF)
    val OnSurfaceVariant    = Color(0xFFBACAC4)
    val Outline             = Color(0xFF85948F)
    val OutlineVariant      = Color(0xFF3B4A45)

    val Primary             = Color(0xFF4FF1D1)
    val PrimaryContainer    = Color(0xFF1ED4B6)
    val OnPrimary           = Color(0xFF00382E)
    val PrimaryFixedDim     = Color(0xFF33DEBF)

    val Secondary           = Color(0xFFB9C7E4)
    val SecondaryContainer  = Color(0xFF3C4962)

    val Warning             = Color(0xFFFFC773)
    val Error               = Color(0xFFFFB4AB)
    val Success             = Primary
}

// ──────────────── Spacing & shape tokens

@Immutable
data class CiboSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val containerMargin: Dp = 20.dp,
    val gutter: Dp = 12.dp,
)

@Immutable
data class CiboRadius(
    val sm: Dp = 4.dp,
    val base: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val pill: Dp = 999.dp,
)

val LocalCiboSpacing = staticCompositionLocalOf { CiboSpacing() }
val LocalCiboRadius  = staticCompositionLocalOf { CiboRadius() }

// ──────────────── Typography (Fraunces display + Plus Jakarta body)

private val Fraunces = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold),
    Font(R.font.fraunces, FontWeight.SemiBold, FontStyle.Italic),
)

private val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

object CiboType {
    val DisplayLg = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
                              fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp)
    val DisplayMd = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                              fontSize = 32.sp, lineHeight = 40.sp)
    val DisplaySm = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold,
                              fontSize = 26.sp, lineHeight = 34.sp)
    val H1        = TextStyle(fontFamily = Jakarta,  fontWeight = FontWeight.Bold,
                              fontSize = 24.sp, lineHeight = 32.sp)
    val H2        = TextStyle(fontFamily = Jakarta,  fontWeight = FontWeight.SemiBold,
                              fontSize = 20.sp, lineHeight = 28.sp)
    val BodyLg    = TextStyle(fontFamily = Jakarta,  fontWeight = FontWeight.Normal,
                              fontSize = 18.sp, lineHeight = 28.sp)
    val BodyMd    = TextStyle(fontFamily = Jakarta,  fontWeight = FontWeight.Normal,
                              fontSize = 16.sp, lineHeight = 24.sp)
    val LabelSm   = TextStyle(fontFamily = Jakarta,  fontWeight = FontWeight.SemiBold,
                              fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp)
    val Italic    = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                              fontStyle = FontStyle.Italic,
                              fontSize = 17.sp, lineHeight = 24.sp)
}

private val CiboMaterialTypography = Typography(
    displayLarge  = CiboType.DisplayLg,
    displayMedium = CiboType.DisplayMd,
    displaySmall  = CiboType.DisplaySm,
    headlineLarge = CiboType.H1,
    headlineMedium = CiboType.H2,
    titleLarge    = CiboType.H1,
    titleMedium   = CiboType.H2,
    titleSmall    = CiboType.H2.copy(fontSize = 16.sp),
    bodyLarge     = CiboType.BodyLg,
    bodyMedium    = CiboType.BodyMd,
    bodySmall     = CiboType.BodyMd.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge    = CiboType.LabelSm.copy(fontSize = 14.sp, letterSpacing = 0.8.sp),
    labelMedium   = CiboType.LabelSm.copy(fontSize = 12.sp, letterSpacing = 1.0.sp),
    labelSmall    = CiboType.LabelSm,
)

private val CiboShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ──────────────── Theme entry point

@Composable
fun CiboTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        background          = CiboColors.Background,
        surface             = CiboColors.SurfaceContainer,
        surfaceVariant      = CiboColors.SurfaceContainerHigh,
        surfaceContainerLow = CiboColors.SurfaceContainerLow,
        surfaceContainer    = CiboColors.SurfaceContainer,
        surfaceContainerHigh    = CiboColors.SurfaceContainerHigh,
        surfaceContainerHighest = CiboColors.SurfaceContainerHighest,
        onBackground        = CiboColors.OnSurface,
        onSurface           = CiboColors.OnSurface,
        onSurfaceVariant    = CiboColors.OnSurfaceVariant,
        outline             = CiboColors.Outline,
        outlineVariant      = CiboColors.OutlineVariant,
        primary             = CiboColors.Primary,
        onPrimary           = CiboColors.OnPrimary,
        primaryContainer    = CiboColors.PrimaryContainer,
        onPrimaryContainer  = CiboColors.OnPrimary,
        secondary           = CiboColors.Secondary,
        secondaryContainer  = CiboColors.SecondaryContainer,
        error               = CiboColors.Error,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography  = CiboMaterialTypography,
        shapes      = CiboShapes,
        content     = content,
    )
}

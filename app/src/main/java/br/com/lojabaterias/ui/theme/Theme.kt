package br.com.lojabaterias.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2050D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EAFF),
    onPrimaryContainer = Color(0xFF0D2675),
    secondary = Color(0xFFE08A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF1DC),
    onSecondaryContainer = Color(0xFF4F2E00),
    tertiary = Color(0xFF0F8A5F),
    onTertiary = Color.White,
    background = Color(0xFFF3F4F7),
    onBackground = Color(0xFF14171C),
    surface = Color.White,
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF5F6673),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFEEF0F4),
    surfaceContainerHighest = Color(0xFFE6E9EE),
    outline = Color(0xFFC9CED7),
    outlineVariant = Color(0xFFE5E8ED),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9BEFF),
    onPrimary = Color(0xFF0D2675),
    primaryContainer = Color(0xFF223F9E),
    onPrimaryContainer = Color(0xFFE3EAFF),
    secondary = Color(0xFFFFC266),
    onSecondary = Color(0xFF452700),
    secondaryContainer = Color(0xFF5E3A00),
    onSecondaryContainer = Color(0xFFFFF1DC),
    tertiary = Color(0xFF6EDBAE),
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE4E6EB),
    surface = Color(0xFF181B21),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF242830),
    onSurfaceVariant = Color(0xFFAAB0BC),
    surfaceContainerLowest = Color(0xFF14161B),
    surfaceContainerLow = Color(0xFF181B21),
    surfaceContainer = Color(0xFF1C1F26),
    surfaceContainerHigh = Color(0xFF242830),
    surfaceContainerHighest = Color(0xFF2C313A),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF2C313A),
    error = Color(0xFFFF8A80),
)

/** Cores semânticas para valores financeiros e estoque. */
object StatusColors {
    val profitLight = Color(0xFF13804F)
    val profitDark = Color(0xFF6EDBAE)
    val warningLight = Color(0xFFB45309)
    val warningDark = Color(0xFFFFC266)
    val dangerLight = Color(0xFFC62828)
    val dangerDark = Color(0xFFFF8A80)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        // textAlign centraliza o texto dos botões quando ele quebra em duas linhas
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LojaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

@Composable
fun profitColor(): Color = if (isSystemInDarkTheme()) StatusColors.profitDark else StatusColors.profitLight

@Composable
fun warningColor(): Color = if (isSystemInDarkTheme()) StatusColors.warningDark else StatusColors.warningLight

@Composable
fun dangerColor(): Color = if (isSystemInDarkTheme()) StatusColors.dangerDark else StatusColors.dangerLight

/** Verde para lucro positivo, vermelho para prejuízo. */
@Composable
fun moneyResultColor(value: Long): Color = if (value < 0) dangerColor() else profitColor()

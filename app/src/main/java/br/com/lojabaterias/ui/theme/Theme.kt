package br.com.lojabaterias.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B2472),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD1),
    onSecondaryContainer = Color(0xFF5A3100),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF15181E),
    surface = Color.White,
    onSurface = Color(0xFF15181E),
    surfaceVariant = Color(0xFFEDEFF4),
    onSurfaceVariant = Color(0xFF585E6B),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFEBEEF4),
    outline = Color(0xFFC5CAD4),
    outlineVariant = Color(0xFFE1E4EA),
    error = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB6FF),
    onPrimary = Color(0xFF0B2472),
    primaryContainer = Color(0xFF1E3A99),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6B3F00),
    onSecondaryContainer = Color(0xFFFFEDD1),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E5EC),
    surface = Color(0xFF1A1D23),
    onSurface = Color(0xFFE3E5EC),
    surfaceVariant = Color(0xFF2A2E36),
    onSurfaceVariant = Color(0xFFB9BECA),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerLow = Color(0xFF1A1D23),
    surfaceContainerHigh = Color(0xFF262A31),
    outline = Color(0xFF4A505C),
    outlineVariant = Color(0xFF363B44),
    error = Color(0xFFFF8A80),
)

/** Cores semânticas para valores financeiros e estoque. */
object StatusColors {
    val profitLight = Color(0xFF15803D)
    val profitDark = Color(0xFF6EE7A0)
    val warningLight = Color(0xFFB45309)
    val warningDark = Color(0xFFFFC46B)
    val dangerLight = Color(0xFFB91C1C)
    val dangerDark = Color(0xFFFF8A80)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        // textAlign centraliza o texto dos botões quando ele quebra em duas linhas
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    )
}

@Composable
fun LojaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
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

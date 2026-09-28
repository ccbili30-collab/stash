package io.github.ccbili30.stash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 视觉规范 v2：Material 3 Expressive + Material You。
 * 颜色策略：Android 12+ 且开关打开 → 壁纸动态取色；
 * 否则回落到 Ocean 种子色（#116682）预生成的一对 scheme。
 */
private val OceanLight = lightColorScheme(
    primary = Color(0xFF006686),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF4D626D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E6F3),
    onSecondaryContainer = Color(0xFF091E28),
    tertiary = Color(0xFF5D5C7D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E0FF),
    onTertiaryContainer = Color(0xFF191837),
    background = Color(0xFFF5FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF5FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F8),
    surfaceContainer = Color(0xFFE9EFF4),
    surfaceContainerHigh = Color(0xFFE3E9EE),
    surfaceContainerHighest = Color(0xFFDDE4E9),
    outline = Color(0xFF71787D),
    outlineVariant = Color(0xFFC1C8CD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2B3134),
    inverseOnSurface = Color(0xFFECF1F5),
    inversePrimary = Color(0xFF95CEF4),
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF95CEF4),
    onPrimary = Color(0xFF003449),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFB1C9D7),
    onSecondary = Color(0xFF1C333F),
    secondaryContainer = Color(0xFF334956),
    onSecondaryContainer = Color(0xFFD3E6F3),
    tertiary = Color(0xFFC7C3EA),
    onTertiary = Color(0xFF2F2D4D),
    tertiaryContainer = Color(0xFF464365),
    onTertiaryContainer = Color(0xFFE3E0FF),
    background = Color(0xFF0E1417),
    onBackground = Color(0xFFDDE4E9),
    surface = Color(0xFF0E1417),
    onSurface = Color(0xFFDDE4E9),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C8CD),
    surfaceContainerLowest = Color(0xFF090F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2124),
    surfaceContainerHigh = Color(0xFF252B2F),
    surfaceContainerHighest = Color(0xFF303639),
    outline = Color(0xFF8B9297),
    outlineVariant = Color(0xFF41484D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDDE4E9),
    inverseOnSurface = Color(0xFF2B3134),
    inversePrimary = Color(0xFF006686),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> OceanDark
        else -> OceanLight
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

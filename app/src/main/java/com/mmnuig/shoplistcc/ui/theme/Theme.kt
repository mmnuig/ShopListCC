package com.mmnuig.shoplistcc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = GreenDark,
    onSecondary = Color.White,
    background = Color.White,
    surface = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = GreenContainer,
    secondary = Color(0xFF81C784),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

/** Card colours for the three item states, per theme. */
data class ShopColors(
    val plannedBg: Color,
    val plannedBorder: Color,
    val boughtBg: Color,
    val boughtBorder: Color,
    val unplannedBg: Color,
    val unplannedBorder: Color,
    val flag: Color
)

val LightShopColors = ShopColors(
    plannedBg = PlannedBg,
    plannedBorder = PlannedBorder,
    boughtBg = BoughtBg,
    boughtBorder = BoughtBorder,
    unplannedBg = UnplannedBg,
    unplannedBorder = UnplannedBorder,
    flag = FlagBlue
)

val DarkShopColors = ShopColors(
    plannedBg = PlannedBgDark,
    plannedBorder = PlannedBorderDark,
    boughtBg = BoughtBgDark,
    boughtBorder = BoughtBorderDark,
    unplannedBg = UnplannedBgDark,
    unplannedBorder = UnplannedBorderDark,
    flag = FlagBlueDark
)

val LocalShopColors = staticCompositionLocalOf { LightShopColors }

@Composable
fun ShopListCCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalShopColors provides if (darkTheme) DarkShopColors else LightShopColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = appTypography(largeText),
            content = content
        )
    }
}

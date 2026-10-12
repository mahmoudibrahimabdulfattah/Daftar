package com.mk.daftar.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.style.StyleScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Daftar's fully custom theme. Components read tokens through the companion accessors (or, inside a
 * Style, through the [StyleScope] extensions below). Material 3 receives a matching scheme so any
 * Material component we use stays on the brand palette.
 */
@Immutable
class DaftarTheme(val colors: DaftarColors, val typography: DaftarTypography, val shapes: DaftarShapes) {
    companion object {
        val colors: DaftarColors
            @Composable @ReadOnlyComposable
            get() = LocalDaftarThemeInstance.current.colors

        val typography: DaftarTypography
            @Composable @ReadOnlyComposable
            get() = LocalDaftarThemeInstance.current.typography

        val shapes: DaftarShapes
            @Composable @ReadOnlyComposable
            get() = LocalDaftarThemeInstance.current.shapes

        val styles: DaftarStyles = DaftarStyles

        val LocalDaftarTheme: ProvidableCompositionLocal<DaftarTheme>
            get() = LocalDaftarThemeInstance
    }
}

// Dynamic, not static: style nodes read it outside composition and must see a theme switch.
internal val LocalDaftarThemeInstance = compositionLocalOf<DaftarTheme> {
    error("DaftarTheme is not provided; wrap the UI in DaftarTheme { }")
}

val StyleScope.colors: DaftarColors
    get() = LocalDaftarThemeInstance.currentValue.colors

val StyleScope.typography: DaftarTypography
    get() = LocalDaftarThemeInstance.currentValue.typography

val StyleScope.shapes: DaftarShapes
    get() = LocalDaftarThemeInstance.currentValue.shapes

/**
 * Installs Daftar's tokens. [arabic] picks Alexandria over Rubik for text; numerals stay Rubik either way.
 * The defaults follow the device, but the catalog overrides them to preview both languages.
 */
@Composable
fun DaftarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    arabic: Boolean = isArabicLocale(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val (text, numeric) = daftarFontFamilies(arabic)
    val typography = remember(text, numeric, arabic) { daftarTypography(text, numeric, arabic) }
    val theme = remember(colors, typography) {
        DaftarTheme(colors = colors, typography = typography, shapes = DaftarShapes())
    }
    CompositionLocalProvider(LocalDaftarThemeInstance provides theme) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography.toMaterial(),
            content = content,
        )
    }
}

private fun DaftarColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = soft,
        onPrimaryContainer = ink,
        inversePrimary = primary,
        // Secondary and tertiary roles would otherwise default to Material's purple and pink.
        secondary = muted,
        onSecondary = surface,
        secondaryContainer = soft,
        onSecondaryContainer = ink,
        tertiary = gold,
        onTertiary = surface,
        tertiaryContainer = goldSoft,
        onTertiaryContainer = gold,
        background = paper,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = soft,
        onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = error,
        onError = surface,
        errorContainer = errorSoft,
        onErrorContainer = error,
        outline = line,
        outlineVariant = line,
        scrim = ink,
        surfaceBright = surface,
        surfaceDim = paper,
        surfaceContainerLowest = surface,
        surfaceContainerLow = paper,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = soft,
    )
}

private fun DaftarTypography.toMaterial(): Typography = Typography(
    displaySmall = heroAmount,
    headlineSmall = screenHeading,
    titleLarge = appBarTitle,
    titleMedium = sectionHeading,
    titleSmall = smallHeading,
    bodyLarge = ledgerValue,
    bodyMedium = body,
    bodySmall = supportingBody,
    labelLarge = buttonLabel,
    labelMedium = fieldLabel,
    labelSmall = caption,
)

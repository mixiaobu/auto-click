package org.xiaobu.autoclick.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.xiaobu.autoclick.data.settings.AppSettingsStore
import org.xiaobu.autoclick.data.settings.DEFAULT_THEME_ID

data class AppThemeOption(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme
)

val AppThemeOptions = listOf(
    AppThemeOption(
        id = "theme_red",
        name = "赤",
        description = "",
        primary = Color(0xFFC9352B),
        secondary = Color(0xFF3B6EA8),
        tertiary = Color(0xFFD18400),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFFC9352B),
            primaryContainer = Color(0xFFF8DCD8),
            onPrimaryContainer = Color(0xFF5D0A05),
            secondary = Color(0xFF3B6EA8),
            secondaryContainer = Color(0xFFDDE7FF),
            tertiary = Color(0xFFD18400),
            background = Color(0xFFF0ECEB),
            surfaceVariant = Color(0xFFDED1CE)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFFFFB4AA),
            primaryContainer = Color(0xFF8D201A),
            secondary = Color(0xFFBBD0F2),
            tertiary = Color(0xFFFFCC72)
        )
    ),
    AppThemeOption(
        id = "theme_orange",
        name = "橙",
        description = "",
        primary = Color(0xFFD96F28),
        secondary = Color(0xFF2F6FA3),
        tertiary = Color(0xFF0F8F78),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFFD96F28),
            primaryContainer = Color(0xFFF7DEC8),
            onPrimaryContainer = Color(0xFF5B1604),
            secondary = Color(0xFF2F6FA3),
            secondaryContainer = Color(0xFFDCE6FF),
            tertiary = Color(0xFF0F8F78),
            background = Color(0xFFF1ECE7),
            surfaceVariant = Color(0xFFDECFC3)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFFFFB68C),
            primaryContainer = Color(0xFF8B3F0C),
            secondary = Color(0xFFB6D1F0),
            tertiary = Color(0xFF7AD8C7)
        )
    ),
    AppThemeOption(
        id = "theme_yellow",
        name = "黄",
        description = "",
        primary = Color(0xFFB98500),
        secondary = Color(0xFF376FA9),
        tertiary = Color(0xFF12805C),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFFB98500),
            primaryContainer = Color(0xFFF4E2B8),
            onPrimaryContainer = Color(0xFF3D2600),
            secondary = Color(0xFF376FA9),
            secondaryContainer = Color(0xFFDCE8FF),
            tertiary = Color(0xFF12805C),
            background = Color(0xFFF0ECE3),
            surfaceVariant = Color(0xFFDDD3BD)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFFFFCF75),
            primaryContainer = Color(0xFF765200),
            secondary = Color(0xFFB8D0F0),
            tertiary = Color(0xFF7BD7AD)
        )
    ),
    AppThemeOption(
        id = "theme_green",
        name = "绿",
        description = "",
        primary = Color(0xFF16815A),
        secondary = Color(0xFF356F97),
        tertiary = Color(0xFFC06B18),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFF16815A),
            primaryContainer = Color(0xFFD6EBDD),
            onPrimaryContainer = Color(0xFF003D29),
            secondary = Color(0xFF356F97),
            secondaryContainer = Color(0xFFD7EBF8),
            tertiary = Color(0xFFC06B18),
            background = Color(0xFFE9F0EC),
            surfaceVariant = Color(0xFFCCDCD2)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFF7BD7AD),
            primaryContainer = Color(0xFF00533A),
            secondary = Color(0xFFB0D2EA),
            tertiary = Color(0xFFFFC078)
        )
    ),
    AppThemeOption(
        id = "theme_cyan",
        name = "青",
        description = "",
        primary = Color(0xFF00899B),
        secondary = Color(0xFF4277A8),
        tertiary = Color(0xFFC84E75),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFF00899B),
            primaryContainer = Color(0xFFD2EBEF),
            onPrimaryContainer = Color(0xFF003840),
            secondary = Color(0xFF4277A8),
            secondaryContainer = Color(0xFFD7EBF8),
            tertiary = Color(0xFFC84E75),
            background = Color(0xFFE8F0F2),
            surfaceVariant = Color(0xFFCBDDE1)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFF80D8E5),
            primaryContainer = Color(0xFF005765),
            secondary = Color(0xFFB4D1EF),
            tertiary = Color(0xFFFFB1C8)
        )
    ),
    AppThemeOption(
        id = "theme_blue",
        name = "蓝",
        description = "",
        primary = Color(0xFF2468D8),
        secondary = Color(0xFF008CA8),
        tertiary = Color(0xFFD23F31),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFF2468D8),
            primaryContainer = Color(0xFFDCE7FB),
            onPrimaryContainer = Color(0xFF082B66),
            secondary = Color(0xFF008CA8),
            secondaryContainer = Color(0xFFD6F3FA),
            tertiary = Color(0xFFD23F31),
            background = Color(0xFFE8EEF6),
            surfaceVariant = Color(0xFFCCD8EA)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFF9BBEFF),
            primaryContainer = Color(0xFF12438C),
            secondary = Color(0xFF78D6E8),
            tertiary = Color(0xFFFFB3AA)
        )
    ),
    AppThemeOption(
        id = DEFAULT_THEME_ID,
        name = "紫",
        description = "",
        primary = Color(0xFF6750D8),
        secondary = Color(0xFF008F8C),
        tertiary = Color(0xFFD94D74),
        lightColorScheme = lightAppColorScheme(
            primary = Color(0xFF6750D8),
            primaryContainer = Color(0xFFE3DFF5),
            onPrimaryContainer = Color(0xFF22105F),
            secondary = Color(0xFF008F8C),
            secondaryContainer = Color(0xFFD2F4F1),
            tertiary = Color(0xFFD94D74),
            background = Color(0xFFEBE9F3),
            surfaceVariant = Color(0xFFD4CEE8)
        ),
        darkColorScheme = darkAppColorScheme(
            primary = Color(0xFFC8BEFF),
            primaryContainer = Color(0xFF4634A8),
            secondary = Color(0xFF7ED9D6),
            tertiary = Color(0xFFFFB1C6)
        )
    )
)

fun findAppThemeOption(themeId: String): AppThemeOption {
    return AppThemeOptions.firstOrNull { it.id == themeId }
        ?: AppThemeOptions.first { it.id == DEFAULT_THEME_ID }
}

@Composable
fun AutoclickTheme(
    selectedThemeId: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val storedThemeId = selectedThemeId ?: remember(context) {
        AppSettingsStore(context.applicationContext).getThemeId()
    }
    val themeOption = findAppThemeOption(storedThemeId)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> themeOption.darkColorScheme
        else -> themeOption.lightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun lightAppColorScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    tertiary: Color,
    background: Color,
    surfaceVariant: Color
): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondaryContainer,
        tertiary = tertiary,
        onTertiary = Color.White,
        background = background,
        onBackground = Color(0xFF171C22),
        surface = Color.White,
        onSurface = Color(0xFF171C22),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF3F4B58),
        outline = Color(0xFF738091),
        outlineVariant = Color(0xFFC5CED9)
    )
}

private fun darkAppColorScheme(
    primary: Color,
    primaryContainer: Color,
    secondary: Color,
    tertiary: Color
): ColorScheme {
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF102030),
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color(0xFFE8F1FF),
        secondary = secondary,
        onSecondary = Color(0xFF102030),
        secondaryContainer = Color(0xFF243342),
        tertiary = tertiary,
        onTertiary = Color(0xFF3A1A18),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE6EAF0),
        surface = Color(0xFF171C22),
        onSurface = Color(0xFFE6EAF0),
        surfaceVariant = Color(0xFF25303A),
        onSurfaceVariant = Color(0xFFC0CAD5),
        outline = Color(0xFF7C8794)
    )
}

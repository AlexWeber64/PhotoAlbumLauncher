package com.alweb.photoalbumlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark: photos read best on black, and it avoids a theme flip between screens.
private val AlbumColorScheme = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    onPrimary = Color(0xFF00315C),
    surface = Color(0xFF101014),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFFC6C5CA),
    background = Color.Black,
    onBackground = Color(0xFFE4E2E6),
)

@Composable
fun PhotoAlbumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlbumColorScheme,
        typography = Typography(),
    ) {
        // Surface is what supplies LocalContentColor. Without one, Material3 falls
        // back to black text, which is invisible on this background.
        Surface(
            color = AlbumColorScheme.background,
            contentColor = AlbumColorScheme.onBackground,
            content = content,
        )
    }
}

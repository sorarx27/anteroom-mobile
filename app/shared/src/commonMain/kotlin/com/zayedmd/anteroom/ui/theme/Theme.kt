package com.zayedmd.anteroom.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AnteroomColors {
    val BrandPrimary = Color(0xFF365F50)
    val Brand = Color(0xFF4B7A68)
    val BrandSecondary = Color(0xFFDCE6E1)
    val BrandTertiary = Color(0xFFEAF0ED)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSecondary = Color(0xFFF4F7F6)
    val SurfaceTertiary = Color(0xFFE6EDE9)
    val SurfaceInverse = Color(0xFF111815)
    val OnSurface = Color(0xFF111815)
    val OnSurfaceSecondary = Color(0xFF2C3E36)
    val OnSurfaceTertiary = Color(0xFF455E53)
    val OnSurfaceInverse = Color(0xFFFFFFFF)
    val Muted = Color(0xFF698075)
    val Border = Color(0xFFE6EDE9)
    val BorderStrong = Color(0xFFC2D1CB)
    val Error = Color(0xFF9E3838)
    val Success = Color(0xFF2D6B4E)
}

val AnteroomShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    // 28.dp, the Material 3 default, not the 999.dp pill this used to be.
    // Nothing in the app reads `extraLarge` directly -- the pill-shaped
    // buttons all pass RoundedCornerShape(999.dp) themselves -- but Material's
    // AlertDialog and ModalBottomSheet both default to this token, and a
    // 999.dp radius on a dialog-sized surface clamps to half the short edge
    // and draws an ellipse. Every confirmation dialog in the app was rendering
    // as an oval with its title and buttons clipped off at the sides.
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColorScheme = lightColorScheme(
    primary = AnteroomColors.BrandPrimary,
    onPrimary = AnteroomColors.OnSurfaceInverse,
    primaryContainer = AnteroomColors.BrandSecondary,
    onPrimaryContainer = AnteroomColors.OnSurfaceSecondary,
    secondary = AnteroomColors.Brand,
    onSecondary = AnteroomColors.OnSurfaceInverse,
    surface = AnteroomColors.Surface,
    onSurface = AnteroomColors.OnSurface,
    background = AnteroomColors.Surface,
    onBackground = AnteroomColors.OnSurface,
    error = AnteroomColors.Error,
    onError = Color.White
)

@Composable
fun AnteroomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        shapes = AnteroomShapes,
        content = content
    )
}

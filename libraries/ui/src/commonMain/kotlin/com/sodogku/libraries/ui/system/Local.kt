package com.sodogku.libraries.ui.system

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.libraries.core.AppState
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.system.color.Colors
import com.sodogku.system.typography.Typography
import kotlin.time.Clock

val LocalColors = compositionLocalOf<Colors> {
    error("Theme wasn't applied")
}

val LocalContentColor = compositionLocalOf<ColorResource> {
    error("Theme wasn't applied")
}

val LocalTypography = compositionLocalOf<Typography> {
    error("Theme wasn't applied")
}

val LocalBuildInfo = staticCompositionLocalOf<BuildInfo> {
    error("No LocalBuildInfo provided")
}

val LocalAppState = staticCompositionLocalOf<AppState> {
    error("No LocalAppState provided")
}

val LocalClock = staticCompositionLocalOf<Clock> {
    error("No LocalClock provided")
}

/**
 * The player's "reduce animations" setting, ambient rather than threaded through
 * every call site.
 *
 * Design-system components that move on their own — dialogs springing in, a
 * board wave — read this so a new screen honours the setting without its author
 * having to know the setting exists. Defaults to `false`, never `error(...)`,
 * so previews and tests get sane motion for free.
 */
val LocalReduceAnimations = staticCompositionLocalOf { false }
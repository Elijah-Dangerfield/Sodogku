package com.sodogku.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The things the game can ask the phone to feel like. */
enum class Feel {
    /** A cross going down. The lightest thing available. */
    Mark,

    /** A dog landing. */
    Place,

    /** A wrong guess. */
    Strike,

    /** A level finished. */
    Win,
}

/**
 * Haptics, behind an on/off the player controls.
 *
 * Routed through one object rather than call sites reaching for
 * [LocalHapticFeedback] directly, for two reasons: the setting has to be
 * respected everywhere without every feature remembering to check it, and
 * Compose's haptic vocabulary is small enough that the mapping from *game
 * event* to *available effect* is a decision worth making once.
 */
@Immutable
class Haptics(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun play(feel: Feel) {
        if (!enabled) return
        feedback.performHapticFeedback(
            when (feel) {
                // Compose Multiplatform exposes only these two types today.
                // TextHandleMove is the lighter of the pair, so it carries the
                // frequent, low-stakes events; LongPress carries the ones that
                // should register as a thump.
                Feel.Mark -> HapticFeedbackType.TextHandleMove
                Feel.Place -> HapticFeedbackType.TextHandleMove
                Feel.Strike -> HapticFeedbackType.LongPress
                Feel.Win -> HapticFeedbackType.LongPress
            },
        )
    }

    companion object {
        val Silent: Haptics = Haptics(
            feedback = object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
            },
            enabled = false,
        )
    }
}

/** Defaults to silent so previews and tests need provide nothing. */
val LocalHaptics = staticCompositionLocalOf { Haptics.Silent }

/**
 * Builds a [Haptics] bound to the current platform feedback and [enabled].
 *
 * Remembered, and the `remember` is the point. [Haptics] has identity equality,
 * so a fresh instance per composition is a *changed* value everywhere one is
 * passed on. [LocalHaptics] is static, and a static local whose value changes
 * does not track its readers — it recomposes the whole subtree under the
 * provider and disables skipping while it does, which is a screen's worth of
 * work bought by a one-line allocation.
 */
@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

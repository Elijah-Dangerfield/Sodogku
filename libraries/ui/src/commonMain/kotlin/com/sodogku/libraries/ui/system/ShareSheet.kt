package com.sodogku.libraries.ui.system

import androidx.compose.runtime.staticCompositionLocalOf
import com.sodogku.libraries.sharing.ShareLauncher

/**
 * The platform share sheet, reachable from any composable.
 *
 * A composition local rather than a ViewModel dependency because of where the
 * *words* live. `ShareText.format` is handed a title, a streak line and a
 * footer that are already localised, which means the only place able to build a
 * share is a composable that can call `stringResource`. Threading a launcher
 * down to every one of them — the win sheet, the daily card, a level long-press
 * — is parameter plumbing for something none of the screens in between care
 * about.
 *
 * Defaults to a no-op, so previews and unit tests get something harmless
 * without providing anything.
 */
val LocalShareSheet = staticCompositionLocalOf<ShareLauncher> { ShareLauncher { } }

/**
 * Whether sharing is switched on at all — `features.sharing`, reachable from any
 * composable that can offer a share.
 *
 * A function rather than a `Boolean` on purpose. The value it wraps resolves
 * from the config map on every call, and a plain boolean provided at the root of
 * the tree would be answered once at app start: the switch would not take effect
 * until the process restarted, which is exactly what a dark launch cannot
 * tolerate. Reading it where the button is drawn keeps the answer current.
 *
 * Defaults to on. An app that never reaches the server, a preview and a unit
 * test all get the share button, which is the fail-open direction SPEC 4.2 asks
 * for — a feature is hidden because someone decided to hide it, never because a
 * fetch failed.
 */
val LocalSharingEnabled = staticCompositionLocalOf<() -> Boolean> { { true } }

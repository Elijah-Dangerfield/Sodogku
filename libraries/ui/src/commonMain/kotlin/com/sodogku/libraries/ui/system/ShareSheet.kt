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

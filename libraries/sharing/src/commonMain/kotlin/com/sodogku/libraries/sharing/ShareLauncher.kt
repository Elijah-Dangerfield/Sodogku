package com.sodogku.libraries.sharing

/**
 * Hands a finished share string to the platform's own share UI — the Android
 * chooser, the iOS activity sheet.
 *
 * Returns nothing, unlike `WebLinkLauncher`, and that is the whole difference
 * between the two. A web link that fails to open leaves the player staring at a
 * screen that did not change and needs an answer; a share sheet that fails to
 * present has no recovery a caller could offer and no second thing to try. The
 * implementations log and move on, which keeps this interface free of
 * `:libraries:core` and keeps this module's dependency list empty.
 */
fun interface ShareLauncher {
    fun share(text: String)
}

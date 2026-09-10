package com.sodogku

import com.sodogku.libraries.navigation.AppShortcuts

/**
 * The deep link behind a tapped home-screen entry, or null for a type nothing
 * claims.
 *
 * A one-line hand-off to [AppShortcuts], and it exists because of how the
 * framework is built rather than because it does anything. Kotlin/Native only
 * exports a non-`api` dependency's declarations when the framework's own API
 * names them, and `AppShortcuts` is named by no Kotlin on this side — the plist
 * carries the item types and Swift asks for the URL. Without something here it
 * never reaches `ComposeApp.h` and `iOSApp.swift` cannot see it.
 *
 * The alternative was exporting the whole of `:libraries:navigation` to Swift
 * for one lookup, which puts every route and the navigation library behind it
 * into the generated header.
 */
fun deepLinkForShortcut(type: String): String? = AppShortcuts.urlFor(type)

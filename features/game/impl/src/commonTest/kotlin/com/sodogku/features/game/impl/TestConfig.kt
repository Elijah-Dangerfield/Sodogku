package com.sodogku.features.game.impl

import com.sodogku.libraries.config.AppConfigMap

/**
 * A config map addressed the way the admin console addresses it — dotted paths
 * — so a test names the key an operator would actually type into the console.
 *
 * `configOf()` with nothing in it is the outage case: every value resolves to
 * the default it declares, which is what a device sees when the server is
 * unreachable.
 */
internal fun configOf(vararg values: Pair<String, Any>): AppConfigMap {
    val tree = mutableMapOf<String, MutableMap<String, Any>>()
    values.forEach { (path, value) ->
        tree.getOrPut(path.substringBefore('.')) { mutableMapOf() }[path.substringAfter('.')] = value
    }
    return object : AppConfigMap() {
        override val map: Map<String, *> = tree
    }
}

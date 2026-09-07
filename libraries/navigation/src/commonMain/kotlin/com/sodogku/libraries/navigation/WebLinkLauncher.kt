package com.sodogku.libraries.navigation

import com.sodogku.libraries.core.Catching

fun interface WebLinkLauncher {
    fun open(url: String): Catching<Unit>
}

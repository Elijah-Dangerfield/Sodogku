package com.sodogku.libraries.sharing.impl

import android.content.Context
import android.content.Intent
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.sharing.ShareLauncher
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Always through a chooser, never a resolved default.
 *
 * `startActivity(sendIntent)` on its own hands the share to whatever the user
 * once set as their default for `text/plain`, which for many people is a
 * clipboard or notes app they picked years ago for something unrelated. The
 * whole value of a Wordle-style share is that it lands in a group chat, so the
 * picker is the feature.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidShareLauncher @Inject constructor(
    private val context: Context,
) : ShareLauncher {

    override fun share(text: String) {
        Catching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MimeType
                putExtra(Intent.EXTRA_TEXT, text)
            }
            // The application context has no task of its own, so the chooser
            // needs one. Without the flag this throws rather than opening.
            val chooser = Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.logOnFailure { "Failed to open the share chooser" }
    }

    private companion object {
        const val MimeType = "text/plain"
    }
}

package com.sodogku.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sodogku.libraries.ui.bounceClick
import sodogku.libraries.resources.generated.resources.dogs_a11y
import com.sodogku.system.Radii
import com.sodogku.system.clip
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.game_dogs_found

/**
 * How many dogs are home, as a pill: a dog's face, then `found/total`.
 *
 * The found count is the accent colour and the total is muted, so the number
 * that changes is the one the eye lands on.
 */
@Composable
fun DogCounter(
    found: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val label = stringResource(Res.string.dogs_a11y, found, total)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = modifier
            // Named before the click, which is the only order that reaches the
            // accessibility tree — see `Modifier.bounceClick`.
            .semantics { contentDescription = label }
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .clip(Radii.Round)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(horizontal = Dimension.D500, vertical = Dimension.D200),
    ) {
        Dog(pose = DogPose.Still, size = Dimension.D1100)
        Text(
            text = stringResource(Res.string.game_dogs_found, found, total),
            typography = AppTheme.typography.Display.D800,
            color = if (found == total && total > 0) {
                AppTheme.colors.accentPrimary
            } else {
                AppTheme.colors.text
            },
        )
    }
}

@Preview
@Composable
private fun DogCounterPreview() {
    PreviewContent {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
            DogCounter(found = 0, total = 11)
            DogCounter(found = 11, total = 11)
        }
    }
}

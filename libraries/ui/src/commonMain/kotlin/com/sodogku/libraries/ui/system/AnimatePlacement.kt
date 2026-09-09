package com.sodogku.libraries.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * Makes a composable slide when its parent moves it, instead of teleporting.
 *
 * Knows nothing about why it moved. It watches where it was actually placed,
 * and if that is somewhere new it springs the difference back to zero, so the
 * element appears to travel from the old spot to the new one while the layout
 * system goes on believing it was placed instantly.
 *
 * This is deliberately not part of [AnchoredCard], which is the component that
 * needed it first. Animating requires a coroutine and a layout pass cannot start
 * one, so a component that both places and animates ends up doing its placement
 * maths in composition, guessing sizes it has not measured yet and correcting
 * itself a frame later. Keeping the two apart leaves both boring: one measures
 * and places, the other watches and catches up.
 *
 * The first placement snaps. There is nothing to travel from, and springing an
 * opening card in from the origin is an entrance nobody asked for.
 *
 * Respects [LocalReduceAnimations], and holds still in previews.
 */
fun Modifier.animatePlacement(
    animationSpec: AnimationSpec<IntOffset> = spring(
        // Slightly under-damped, so it settles with a hint of overshoot rather
        // than gliding to a mathematical stop. Critically damped movement across
        // this kind of distance reads as a window being repositioned; a little
        // bounce reads as the thing hopping to where it now belongs.
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val still = LocalReduceAnimations.current || LocalInspectionMode.current

    var placedAt by remember { mutableStateOf<IntOffset?>(null) }
    val travel = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }

    this
        .onPlaced { coordinates ->
            val current = coordinates.positionInParent().round()
            val previous = placedAt
            placedAt = current

            if (previous == null || previous == current || still) return@onPlaced

            scope.launch {
                // Jump back to where it was, then let the spring carry it here.
                // Expressed as a delta rather than an absolute so a parent that
                // also moves does not fight this.
                travel.snapTo(travel.value + (previous - current))
                travel.animateTo(IntOffset.Zero, animationSpec)
            }
        }
        // Read in the layout phase, never in composition: the lambda form of
        // `offset` exists for exactly this reason, and reading the spring in
        // composition would recompose the subtree on every frame of the travel.
        .offset { travel.value }
}

private val IntOffset.Companion.VisibilityThreshold: IntOffset
    get() = IntOffset(1, 1)

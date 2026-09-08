package com.sodogku.devfeedback

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

actual fun Modifier.excludeFromSystemGestures(): Modifier = systemGestureExclusion()

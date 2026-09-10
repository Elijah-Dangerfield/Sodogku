package com.sodogku.libraries.ui.nativeviews

import androidx.compose.runtime.staticCompositionLocalOf
import platform.UIKit.UIView
import kotlin.experimental.ExperimentalObjCName

/**
 * Camera guidance state from the native camera preview.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SodogkuCameraGuidanceState", exact = true)
enum class CameraGuidanceState {
    Ready,           // Good to capture
    TiltedTooMuch,   // Phone is tilted, suggest holding flat
    TooDark,         // Low light, suggest using flash
    TooBlurry        // Motion detected, hold steady
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SodogkuNativeViewFactory", exact = true)
interface NativeViewFactory {

    /**
     * Toggle flash/torch on the camera preview.
     * @return true if flash is now enabled, false if disabled
     */
    fun toggleCameraFlash(view: UIView): Boolean
    
    /**
     * Check if flash is currently enabled.
     */
    fun isCameraFlashEnabled(view: UIView): Boolean
}

val LocalNativeViewFactory = staticCompositionLocalOf<NativeViewFactory?> { null }

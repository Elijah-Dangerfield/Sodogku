package com.sodogku

import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sodogku.libraries.telemetry.impl.AndroidJankMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate()
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge with light status bar (dark icons)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        // Keep the splash screen on until AppViewModel has determined the destination.
        // AppViewModel is a singleton, so this is the same instance used in App composable.
        splashScreen.setKeepOnScreenCondition {
            !appComponent.appViewModel.isReady.value
        }

        // JankStats needs a Window, so it can only be armed once the Activity
        // has one. Attaching after setContent would miss the first frames,
        // which are the ones most likely to be janky.
        (appComponent.jankMonitor as? AndroidJankMonitor)?.attach(window)

        setContent {
            App(appComponent)
        }

        reportStartupWhenReady()
    }

    /**
     * Closes the cold-start measurement at the moment the user can actually use
     * the app, and tells the platform the same thing.
     *
     * The timing is the whole point. [AppViewModel.isReady] is when the start
     * destination is resolved and the splash condition lets go — but the frame
     * behind the splash has not been drawn yet at that instant. `onPreDraw`
     * fires immediately *before* that frame, so posting from inside it lands
     * just after the pixels are up. Measuring at `isReady` instead would
     * under-report by however long the first real composition takes, which is
     * the slowest frame of the launch and the one most worth counting.
     *
     * [reportFullyDrawn] hands that same instant to the platform, which is what
     * Play Console grades "fully drawn" startup on. Without it Play measures to
     * the first frame — the splash — and grades the app on a number no user ever
     * experiences.
     */
    private fun reportStartupWhenReady() {
        lifecycleScope.launch {
            appComponent.appViewModel.isReady.first { it }

            val decorView = window.decorView
            decorView.viewTreeObserver.addOnPreDrawListener(
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        decorView.viewTreeObserver.removeOnPreDrawListener(this)
                        decorView.post {
                            reportFullyDrawn()
                            appComponent.startupReporter.onAppReady()
                        }
                        return true
                    }
                },
            )
        }
    }

    /**
     * The warm half of deep-link handling, launcher shortcuts included.
     *
     * A cold launch needs nothing here: NavController reads the launching
     * Activity's intent itself once the graph is built. Nothing reads a
     * *second* intent, though, so a `sodogku://` link arriving at a running app
     * used to be silently dropped. It goes through the same bridge iOS uses.
     *
     * `setIntent` is deliberately not called. The intent is handled from here
     * once, and leaving `getIntent()` alone means a NavController built later —
     * a new one is created if the App composable is ever recreated — cannot
     * find it and open the same link a second time.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { appComponent.deepLinkBridge.emit(it.toString()) }
    }

    override fun onStop() {
        super.onStop()
        // Flush whatever this screen accumulated before the process can be
        // killed in the background. A session that never returns still reports
        // the screen it was on, which is the one worth knowing about.
        appComponent.jankMonitor.onBackground()
    }

    override fun onDestroy() {
        (appComponent.jankMonitor as? AndroidJankMonitor)?.detach()
        super.onDestroy()
    }

    private val appComponent get() = (application as SodogkuApplication).appComponent
}

package com.sodogku.libraries.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.ComposeNavigatorDestinationBuilder
import androidx.navigation.get
import androidx.navigation.navDeepLink
import androidx.navigation.navigation as composeNestedGraph
import com.sodogku.libraries.ui.components.dialog.DialogState
import com.sodogku.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.sodogku.libraries.navigation.floatingwindow.DialogDestination
import com.sodogku.libraries.navigation.floatingwindow.FloatingWindowNavDestinationBuilder
import com.sodogku.libraries.navigation.floatingwindow.FloatingWindowNavigator
import com.sodogku.libraries.navigation.floatingwindow.BottomSheetDestination
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * NavTypes for the arguments every [Route] carries on its **base class**.
 *
 * Type-safe navigation turns each serialized constructor `val` of a [Route] into
 * a nav argument, and on iOS/Native — which has no reflection fallback — every
 * non-primitive arg needs an explicit [NavType] in the destination's typeMap.
 * Miss one and graph-build throws `IllegalArgumentException: ... could not find
 * any NavType for argument <name>`. Every destination + deep-link builder below
 * merges this in.
 *
 * Every type registered here MUST be `@Serializable`. On Native there is no
 * built-in enum NavType (`parseEnum` returns UNKNOWN), so enum args are resolved
 * *through* this typeMap — and androidx.navigation's `matchKType` calls the
 * reflective `serializerOrNull(kType)` on every key while resolving every route
 * arg. A key whose type has no reflectively-resolvable serializer (a plain,
 * non-`@Serializable` enum) makes that return null and graph-build throws — with
 * a message that misleadingly names whatever arg was being resolved at the time.
 *
 * Keep in sync with the serialized properties of [Route]:
 *  - `enter` / `exit` / `popExit` → [AnimationType]
 */
val baseRouteTypeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = mapOf(
    typeOf<AnimationType>() to serializableType<AnimationType>(),
)

inline fun <reified T : Route> NavGraphBuilder.screen(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline enterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        null,
    noinline exitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        null,
    noinline popEnterTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    EnterTransition?)? =
        enterTransition,
    noinline popExitTransition:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    ExitTransition?)? =
        exitTransition,
    noinline sizeTransform:
    (AnimatedContentTransitionScope<NavBackStackEntry>.() -> @JvmSuppressWildcards
    SizeTransform?)? =
        null,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    destination(
        ComposeNavigatorDestinationBuilder(
            provider[ComposeNavigator::class],
            T::class,
            typeMap + baseRouteTypeMap,
            content
        )
            .apply {
                deepLinks.forEach { deepLink -> deepLink(deepLink) }
                this.enterTransition = enterTransition
                this.exitTransition = exitTransition
                this.popEnterTransition = popEnterTransition
                this.popExitTransition = popExitTransition
                this.sizeTransform = sizeTransform
            }
    )
}

/**
 * Builds a [NavDeepLink] for a [Route], injecting the [AnimationType]
 * NavType the way [screen]/[dialog]/[bottomSheet] do for their
 * destinations. Every [Route] carries `enter`/`exit`/`popExit`
 * AnimationType args, so a raw `navDeepLink<T>(basePath = …)` (empty
 * typeMap) crashes at graph-build time with
 * "could not find any NavType for argument enter of type AnimationType".
 * Always use this for route deep links — never the bare `navDeepLink`.
 */
inline fun <reified T : Route> routeDeepLink(
    basePath: String,
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
): NavDeepLink = navDeepLink<T>(
    basePath = basePath,
    typeMap = typeMap + baseRouteTypeMap,
)

inline fun <reified T : Route> NavGraphBuilder.dialog(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable (NavBackStackEntry, DialogState) -> Unit
) {
    destination(
        FloatingWindowNavDestinationBuilder(
            provider[FloatingWindowNavigator::class],
            T::class,
            typeMap + baseRouteTypeMap
        ) { backStackEntry ->
            DialogDestination(backStackEntry, content)
        }
            .apply {
                deepLinks.forEach { deepLink -> deepLink(deepLink) }
            }
    )
}


/**
 * Type-safe nested graph wrapper. Use to group a tab's start
 * destination with its sub-routes (sheets, dialogs, detail screens) so
 * a shared `ViewModel` can be scoped to the **graph entry** instead of
 * one of the leaf entries.
 *
 * Pattern:
 * ```
 * navigation<SettingsGraph>(startDestination = SettingsRoute()) {
 *     screen<SettingsRoute> { ... }
 *     bottomSheet<SettingsDetailSheetRoute> { ... }
 * }
 * ```
 *
 * Both the screen and the sheet can resolve the same graph-scoped
 * ViewModel — the graph entry survives as long as **anything** in the
 * tab is on the stack, which is the natural scope for tab-wide state.
 */
inline fun <reified T : Route> NavGraphBuilder.navigation(
    startDestination: Route,
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    noinline builder: NavGraphBuilder.() -> Unit,
) {
    val resolvedTypeMap = typeMap + baseRouteTypeMap
    composeNestedGraph<T>(
        startDestination = startDestination,
        typeMap = resolvedTypeMap,
        builder = builder,
    )
}

inline fun <reified T : Route> NavGraphBuilder.bottomSheet(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable (NavBackStackEntry, BottomSheetState) -> Unit
) {
    destination(
        FloatingWindowNavDestinationBuilder(
            provider[FloatingWindowNavigator::class],
            T::class,
            typeMap + baseRouteTypeMap
        ) { backStackEntry ->
            BottomSheetDestination(backStackEntry, content)
        }
    ).apply {
        deepLinks.forEach { deepLink -> deepLink(deepLink) }
    }
}
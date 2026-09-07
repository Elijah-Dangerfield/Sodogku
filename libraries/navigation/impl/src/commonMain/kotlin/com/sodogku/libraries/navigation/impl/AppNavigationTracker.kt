package com.sodogku.libraries.navigation.impl

import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.navigation.NavigationTracker
import com.sodogku.libraries.navigation.Route
import com.sodogku.libraries.navigation.TrackableRoute
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Tracks navigation events and updates AppCache accordingly.
 * 
 * Any route that extends TrackableRoute will automatically have its
 * visits tracked using its trackingKey. No additional code changes needed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NavigationTracker::class)
@Inject
class AppNavigationTracker(
    private val appCache: AppCache
) : NavigationTracker {

    override suspend fun onNavigate(route: Route) {
        if (route is TrackableRoute) {
            appCache.update { it.incrementVisit(route.trackingKey) }
        }
    }
}

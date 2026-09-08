package com.sodogku

import com.sodogku.libraries.ads.AdNetwork
import com.sodogku.libraries.billing.StoreBilling
import com.sodogku.libraries.sodogku.PermissionManager
import com.sodogku.libraries.review.ReviewLauncher
import com.sodogku.libraries.ui.nativeviews.NativeViewFactory
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The iOS half of the graph.
 *
 * Everything here is a Swift object that Kotlin cannot construct: the platform
 * SDKs (GoogleMobileAds, StoreKit) ship as iOS frameworks, and reaching them
 * through cinterop would mean maintaining a Kotlin binding for an SDK Google
 * changes on their schedule. Swift implements the narrow seam instead — see
 * `apps/ios/iosApp/Platform/` — and the policy above it stays in common Kotlin
 * where both platforms share it and tests can reach it.
 */
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent(
    private val permissionManager: PermissionManager,
    private val reviewLauncher: ReviewLauncher,
    private val adNetwork: AdNetwork,
    private val storeBilling: StoreBilling,
    val nativeViewFactory: NativeViewFactory
) : AppComponent {

    @Provides
    fun providePermissionManager(): PermissionManager = permissionManager

    @Provides
    fun provideReviewLauncher(): ReviewLauncher = reviewLauncher

    @Provides
    fun provideAdNetwork(): AdNetwork = adNetwork

    @Provides
    fun provideStoreBilling(): StoreBilling = storeBilling
}


@MergeComponent.CreateComponent
expect fun create(
    permissionManager: PermissionManager,
    reviewLauncher: ReviewLauncher,
    adNetwork: AdNetwork,
    storeBilling: StoreBilling,
    nativeViewFactory: NativeViewFactory
): IosAppComponent

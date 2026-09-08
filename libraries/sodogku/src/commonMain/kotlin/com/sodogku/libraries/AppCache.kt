package com.sodogku.libraries.sodogku

import com.sodogku.libraries.storage.Cache
import com.sodogku.libraries.storage.CacheFactory
import com.sodogku.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * What an ad refill tops a consumable up to. Holdings may exceed it.
 *
 * The number the *game* refills to is `boosters.refillTo` in remote config;
 * this is the copy the booster prompt still prints and the seed for [AppData]'s
 * bone count. Keep the two in step until the prompt is handed the live value.
 */
const val ConsumableRefillTo: Int = 3

/**
 * In-memory + persistent cache for app-wide state that doesn't need to be in the database.
 */
@Serializable
data class AppData(
    // Onboarding
    val hasUserOnboarded: Boolean = false,

    /**
     * True once the player has been through (or deliberately skipped) the
     * guided first three levels. Separate from [hasUserOnboarded] so the
     * tutorial can be re-run from settings without resetting onboarding.
     */
    val hasCompletedTutorial: Boolean = false,

    /**
     * Identifies regions by glyph as well as hue. Region colour is the core
     * mechanic, and no ten-colour set survives red-green colour vision
     * deficiency, so this is a real accessibility mode rather than a preference.
     */
    val colorblindMode: Boolean = false,

    /** Vibration on marks, placements and strikes. */
    val hapticsEnabled: Boolean = true,

    /**
     * Swaps the animated dogs for stills and shortens the board's entrance.
     * A battery setting rather than an accessibility one, though it serves both.
     */
    val reduceAnimations: Boolean = false,

    /**
     * Whether badges are *shown* — the grid and the unlock toast.
     *
     * Display only. `AchievementsRepository` keeps recording while this is
     * false, deliberately, so a player who switches them back on months later
     * sees the history they actually earned instead of an empty grid. Nothing
     * that writes to the achievement log may read this.
     */
    val achievementsVisible: Boolean = true,

    /**
     * Bones held, and **the only place the count lives** (SPEC 1.4).
     *
     * One number across the campaign and the daily. It used to be per-attempt,
     * reset to three by every `startAttempt`, which meant starting anything
     * refilled it for free — reported from a device as bones coming back on the
     * way out of a lost board. Not nullable like [sniffs] and [treats] because
     * no config key opens the game with a different number: three strikes is a
     * game rule, and the seed is the same constant the refill tops back up to.
     */
    val bones: Int = ConsumableRefillTo,

    /**
     * The other two held consumables. Not capped at three — a refill tops up to
     * `boosters.refillTo`, but clearing levels grants extra, so the store is a
     * reward for playing rather than a meter that only ever empties.
     *
     * Null until the player has been granted any. The opening handful is
     * `boosters.startingSniffs` / `startingTreats`, and a default written here
     * instead would be a second answer to the same question — one an operator
     * cannot change, and the one that would win, because a record that already
     * says "3" is indistinguishable from a player who spent down to three.
     */
    val sniffs: Int? = null,
    val treats: Int? = null,

    /** Boosters whose first-use explainer has been seen, by name. */
    val explainedBoosters: Set<String> = emptySet(),

    /**
     * The attempt in progress, if there is one. See [BoardSnapshot].
     *
     * One slot, not one per level: a player has one board on the go, and keeping
     * a stack of half-finished levels would turn resuming into a choice.
     */
    val boardInProgress: BoardSnapshot? = null,

    /**
     * Stable per-install identifier, minted on first read and persisted for
     * the app's lifetime on this device (survives sign-out; dies with
     * uninstall). Sent as X-Install-Id on authenticated requests so the
     * server can associate anonymous accounts from the same install.
     * Stored as a string (UUID canonical form) so the JSON serializer
     * doesn't need a Uuid-aware adapter on every cache read.
     */
    val installId: String? = null,

    // Screen visits - automatically tracked for any TrackableRoute
    val screenVisits: Map<String, Int> = emptyMap(),

    // User actions
    val feedbacksGiven: Int = 0,
    val bugsReported: Int = 0,

    /** Epoch-ms — first observed by the review coordinator. 0 = uncaptured. */
    val reviewInstallAt: Long = 0L,

    /** Epoch-ms — last review prompt the coordinator forwarded to the platform. 0 = never. */
    val lastReviewPromptAt: Long = 0L,

    /**
     * Whether this device owns Sodogku Pro, cached **true until proven false**
     * (SPEC 5.2). Written only by `RealEntitlements`: set on a purchase or a
     * restore, and cleared *only* on an explicit "not entitled" from the store.
     * A store we could not reach leaves it alone, which is what keeps a paying
     * customer ad-free on a train.
     *
     * There is no account and no server receipt store, so this plus the store's
     * own restore is the whole of the entitlement. It dies with the install,
     * and Settings says so.
     */
    val isProEntitled: Boolean = false,

    /**
     * The `legal.termsVersion` / `legal.privacyVersion` this device has accepted,
     * and when. `legalAcceptedAt` of 0 means *never asked*, which is a different
     * state from "accepted version 0" and is why the timestamp is load-bearing
     * rather than a record: a fresh install is seeded with whatever the current
     * versions are, because the version gate exists to notice a *change* since
     * acceptance and there has not been one yet.
     *
     * Under the no-accounts rule there is no server-side record of consent and
     * no need for one (SPEC 7.3).
     */
    val acceptedTermsVersion: Int = 0,
    val acceptedPrivacyVersion: Int = 0,
    val legalAcceptedAt: Long = 0L,

    /**
     * The `upgrade.softUpdateVersionCode` the player has already waved away. The
     * suggestion is a banner, not a block, so it has to stay dismissed — but
     * only for the version it was dismissed at, so raising the soft-update
     * target later asks again.
     */
    val softUpdateDismissedFor: Int = 0,
) {
    /**
     * Get the visit count for a screen by its tracking key.
     */
    fun getVisitCount(trackingKey: String): Int = screenVisits[trackingKey] ?: 0
    
    /**
     * Increment the visit count for a screen.
     */
    fun incrementVisit(trackingKey: String): AppData = copy(
        screenVisits = screenVisits + (trackingKey to (getVisitCount(trackingKey) + 1))
    )
}

interface AppCache : Cache<AppData>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppCache::class)
@Inject
class AppCacheImpl(
    cacheFactory: CacheFactory
) : AppCache, Cache<AppData> by cacheFactory.persistent(
    name = "app_data",
    serializer = versionedJsonSerializer(
        defaultValue = { AppData() },
    )
)
/**
 * Reset the **account-scoped** fields back to defaults while preserving every
 * device-scoped setting (install id, screen visits, feedback counters…). Used
 * whenever the active user changes (account switch or sign-out / delete) so
 * the next account doesn't inherit the previous one's state.
 *
 * This is one `UserScopedClearer` in the dump the auth layer runs on a user
 * change: DB tables are wiped by `UserScopedDaoCleaner`, the profile caches by
 * `UserScopedProfileCacheCleaner`, and this covers the account-scoped fields
 * that live in [AppData]. Add any new account-scoped field here.
 */
fun AppData.resetAccountScoped(): AppData = copy(
    // A full sign-out → continue-as-guest is a deliberate fresh start, so the
    // next identity is re-offered onboarding rather than inheriting the
    // previous user's completion.
    hasUserOnboarded = false,
)

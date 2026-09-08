package com.sodogku.features.gate.impl

import androidx.lifecycle.viewModelScope
import com.sodogku.features.gate.BlockingGate
import com.sodogku.features.gate.LaunchGates
import com.sodogku.features.gate.LegalInputs
import com.sodogku.features.gate.MaintenanceInputs
import com.sodogku.features.gate.NoticeGate
import com.sodogku.features.gate.StoreListing
import com.sodogku.features.gate.UpgradeInputs
import com.sodogku.features.gate.resolveLaunchGates
import com.sodogku.libraries.config.AppConfigRepository
import com.sodogku.libraries.config.values.AppMaintenanceMessage
import com.sodogku.libraries.config.values.AppMaintenanceMode
import com.sodogku.libraries.config.values.AppMinSupportedVersion
import com.sodogku.libraries.config.values.AppSoftUpdateVersion
import com.sodogku.libraries.config.values.LegalForceReacceptBelow
import com.sodogku.libraries.config.values.LegalPrivacyUrl
import com.sodogku.libraries.config.values.LegalPrivacyVersion
import com.sodogku.libraries.config.values.LegalTermsUrl
import com.sodogku.libraries.config.values.LegalTermsVersion
import com.sodogku.libraries.core.BuildInfo
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.SEAViewModel
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The launch gates, resolved live.
 *
 * **Every config value is read at the moment the gate is decided, never captured
 * at construction.** SPEC 4.2 asks for exactly that for the kill switches, and it
 * is the difference between an operator's change landing on the next foreground
 * and landing after a force-quit nobody is going to perform mid-incident. The
 * trigger is `configStream()` combined with the `AppData` the record lives in, so
 * a config refresh *or* an acceptance re-runs the whole decision.
 *
 * **Nothing thrown in here can raise a gate.** The resolution is wrapped in
 * [Catching] and a failure resolves to [LaunchGates] with nothing gated — a
 * malformed value on an `Int` key throws rather than falls back on debug builds
 * (`getValueRecursive` calls `throwIfDebug`), and an exception escaping
 * `handleAction` would also kill the action loop for the rest of the process.
 * Both of those failure modes end at "the player plays".
 *
 * Scoped as a singleton and driven from `App.kt` rather than from a nav
 * destination: a blocking gate is rendered *instead of* the nav host, so there is
 * no back stack entry to pop and no deep link that can land behind it.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@Inject
class LaunchGateViewModel(
    private val appCache: AppCache,
    private val appConfigRepository: AppConfigRepository,
    private val minSupportedVersion: AppMinSupportedVersion,
    private val softUpdateVersion: AppSoftUpdateVersion,
    private val maintenanceMode: AppMaintenanceMode,
    private val maintenanceMessage: AppMaintenanceMessage,
    private val termsVersion: LegalTermsVersion,
    private val privacyVersion: LegalPrivacyVersion,
    private val forceReacceptBelow: LegalForceReacceptBelow,
    private val termsUrl: LegalTermsUrl,
    private val privacyUrl: LegalPrivacyUrl,
    private val clock: Clock,
) : SEAViewModel<LaunchGateState, LaunchGateEvent, LaunchGateAction>(
    initialStateArg = LaunchGateState(),
) {

    /**
     * The maintenance banner the player has closed this session, by its text.
     *
     * A plain field rather than state: `state` lags `updateState` by a dispatch,
     * and this is read on the very next resolve — which an `AppData` write can
     * trigger a millisecond later. It is deliberately not persisted; see
     * [MaintenanceInputs.dismissedBanner].
     */
    private var dismissedBanner: String? = null

    /** The last gate reported to telemetry, so `gate.raised` fires on edges only. */
    private var lastReported: String? = null

    private val logger = KLog.withTag("LaunchGate")

    init {
        viewModelScope.launch {
            combine(appConfigRepository.configStream(), appCache.updates) { _, data -> data }
                .collect { data -> takeAction(LaunchGateAction.Resolve(data)) }
        }
    }

    override suspend fun handleAction(action: LaunchGateAction) {
        when (action) {
            is LaunchGateAction.Resolve -> action.resolve(action.appData)
            is LaunchGateAction.AcceptLegal -> record(action.termsVersion, action.privacyVersion)
            is LaunchGateAction.DismissNotice -> action.dismiss(action.notice)
            LaunchGateAction.OpenStore -> sendEvent(LaunchGateEvent.OpenLink(StoreListing.url()))
            LaunchGateAction.OpenTerms -> sendEvent(LaunchGateEvent.OpenLink(termsUrl()))
            LaunchGateAction.OpenPrivacy -> sendEvent(LaunchGateEvent.OpenLink(privacyUrl()))
        }
    }

    private suspend fun LaunchGateAction.resolve(data: AppData) {
        if (data.legalAcceptedAt == 0L) {
            // A first launch has no earlier acceptance for a version bump to be
            // measured against, so the versions in hand are recorded and nothing
            // is gated. The write re-enters this flow with the record in place.
            // This is also what makes the blocking re-accept sheet unreachable on
            // a fresh install, whatever `forceReacceptBelow` says.
            record(termsVersion(), privacyVersion())
            return
        }

        val gates = Catching { gatesFor(data) }
            .logOnFailure { "Failed to resolve the launch gates; letting the player play" }
            .getOrNull()
            ?: LaunchGates()

        report(gates)
        updateState { it.copy(blocking = gates.blocking, notice = gates.notice) }
    }

    /**
     * `gate.raised`, on the edge only.
     *
     * How many installs are behind the wall is the first number anyone wants
     * during an incident, and it is one the backend cannot answer — the whole
     * point of a maintenance gate is that our server is the thing that is down.
     * The telemetry pipe goes direct to Grafana, so this still ships.
     *
     * Keyed on the gate's identity rather than the whole object, so an operator
     * rewording a maintenance message does not read as a second incident, and
     * every resolve (which is every `AppData` write) does not emit.
     */
    private fun report(gates: LaunchGates) {
        val raised = gates.blocking?.eventName() ?: gates.notice?.eventName()
        if (raised == lastReported) return
        lastReported = raised
        if (raised == null) return

        logger.logEvent(
            "gate.raised",
            "gate" to raised,
            "blocking" to (gates.blocking != null),
        )
    }

    private fun gatesFor(data: AppData): LaunchGates = resolveLaunchGates(
        upgrade = UpgradeInputs(
            installedVersionCode = BuildInfo.versionCode,
            minSupportedVersionCode = minSupportedVersion(),
            softUpdateVersionCode = softUpdateVersion(),
            softUpdateDismissedFor = data.softUpdateDismissedFor,
        ),
        maintenance = MaintenanceInputs(
            mode = maintenanceMode(),
            message = maintenanceMessage(),
            dismissedBanner = dismissedBanner,
        ),
        legal = LegalInputs(
            termsVersion = termsVersion(),
            privacyVersion = privacyVersion(),
            acceptedTermsVersion = data.acceptedTermsVersion,
            acceptedPrivacyVersion = data.acceptedPrivacyVersion,
            forceReacceptBelow = forceReacceptBelow(),
            hasEverAccepted = true,
        ),
    )

    private suspend fun LaunchGateAction.dismiss(notice: NoticeGate) {
        when (notice) {
            // Persisted, so it does not come back every launch — but keyed on the
            // version it was dismissed at, so raising the soft-update target asks
            // again.
            is NoticeGate.SoftUpdate -> persist { it.copy(softUpdateDismissedFor = notice.versionCode) }

            // Closing the "terms have moved" banner is the acceptance. The banner
            // says so; it is the non-material half of SPEC 7.3, where continuing
            // to play is consent and the blocking sheet is what a material change
            // gets instead.
            is NoticeGate.LegalUpdated -> record(notice.termsVersion, notice.privacyVersion)

            is NoticeGate.Maintenance -> {
                dismissedBanner = notice.message
                updateState { it.copy(notice = null) }
            }
        }
    }

    /**
     * Records acceptance of exactly the versions that were on screen.
     *
     * Both are parameters rather than a re-read of the config: a refresh landing
     * between the prompt and the tap would otherwise write consent to a version
     * nobody was ever shown. `maxOf` because the record only moves forward — a
     * config that regressed `termsVersion` must not un-accept anything.
     */
    private suspend fun record(terms: Int, privacy: Int) {
        val now = clock.now().toEpochMilliseconds()
        persist {
            it.copy(
                acceptedTermsVersion = maxOf(it.acceptedTermsVersion, terms),
                acceptedPrivacyVersion = maxOf(it.acceptedPrivacyVersion, privacy),
                legalAcceptedAt = now,
            )
        }
    }

    private suspend fun persist(transform: (AppData) -> AppData) {
        Catching { appCache.update(transform) }
            .logOnFailure { "Failed to persist a launch-gate decision" }
    }
}

data class LaunchGateState(
    val blocking: BlockingGate? = null,
    val notice: NoticeGate? = null,
)

/**
 * Stable, low-cardinality names for the funnel. Not the class name: these are
 * dashboard keys, and a rename in Kotlin should not silently start a new series.
 */
private fun BlockingGate.eventName(): String = when (this) {
    BlockingGate.ForceUpdate -> "force_update"
    is BlockingGate.Maintenance -> "maintenance"
    is BlockingGate.ReacceptLegal -> "legal_reaccept"
}

private fun NoticeGate.eventName(): String = when (this) {
    is NoticeGate.Maintenance -> "maintenance_banner"
    is NoticeGate.LegalUpdated -> "legal_updated"
    is NoticeGate.SoftUpdate -> "soft_update"
}

sealed interface LaunchGateEvent {
    /** Terms, privacy and the store listing are all pages, so they open in a browser. */
    data class OpenLink(val url: String) : LaunchGateEvent
}

sealed interface LaunchGateAction {
    /** Config or the record changed; re-decide. */
    data class Resolve(val appData: AppData) : LaunchGateAction

    /**
     * The versions travel on the action rather than being read back off `state`,
     * which lags `updateState` by a dispatch.
     */
    data class AcceptLegal(val termsVersion: Int, val privacyVersion: Int) : LaunchGateAction

    data class DismissNotice(val notice: NoticeGate) : LaunchGateAction

    data object OpenStore : LaunchGateAction
    data object OpenTerms : LaunchGateAction
    data object OpenPrivacy : LaunchGateAction
}

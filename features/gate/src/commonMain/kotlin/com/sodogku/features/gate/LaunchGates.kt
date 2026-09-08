package com.sodogku.features.gate

import com.sodogku.libraries.config.values.AppMaintenanceMode.Companion.MAINTENANCE_BANNER
import com.sodogku.libraries.config.values.AppMaintenanceMode.Companion.MAINTENANCE_BLOCKING

/**
 * The launch gates: the three controls an operator reaches for in an incident,
 * resolved from remote config and the device's own record.
 *
 * Every function in this file is pure, and every one of them answers "block
 * nobody" for an input it does not fully understand. That is not defensiveness,
 * it is the rule SPEC 4.2 states and `docs/decisions.md` explains: a force-update
 * gate is the only config value that can brick every install at once, so a
 * missing, partial, malformed or unreachable config has to resolve to letting the
 * player play. Every one of the keys behind this defaults to 0 or `off`.
 *
 * The inputs are plain numbers and strings rather than `ConfiguredValue`s so the
 * whole decision is testable without a config map, a DI graph or a device — see
 * `LaunchGatesTest`, which is mostly a list of ways an outage must *not* be able
 * to reach a wall.
 */

/** A gate that stops the app. At most one is ever raised; see [resolveLaunchGates]. */
sealed interface BlockingGate {

    /** Below `upgrade.minSupportedVersionCode`. The only exit is the store. */
    data object ForceUpdate : BlockingGate

    /** `upgrade.maintenanceMode` is `blocking`. [message] is operator-written. */
    data class Maintenance(val message: String) : BlockingGate

    /**
     * An accepted legal version below `legal.forceReacceptBelow`.
     *
     * The two versions are carried on the gate rather than re-read when the
     * player accepts: a config refresh landing between the prompt and the tap
     * would otherwise record consent to a version nobody was shown.
     */
    data class ReacceptLegal(val termsVersion: Int, val privacyVersion: Int) : BlockingGate
}

/** A gate that says something and gets out of the way. */
sealed interface NoticeGate {

    /** `upgrade.maintenanceMode` is `banner`. [message] is operator-written. */
    data class Maintenance(val message: String) : NoticeGate

    /** Terms or privacy moved on, and the change is not material enough to force. */
    data class LegalUpdated(val termsVersion: Int, val privacyVersion: Int) : NoticeGate

    /** Below `upgrade.softUpdateVersionCode`. Dismissible, and stays dismissed. */
    data class SoftUpdate(val versionCode: Int) : NoticeGate
}

/**
 * What the gates want to do to this launch. Both null is the overwhelmingly
 * common answer and the one an outage must produce.
 */
data class LaunchGates(
    val blocking: BlockingGate? = null,
    val notice: NoticeGate? = null,
)

/** `upgrade.minSupportedVersionCode` / `softUpdateVersionCode` against this build. */
data class UpgradeInputs(
    val installedVersionCode: Int,
    val minSupportedVersionCode: Int,
    val softUpdateVersionCode: Int,
    val softUpdateDismissedFor: Int,
)

/** `upgrade.maintenanceMode` and the raw `upgrade.maintenanceMessage` beside it. */
data class MaintenanceInputs(
    val mode: String,
    val message: String,

    /**
     * A banner the player has already read and closed, by its exact text.
     * Session-scoped and never persisted: an incident that is still running
     * should say so again on the next launch, and an operator who rewords the
     * message has said something new. It has no effect on the blocking mode —
     * you cannot dismiss a wall.
     */
    val dismissedBanner: String? = null,
)

/** `legal.*` against what this device has already accepted. */
data class LegalInputs(
    val termsVersion: Int,
    val privacyVersion: Int,
    val acceptedTermsVersion: Int,
    val acceptedPrivacyVersion: Int,
    val forceReacceptBelow: Int,

    /**
     * False until the device has recorded an acceptance at all. A first launch
     * has nothing to be out of date *against*, so it is never gated — the
     * versions in hand are seeded as accepted instead.
     */
    val hasEverAccepted: Boolean,
)

/**
 * Resolves at most one blocking gate and, if nothing blocks, at most one notice.
 *
 * **Blocking order is force-update, then maintenance, then legal.** An update is
 * the only one of the three the player can act on permanently, and it also
 * replaces the client that is reading this config — telling somebody on an
 * unsupported build that we are doing maintenance sends them back tomorrow to the
 * same wall. Legal is last because consent to keep using an app is worth nothing
 * while the app is unusable anyway.
 *
 * **Notice order is maintenance, then legal, then update.** One strip, and the
 * incident outranks the paperwork, which outranks the suggestion.
 */
fun resolveLaunchGates(
    upgrade: UpgradeInputs,
    maintenance: MaintenanceInputs,
    legal: LegalInputs,
): LaunchGates {
    val maintenanceBlock = maintenance.messageFor(MAINTENANCE_BLOCKING)
    val blocking: BlockingGate? = when {
        upgrade.forcesUpdate() -> BlockingGate.ForceUpdate
        maintenanceBlock != null -> BlockingGate.Maintenance(maintenanceBlock)
        legal.forcesReaccept() -> BlockingGate.ReacceptLegal(legal.termsVersion, legal.privacyVersion)
        else -> null
    }
    if (blocking != null) return LaunchGates(blocking = blocking)

    val maintenanceNotice = maintenance.messageFor(MAINTENANCE_BANNER)
        ?.takeIf { it != maintenance.dismissedBanner }
    val notice: NoticeGate? = when {
        maintenanceNotice != null -> NoticeGate.Maintenance(maintenanceNotice)
        legal.isOutOfDate() -> NoticeGate.LegalUpdated(legal.termsVersion, legal.privacyVersion)
        upgrade.suggestsUpdate() -> NoticeGate.SoftUpdate(upgrade.softUpdateVersionCode)
        else -> null
    }
    return LaunchGates(notice = notice)
}

/**
 * Zero blocks nobody, and so does a build that cannot say what version it is.
 * The second guard matters more than it looks: [UpgradeInputs.installedVersionCode]
 * comes from generated build config, and a build that reported 0 would be below
 * every threshold an operator could ever set.
 */
private fun UpgradeInputs.forcesUpdate(): Boolean =
    installedVersionCode > 0 &&
        minSupportedVersionCode > 0 &&
        installedVersionCode < minSupportedVersionCode

private fun UpgradeInputs.suggestsUpdate(): Boolean =
    installedVersionCode > 0 &&
        softUpdateVersionCode > 0 &&
        installedVersionCode < softUpdateVersionCode &&
        softUpdateDismissedFor < softUpdateVersionCode

/**
 * The operator's message when [mode] is [wanted], and null otherwise.
 *
 * **A maintenance gate with nothing to say is treated as no gate at all**, which
 * is the one rule here that is a judgement rather than a default. `mode` and
 * `message` are two keys, so "blocking with no message" is what a half-finished
 * config write looks like from the client — and the message is the entire
 * content of the screen, so raising the wall without it strands the player on a
 * blank apology. Both keys or neither.
 *
 * Casing is forgiving for the same reason the boolean parser is: the console
 * takes raw text and `Blocking` is not a mistake worth punishing. Anything that
 * is not one of the three declared words resolves to off.
 */
private fun MaintenanceInputs.messageFor(wanted: String): String? {
    if (mode.trim().lowercase() != wanted) return null
    return message.trim().takeIf { it.isNotEmpty() }
}

private fun LegalInputs.isOutOfDate(): Boolean =
    hasEverAccepted &&
        (acceptedTermsVersion < termsVersion || acceptedPrivacyVersion < privacyVersion)

/**
 * Whether the accepted versions are behind `legal.forceReacceptBelow`.
 *
 * **The floor is capped per document at the version actually on offer.** A
 * `forceReacceptBelow` of 5 against a `termsVersion` of 3 is unsatisfiable:
 * accepting records 3, 3 is still below 5, and the player is walled out of the
 * game permanently by a config they can do nothing about. Capping means every
 * block this raises is one the accept button can clear, which is the difference
 * between a gate and a brick.
 */
private fun LegalInputs.forcesReaccept(): Boolean {
    if (!hasEverAccepted) return false
    val termsFloor = minOf(forceReacceptBelow, termsVersion)
    val privacyFloor = minOf(forceReacceptBelow, privacyVersion)
    return acceptedTermsVersion < termsFloor || acceptedPrivacyVersion < privacyFloor
}

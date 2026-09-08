package com.sodogku.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A one-line success/error banner shown under the connection panel. */
internal data class Status(val ok: Boolean, val message: String)

/**
 * A failed write, kept until the operator dismisses it. The transient status
 * line gets overwritten by the next action; this list doesn't — so a rejected
 * write can never silently "snap back" (the reload refreshes server truth, but
 * the attempted value survives here).
 */
internal data class ErrorEntry(
    val time: String,
    val operation: String,
    val attempted: String?,
    val message: String,
)

internal fun nowTimeLabel(): String = js("new Date().toLocaleTimeString()") as String

/**
 * Everything a view needs to make a write: the api, which environment it hits,
 * and the shared safety plumbing. [confirmWrite] is the only mutation path in
 * the console — it puts a before→after confirm sheet in front of the operator,
 * and on failure records a persistent [ErrorEntry].
 *
 * Reloads on both success and failure: a rejected write can still have changed
 * state (or the operator's view of it can be stale), so we always re-fetch so
 * the flag/rule list reflects what the server actually holds.
 */
internal class AdminCtx(
    val api: AdminApi,
    val scope: CoroutineScope,
    val envName: String,
    val isProd: Boolean,
    val setStatus: (Status) -> Unit,
    val reload: () -> Unit,
    private val requestConfirm: (PendingWrite) -> Unit,
    private val reportError: (ErrorEntry) -> Unit,
) {
    fun confirmWrite(
        title: String,
        flagPath: String?,
        before: String,
        after: String,
        success: String,
        warning: String? = null,
        block: suspend () -> Unit,
    ) {
        requestConfirm(
            PendingWrite(
                title = title,
                envName = envName,
                isProd = isProd,
                flagPath = flagPath,
                before = before,
                after = after,
                warning = warning,
                requireEnvTyping = isProd && warning != null,
                onConfirm = {
                    scope.launch {
                        Catching { block() }
                            .onSuccess { setStatus(Status(true, success)) }
                            .onFailure { error ->
                                val message = error.message ?: "Request failed"
                                reportError(ErrorEntry(nowTimeLabel(), title, after, message))
                                setStatus(Status(false, message))
                            }
                        reload()
                    }
                },
            ),
        )
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun parseJsonOrNull(raw: String): JsonElement? =
    Catching { adminJson.parseToJsonElement(raw.trim()) }.getOrNull()

internal fun csvSet(raw: String): Set<String>? =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty { null }

internal fun randomUuid(): String = js("crypto.randomUUID()") as String

/**
 * A confirm prompt for values that hit every player at once. Returns the warning
 * to show, or null when the change is routine. Keyed on (path, value) because
 * the danger is value-specific: `maintenanceMode = "blocking"` is a lockout,
 * `"off"` is the all-clear.
 *
 * Three things earn a warning:
 *
 * 1. It blocks a player, or tells them to upgrade.
 * 2. It runs SPEC section 4.2's second constraint backwards — monetization keys
 *    fail *open*, and these are the values that make one fail closed.
 * 3. It changes what every player sees: a shipped feature disappears, or an ad
 *    format that ships off appears.
 *
 * Ordinary retuning earns nothing. Raising `paywall.sessionCap` or dropping
 * `ads.interstitialEveryNLevels` to 1 makes the app more aggressive, and SPEC
 * 4.2 says so in as many words: tightening in *config* is a live-ops decision
 * and is fine, it is tightening the shipped defaults that is forbidden. On prod
 * a warning also makes the operator type the environment name, so warning about
 * everything would only teach them to type it without reading it.
 *
 * The value arrives either JSON-encoded (`"blocking"`, `false`) or as the raw
 * text of a half-typed field, depending on the caller, so nothing here matches
 * on one spelling of it.
 */
internal fun dangerousWarning(path: String, value: String): String? =
    blocksPlayers(path, value)
        ?: defeatsFailOpen(path, value)
        ?: changesWhatEveryoneSees(path, value)

private fun blocksPlayers(path: String, value: String): String? = when {
    path == "upgrade.maintenanceMode" && value.asText() == "blocking" ->
        "This sets maintenance mode to BLOCKING — it locks ALL users out of the app. Continue?"
    path == "upgrade.maintenanceMode" && value.asText() == "banner" ->
        "This shows a maintenance banner to ALL users. Continue?"
    path == "upgrade.minSupportedVersionCode" ->
        "Raising the minimum supported version force-upgrades every user below it. Double-check the number. Continue?"
    path == "upgrade.softUpdateVersionCode" && (value.asInt() ?: 0) > 0 ->
        "Everyone below this build gets an update prompt on launch. It does not block them — " +
            "upgrade.minSupportedVersionCode is the one that does. Continue?"
    path == "legal.forceReacceptBelow" && (value.asInt() ?: 0) > 0 ->
        "This puts a BLOCKING re-acceptance sheet in front of everyone who accepted an older " +
            "version. Raise it only alongside legal.termsVersion. Continue?"
    else -> null
}

private fun defeatsFailOpen(path: String, value: String): String? = when {
    path == "ads.failureMode" && value.asText() == "LOCK" ->
        "LOCK is the harsher arm: a third strike locks the level until a rewarded ad reopens it. " +
            "A player with no connection stays locked. Continue?"
    path in OfflineGracePaths && value.asInt() == 0 ->
        "Zero offline grace blocks play at the first ad gate that can't be served, with no " +
            "warning to the player. Continue?"
    path == "ads.rewardedPlacements" && value.disablesARewardedPlacement() ->
        "Turning a rewarded placement off leaves its button on screen doing nothing — the reward " +
            "is something the player asked for and is owed. Continue?"
    else -> null
}

private fun changesWhatEveryoneSees(path: String, value: String): String? = when {
    path == "ads.enabled" && value.asBoolean() == false ->
        "This stops every rewarded ad in the app, which is every ad the app has, so there is no " +
            "ad revenue and no way to refill a booster until it is turned back on. Continue?"
    path == "daily.enabled" && value.asBoolean() == false ->
        "This removes the Daily Challenge for everyone. It runs off the bundled pool, so an " +
            "outage is never the reason to do this. Continue?"
    path.startsWith("features.") && value.asBoolean() == false ->
        "This hides a shipped feature from every player until it is turned back on. Continue?"
    else -> null
}

private val OfflineGracePaths = setOf("ads.offlineGraceLevels", "ads.offlineGraceMinutes")

/** The value as plain text, whether it arrived JSON-encoded or as a half-typed field. */
private fun String.asText(): String = (parseJsonOrNull(this) as? JsonPrimitive)?.contentOrNull ?: trim()

private fun String.asBoolean(): Boolean? = asText().toBooleanStrictOrNull()

private fun String.asInt(): Int? = asText().toIntOrNull()

/** True when the placement map turns any placement off. */
private fun String.disablesARewardedPlacement(): Boolean =
    (parseJsonOrNull(this) as? JsonObject)?.values
        ?.any { (it as? JsonPrimitive)?.booleanOrNull == false } == true

/** Compact, human-readable rendering of a JSON value for tables (no pretty-print). */
internal fun JsonElement?.inline(): String = this?.toString() ?: "—"

/**
 * A targeting rule as a plain-English clause: "app version > 1.0.1 and country
 * in US/CA". The value it sets is shown separately by the caller.
 */
internal fun conditionsSentence(c: RuleConditions): String {
    val parts = buildList {
        c.platforms?.let { add("platform in ${it.joinToString("/")}") }
        appVersionPhrase(c)?.let { add(it) }
        c.minVersionCode?.let { add("build ≥ $it") }
        c.maxVersionCode?.let { add("build ≤ $it") }
        c.countries?.let { add("country in ${it.joinToString("/")}") }
        c.locales?.let { add("locale in ${it.joinToString("/")}") }
        c.userAllow?.let { add("user in allowlist (${it.size})") }
        c.userDeny?.let { add("user not in denylist (${it.size})") }
        c.rolloutPercent?.let { add("rollout $it%") }
    }
    return if (parts.isEmpty()) "everyone" else parts.joinToString(" and ")
}

private fun appVersionPhrase(c: RuleConditions): String? {
    val min = c.minAppVersion?.takeUnless { it.isBlank() }
    val max = c.maxAppVersion?.takeUnless { it.isBlank() }
    val minOp = if (c.minAppVersionInclusive) "≥" else ">"
    val maxOp = if (c.maxAppVersionInclusive) "≤" else "<"
    return when {
        min != null && max != null -> "app version $minOp $min and $maxOp $max"
        min != null -> "app version $minOp $min"
        max != null -> "app version $maxOp $max"
        else -> null
    }
}

package com.sodogku.libraries.sodogku

/**
 * There is deliberately no `setUser` here, and `captureUserFeedback` takes no
 * email. Both existed, both compiled, and neither had a caller — a one-line
 * `telemetry.setUser(email, …)` is the natural thing to write the day someone
 * adds a contact field to the feedback form, and it would make two published
 * sentences false: `pages/privacy.html` says the app never gives Sentry a name,
 * an email address or a user id, and that the form has no email field. A seam
 * that only exists to be misused is worse than no seam. If contact details are
 * ever wanted, they go in the message body, which is what the policy already
 * describes.
 */
interface Telemetry {
    fun initialize()

    /**
     * Records the user's current navigation route on the crash-reporting
     * scope as both a searchable tag and a visible extra (`route`). Set it
     * eagerly on every navigation, NOT at event time.
     *
     * Why on the scope rather than in a `beforeSend` hook: the SDK persists
     * the scope to disk the moment it changes, and native crashes are turned
     * into events on the *next launch* using that persisted scope. So a route
     * written here is frozen at the instant the user was on it — a crash on
     * this route carries this route, even though it's transmitted later. A
     * `beforeSend` callback, by contrast, runs at next-launch for crashes and
     * would read a stale/empty route.
     *
     * The value sticks until the next navigation overwrites it, and any single
     * event may override it by setting its own `route` tag (a per-event local
     * scope wins over this global one). Best-effort: a no-op when crash
     * reporting is disabled.
     */
    fun setCurrentRoute(route: String)

    /**
     * Records the current app session's correlation id on the crash-
     * reporting scope as a searchable `session_id` tag. Set it whenever the
     * session rolls (cold boot, background-rollover) so every subsequent
     * event — including user feedback — carries the id. The same value is
     * sent to the backend via `X-Session-Id`, so one id pulls a session's
     * frontend events and backend traces/logs together. Best-effort: a
     * no-op when crash reporting is disabled.
     */
    fun setSession(sessionId: String)

    /**
     * Records the install id on the crash-reporting scope as an `install_id`
     * tag — stable across sessions, useful for "all of this tester's
     * sessions." Best-effort; no-op when crash reporting is disabled.
     */
    fun setInstallId(installId: String)

    /**
     * Records an app-specific context value as a searchable [key] tag on the
     * crash-reporting scope, or clears it when [value] is null/blank. Use for
     * domain state worth pivoting on at triage time (the active document id,
     * the current lobby code, an experiment bucket) — the value sticks until
     * overwritten or cleared, so set it on entry and clear it on exit. If the
     * same key exists on backend spans/logs, use identical naming so one
     * query string works across Sentry, Tempo, and Loki. Best-effort; no-op
     * when crash reporting is disabled.
     */
    fun setContext(key: String, value: String?)

    /**
     * [screenshots] are JPEG-compressed image bytes the reporter chose to attach
     * (already downscaled at capture). Each rides along on the carrier event
     * as its own image attachment, so a triager sees exactly what the reporter
     * saw. Empty by default.
     *
     * [includeLogs] attaches the in-memory session log tail. Default on: a
     * report without the logs around it usually costs a round trip to
     * reproduce, and the tail is redacted before it is ever buffered.
     *
     * Neither player-facing screen passes it, so the default is what actually
     * ships, and both screens say so in `feedback_log_notice`. Changing the
     * default changes what the app has told the player and what the privacy
     * policy claims; move all three together.
     */
    fun captureUserFeedback(
        message: String,
        kind: FeedbackKind,
        eventId: String?,
        errorCode: Int?,
        screenshots: List<ByteArray> = emptyList(),
        includeLogs: Boolean = true,
    )
}

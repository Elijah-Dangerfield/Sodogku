package com.sodogku.libraries.sodogku

/**
 * Who wrote a report and what it is for. Rides to Sentry as the `feedback_kind`
 * tag, which is the one thing the triage routine filters on.
 *
 * It is a tag rather than a message prefix because the carrier event's message
 * is also its issue title: titles are grouped, re-summarized by Sentry's own
 * AI, and edited by whoever next touches the copy. A tag is indexed, is queried
 * with `feedback_kind:owner_directive`, and is unaffected by grouping. Each
 * report is already fingerprinted into its own issue, and the tag rides every
 * event in it.
 */
enum class FeedbackKind(val tag: String, val carrierMessage: String) {
    /** A player said something nice or had an idea. Read, not actioned. */
    Feedback(tag = "feedback", carrierMessage = "User feedback"),

    /** A player reported something broken. Actioned after diagnosis. */
    BugReport(tag = "bug_report", carrierMessage = "Bug report"),

    /**
     * The owner, from a debug or TestFlight build, asking for a change. Treated
     * as an instruction rather than a data point: triage files it on the TODO
     * list without asking whether it is worth doing.
     */
    OwnerDirective(tag = "owner_directive", carrierMessage = "Owner directive"),
}

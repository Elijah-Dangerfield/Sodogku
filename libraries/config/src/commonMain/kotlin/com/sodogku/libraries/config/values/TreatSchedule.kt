package com.sodogku.libraries.config.values

import kotlinx.serialization.Serializable

/**
 * One stretch of the campaign and how often it pays a free Treat.
 *
 * [fromLevel] is inclusive, and a band runs until the next one starts. The list
 * these come in arrives from a server as a hand-editable JSON array, so nothing
 * here assumes it is sorted — see [paysTreatAt].
 */
@Serializable
data class TreatBand(val fromLevel: Int, val everyNLevels: Int)

/**
 * Whether clearing [level] for the first time pays a Treat.
 *
 * The campaign used to pay one every five levels all the way to 500 — a hundred
 * free Treats, arriving at the same rate on level 480 as on level 5. That is
 * backwards twice over. Early on a Treat is a teaching aid and a reason to keep
 * going, and the player has no stash, so grants should be *denser* than they
 * were. Late on the player has been earning them for hours, a Treat is worth
 * less because they already hold several, and a reward on a metronome stops
 * registering as a reward at all.
 *
 * ### Why bands rather than a decay formula
 *
 * A curve would be smoother and completely opaque. A player cannot look at
 * `floor(3 * 1.004^level)` and know when the next Treat lands, and neither can
 * whoever retunes the economy six months from now. Bands are legible in the
 * level pane — the chips visibly space out as you scroll — and legible in the
 * admin console, where the whole schedule is four short objects.
 *
 * Within a band the test is a plain multiple of [TreatBand.everyNLevels] rather
 * than a count from the band's start, so grants land on round numbers (12, 24,
 * 36) and stay where a player expects them across a boundary.
 *
 * Fails closed on nonsense — an empty schedule, a level below every band, a
 * non-positive rate. The alternative to "no free Treat" is a modulo by zero, and
 * a remote value that crashes the game on level completion is much worse than
 * one that quietly stops paying.
 */
fun List<TreatBand>.paysTreatAt(level: Int): Boolean {
    // The latest band that has started, not the first one that matches, because
    // the array may arrive in any order.
    val everyN = filter { it.fromLevel <= level }
        .maxByOrNull { it.fromLevel }
        ?.everyNLevels
        ?: return false
    return everyN > 0 && level % everyN == 0
}

/**
 * The shipped curve, chosen against the campaign's actual shape rather than
 * picked to look tidy.
 *
 * Over 500 levels it pays 34 Treats where the flat every-fifth rule paid 100.
 * The first twenty levels get *more* than before — six instead of four — and the
 * last three hundred get one roughly every twenty-five.
 *
 * The band edges sit near where the campaign's difficulty tiers change, so the
 * rate drops at the same points the boards get harder rather than at an
 * unrelated round number.
 */
val DefaultTreatBands: List<TreatBand> = listOf(
    TreatBand(fromLevel = 1, everyNLevels = 3),
    TreatBand(fromLevel = 21, everyNLevels = 6),
    TreatBand(fromLevel = 61, everyNLevels = 12),
    TreatBand(fromLevel = 151, everyNLevels = 25),
)

/**
 * The same bands as the plain nested collections the bundled fallback map holds.
 *
 * The fallback is a `Map<String, Any>` that the config pipeline converts to JSON
 * on the way to a deserializer, and that conversion only understands maps, lists
 * and scalars — a `TreatBand` in there would be stringified into a primitive and
 * silently fail to decode, leaving the app on this value's default forever. So
 * the fallback holds the plain shape, derived from the typed one rather than
 * written out a second time.
 *
 * The field names are still spelled twice, here and in [TreatBand]. That is the
 * one part no derivation can remove, and it is what
 * `BundledJsonConfigDecodesTest` exists to catch.
 */
fun List<TreatBand>.asFallbackConfig(): List<Map<String, Int>> =
    map { mapOf("fromLevel" to it.fromLevel, "everyNLevels" to it.everyNLevels) }

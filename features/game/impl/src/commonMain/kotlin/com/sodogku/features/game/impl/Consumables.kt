package com.sodogku.features.game.impl

/**
 * The three things a player spends.
 *
 * They deliberately share one shape — start at three, refill to three by
 * watching an ad, may be held above three from level rewards — because three
 * different economies would be three things to learn before the puzzle.
 */
enum class Consumable {
    /** A wrong guess costs one. Running out ends the attempt. */
    Bone,

    /** A hint: dims the board and shows where a dog cannot go. */
    Sniff,

    /** A free correct placement. */
    Treat,
}

/** How many of each the player holds, and whether its explainer has been seen. */
data class ConsumableState(
    val bones: Int = 0,
    val sniffs: Int = 0,
    val treats: Int = 0,
    val explained: Set<Consumable> = emptySet(),
) {
    operator fun get(consumable: Consumable): Int = when (consumable) {
        Consumable.Bone -> bones
        Consumable.Sniff -> sniffs
        Consumable.Treat -> treats
    }

    fun with(consumable: Consumable, count: Int): ConsumableState = when (consumable) {
        Consumable.Bone -> copy(bones = count)
        Consumable.Sniff -> copy(sniffs = count)
        Consumable.Treat -> copy(treats = count)
    }

    fun isExplained(consumable: Consumable): Boolean = consumable in explained
}

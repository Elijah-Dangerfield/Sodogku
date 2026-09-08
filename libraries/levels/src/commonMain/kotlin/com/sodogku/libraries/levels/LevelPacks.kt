package com.sodogku.libraries.levels

import com.sodogku.libraries.levels.generated.CAMPAIGN_LEVEL_LINES
import com.sodogku.libraries.levels.generated.DAILY_LEVEL_LINES

/**
 * The two shipped packs.
 *
 * Decoding is lazy and cached, so the cost is paid once on first access rather
 * than at app boot, and never at all for a player who only ever opens the
 * daily.
 *
 * [PACK_VERSION] is bumped whenever the generator is re-run with a different
 * seed or band layout. Progress is keyed on level id, so a regenerated pack
 * silently reassigns everyone's completed levels to different boards — the
 * version is what a future migration would key off.
 */
object LevelPacks {

    const val PACK_VERSION: Int = 1

    val campaign: LevelPack by lazy {
        LevelCodec.decodePack(PackKind.Campaign, PACK_VERSION, CAMPAIGN_LEVEL_LINES)
    }

    val daily: LevelPack by lazy {
        LevelCodec.decodePack(PackKind.Daily, PACK_VERSION, DAILY_LEVEL_LINES)
    }

    /**
     * The id of the last level in the campaign.
     *
     * Progress does not know how many levels ship, so `unlockedThrough` can run
     * past the end when someone clears the final level. Clamping belongs here,
     * once, rather than at every consumer — a forgotten clamp is a crash-free
     * wrong answer that only shows up for players who finish.
     */
    val lastCampaignLevelId: Int get() = campaign.levels.lastOrNull()?.id ?: 1

    /** [level] clamped to a level that actually exists in the campaign. */
    fun clampToCampaign(level: Int): Int = level.coerceIn(1, lastCampaignLevelId)

    /**
     * The daily board for a given local date, expressed as days since the Unix
     * epoch. Wraps when the pool runs out, which is two years away; a content
     * update ships a longer pool well before then.
     *
     * Local date on purpose. It needs no server, and a player who changes their
     * device clock to skip ahead has only cheated themselves.
     */
    fun dailyFor(epochDay: Long): LevelDefinition = daily.levels[dailyIndexFor(epochDay)]

    /**
     * Where [epochDay] lands in the daily pool.
     *
     * Exposed as well as [dailyFor] because the day's result records *which board
     * it was*, and that has to be the same wrap the board came from — recomputing
     * the modulo at the call site is how the two quietly disagree once
     * `daily.poolOffset` moves.
     */
    fun dailyIndexFor(epochDay: Long): Int {
        val pool = daily.levels.size
        return (((epochDay % pool) + pool) % pool).toInt()
    }
}

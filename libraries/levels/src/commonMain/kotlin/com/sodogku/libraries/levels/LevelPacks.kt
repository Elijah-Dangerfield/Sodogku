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
     * The daily board for a given local date, expressed as days since the Unix
     * epoch. Wraps when the pool runs out, which is two years away; a content
     * update ships a longer pool well before then.
     *
     * Local date on purpose. It needs no server, and a player who changes their
     * device clock to skip ahead has only cheated themselves.
     */
    fun dailyFor(epochDay: Long): LevelDefinition {
        val pool = daily.levels
        val index = ((epochDay % pool.size) + pool.size) % pool.size
        return pool[index.toInt()]
    }
}

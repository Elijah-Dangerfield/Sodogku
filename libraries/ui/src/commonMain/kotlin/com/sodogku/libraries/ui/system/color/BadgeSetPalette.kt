package com.sodogku.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.sodogku.libraries.achievements.AchievementGroup

/**
 * The colour one set of badges is drawn in, in three parts that always travel
 * together: a pale [disc] behind the glyph of a badge still being earned, a
 * dark [ink] for the set's label, and a saturated [fill] for its progress bar,
 * the dot on its section header, and the disc behind a badge once it is earned.
 *
 * Three rather than one because one colour cannot do all three jobs. The fill
 * is a bar colour and would fail as text on an ivory card; the ink is a text
 * colour and would make a leaden disc. The disc is the fill let nearly all the
 * way out to white, which is what keeps a locked badge looking like something
 * to want rather than something switched off.
 */
@Immutable
data class BadgeSetStyle(
    val disc: ColorResource,
    val ink: ColorResource,
    val fill: ColorResource,
)

/**
 * A [BadgeSetStyle] for every [AchievementGroup].
 *
 * Keyed by the group rather than by a design-system enum of its own, so the
 * catalog gaining a tenth shelf fails *this* build until the shelf has colours,
 * the way it already fails `AchievementCopy` until it has a name. A parallel
 * enum would have been a second list to keep in step and a runtime fallback
 * for the day they drifted.
 */
interface BadgeSetPalette {
    operator fun get(group: AchievementGroup): BadgeSetStyle
}

/**
 * The nine sets from the 2026-09 handoff, which named three and left six to
 * be drawn in the same key: a pale disc, a dark ink readable on it, a
 * saturated fill.
 *
 * Contrast was measured rather than eyeballed, and one of the handoff's own
 * values did not clear the bar. Ink on disc, WCAG ratio:
 *
 * | Set | Disc | Ink | Fill | Ink on disc |
 * |---|---|---|---|---|
 * | Campaign | `#dff0c9` | `#457326` | `#6aa832` | 4.67:1 |
 * | Clean play | `#d6e9fb` | `#23486e` | `#2f8ef4` | 7.60:1 |
 * | The hard way | `#ffd9e2` | `#a83a5c` | `#e0568a` | 4.75:1 |
 * | Speed | `#ffe2c7` | `#9c4a0f` | `#f4862a` | 4.99:1 |
 * | Score | `#ecdcf7` | `#5f2b82` | `#9c4fd1` | 7.49:1 |
 * | Paws | `#ffeab8` | `#7d4a12` | `#f5b81e` | 6.19:1 |
 * | Daily | `#d3f0ea` | `#1d6a5c` | `#2bb59b` | 5.33:1 |
 * | Dedication | `#dfe0fa` | `#3c3f8f` | `#6c6fe0` | 7.04:1 |
 * | Secrets | `#e9e2da` | `#5e5048` | `#9a8b80` | 6.02:1 |
 *
 * The campaign's ink is a step darker than the handoff's `#4a7a2a`, which sits
 * at 4.25:1 on its own disc. Every other named value is the handoff's.
 * `BadgeSetPaletteTest` holds the floor by computing it, not by reading this
 * table.
 *
 * Paws wear the bone's gold rather than the amber, so a paw set and an earned
 * ring stay two things. Secrets lean neutral on purpose: a hidden badge has no
 * colour to give away.
 */
val defaultBadgeSets: BadgeSetPalette = object : BadgeSetPalette {
    override fun get(group: AchievementGroup): BadgeSetStyle = when (group) {
        AchievementGroup.Campaign -> Campaign
        AchievementGroup.CleanPlay -> CleanPlay
        AchievementGroup.HardWay -> HardWay
        AchievementGroup.Speed -> Speed
        AchievementGroup.Score -> Score
        AchievementGroup.Paws -> Paws
        AchievementGroup.Daily -> Daily
        AchievementGroup.Dedication -> Dedication
        AchievementGroup.Secrets -> Secrets
    }
}

private val Campaign = set("campaign", disc = 0xFFDFF0C9, ink = 0xFF457326, fill = 0xFF6AA832)
private val CleanPlay = set("clean-play", disc = 0xFFD6E9FB, ink = 0xFF23486E, fill = 0xFF2F8EF4)
private val HardWay = set("hard-way", disc = 0xFFFFD9E2, ink = 0xFFA83A5C, fill = 0xFFE0568A)
private val Speed = set("speed", disc = 0xFFFFE2C7, ink = 0xFF9C4A0F, fill = 0xFFF4862A)
private val Score = set("score", disc = 0xFFECDCF7, ink = 0xFF5F2B82, fill = 0xFF9C4FD1)
private val Paws = set("paws", disc = 0xFFFFEAB8, ink = 0xFF7D4A12, fill = 0xFFF5B81E)
private val Daily = set("daily", disc = 0xFFD3F0EA, ink = 0xFF1D6A5C, fill = 0xFF2BB59B)
private val Dedication = set("dedication", disc = 0xFFDFE0FA, ink = 0xFF3C3F8F, fill = 0xFF6C6FE0)
private val Secrets = set("secrets", disc = 0xFFE9E2DA, ink = 0xFF5E5048, fill = 0xFF9A8B80)

/**
 * Named after the set rather than added to the colour ladder as twenty-seven
 * rungs: these are not steps of a ramp anything else should pick from, the way
 * the region palette's fills are not.
 */
private fun set(name: String, disc: Long, ink: Long, fill: Long) = BadgeSetStyle(
    disc = ColorResource.FromColor(Color(disc), "badge-$name-disc"),
    ink = ColorResource.FromColor(Color(ink), "badge-$name-ink"),
    fill = ColorResource.FromColor(Color(fill), "badge-$name-fill"),
)

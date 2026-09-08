package com.sodogku.features.achievements

import com.sodogku.libraries.achievements.AchievementId
import org.jetbrains.compose.resources.StringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievement_best_in_show_body
import sodogku.libraries.resources.generated.resources.achievement_best_in_show_name
import sodogku.libraries.resources.generated.resources.achievement_blitz_body
import sodogku.libraries.resources.generated.resources.achievement_blitz_name
import sodogku.libraries.resources.generated.resources.achievement_chain_of_eight_body
import sodogku.libraries.resources.generated.resources.achievement_chain_of_eight_name
import sodogku.libraries.resources.generated.resources.achievement_comeback_body
import sodogku.libraries.resources.generated.resources.achievement_comeback_name
import sodogku.libraries.resources.generated.resources.achievement_daily_devotion_body
import sodogku.libraries.resources.generated.resources.achievement_daily_devotion_name
import sodogku.libraries.resources.generated.resources.achievement_early_bird_body
import sodogku.libraries.resources.generated.resources.achievement_early_bird_name
import sodogku.libraries.resources.generated.resources.achievement_faithful_body
import sodogku.libraries.resources.generated.resources.achievement_faithful_name
import sodogku.libraries.resources.generated.resources.achievement_fetch_daily_body
import sodogku.libraries.resources.generated.resources.achievement_fetch_daily_name
import sodogku.libraries.resources.generated.resources.achievement_first_steps_body
import sodogku.libraries.resources.generated.resources.achievement_first_steps_name
import sodogku.libraries.resources.generated.resources.achievement_flawless_ten_body
import sodogku.libraries.resources.generated.resources.achievement_flawless_ten_name
import sodogku.libraries.resources.generated.resources.achievement_good_dog_body
import sodogku.libraries.resources.generated.resources.achievement_good_dog_name
import sodogku.libraries.resources.generated.resources.achievement_grid_seven_body
import sodogku.libraries.resources.generated.resources.achievement_grid_seven_name
import sodogku.libraries.resources.generated.resources.achievement_grid_ten_body
import sodogku.libraries.resources.generated.resources.achievement_grid_ten_name
import sodogku.libraries.resources.generated.resources.achievement_high_roller_body
import sodogku.libraries.resources.generated.resources.achievement_high_roller_name
import sodogku.libraries.resources.generated.resources.achievement_night_owl_body
import sodogku.libraries.resources.generated.resources.achievement_night_owl_name
import sodogku.libraries.resources.generated.resources.achievement_no_help_needed_body
import sodogku.libraries.resources.generated.resources.achievement_no_help_needed_name
import sodogku.libraries.resources.generated.resources.achievement_pedigree_body
import sodogku.libraries.resources.generated.resources.achievement_pedigree_name
import sodogku.libraries.resources.generated.resources.achievement_perfect_form_body
import sodogku.libraries.resources.generated.resources.achievement_perfect_form_name
import sodogku.libraries.resources.generated.resources.achievement_show_dog_body
import sodogku.libraries.resources.generated.resources.achievement_show_dog_name
import sodogku.libraries.resources.generated.resources.achievement_speed_demon_body
import sodogku.libraries.resources.generated.resources.achievement_speed_demon_name
import sodogku.libraries.resources.generated.resources.achievement_top_dog_body
import sodogku.libraries.resources.generated.resources.achievement_top_dog_name

/**
 * The words and the glyph for every badge.
 *
 * This file is the other half of the deal `AchievementId` makes: the catalog
 * carries stable ids and no display copy, and adding an entry to it fails
 * *here*, at compile time, until somebody writes the words. The `when`s below
 * have no `else` for exactly that reason — an `else` would turn a missing
 * translation back into a runtime problem, which is the failure mode the split
 * exists to prevent.
 *
 * It lives in the feature's **api** module rather than its impl because
 * `:features:game:impl` needs a badge's name for the unlock toast, and a
 * feature impl may only depend on another feature's api.
 */
object AchievementCopy {

    fun name(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstSteps -> Res.string.achievement_first_steps_name
        AchievementId.GoodDog -> Res.string.achievement_good_dog_name
        AchievementId.BestInShow -> Res.string.achievement_best_in_show_name
        AchievementId.TopDog -> Res.string.achievement_top_dog_name
        AchievementId.PerfectForm -> Res.string.achievement_perfect_form_name
        AchievementId.FlawlessTen -> Res.string.achievement_flawless_ten_name
        AchievementId.Comeback -> Res.string.achievement_comeback_name
        AchievementId.NoHelpNeeded -> Res.string.achievement_no_help_needed_name
        AchievementId.SpeedDemon -> Res.string.achievement_speed_demon_name
        AchievementId.Blitz -> Res.string.achievement_blitz_name
        AchievementId.HighRoller -> Res.string.achievement_high_roller_name
        AchievementId.ChainOfEight -> Res.string.achievement_chain_of_eight_name
        AchievementId.ShowDog -> Res.string.achievement_show_dog_name
        AchievementId.Pedigree -> Res.string.achievement_pedigree_name
        AchievementId.GridSeven -> Res.string.achievement_grid_seven_name
        AchievementId.GridTen -> Res.string.achievement_grid_ten_name
        AchievementId.FetchDaily -> Res.string.achievement_fetch_daily_name
        AchievementId.DailyDevotion -> Res.string.achievement_daily_devotion_name
        AchievementId.Faithful -> Res.string.achievement_faithful_name
        AchievementId.NightOwl -> Res.string.achievement_night_owl_name
        AchievementId.EarlyBird -> Res.string.achievement_early_bird_name
    }

    fun description(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstSteps -> Res.string.achievement_first_steps_body
        AchievementId.GoodDog -> Res.string.achievement_good_dog_body
        AchievementId.BestInShow -> Res.string.achievement_best_in_show_body
        AchievementId.TopDog -> Res.string.achievement_top_dog_body
        AchievementId.PerfectForm -> Res.string.achievement_perfect_form_body
        AchievementId.FlawlessTen -> Res.string.achievement_flawless_ten_body
        AchievementId.Comeback -> Res.string.achievement_comeback_body
        AchievementId.NoHelpNeeded -> Res.string.achievement_no_help_needed_body
        AchievementId.SpeedDemon -> Res.string.achievement_speed_demon_body
        AchievementId.Blitz -> Res.string.achievement_blitz_body
        AchievementId.HighRoller -> Res.string.achievement_high_roller_body
        AchievementId.ChainOfEight -> Res.string.achievement_chain_of_eight_body
        AchievementId.ShowDog -> Res.string.achievement_show_dog_body
        AchievementId.Pedigree -> Res.string.achievement_pedigree_body
        AchievementId.GridSeven -> Res.string.achievement_grid_seven_body
        AchievementId.GridTen -> Res.string.achievement_grid_ten_body
        AchievementId.FetchDaily -> Res.string.achievement_fetch_daily_body
        AchievementId.DailyDevotion -> Res.string.achievement_daily_devotion_body
        AchievementId.Faithful -> Res.string.achievement_faithful_body
        AchievementId.NightOwl -> Res.string.achievement_night_owl_body
        AchievementId.EarlyBird -> Res.string.achievement_early_bird_body
    }

    /**
     * The badge's face. A glyph rather than a string resource, because there is
     * nothing here to translate and 21 emoji in `strings.xml` would be 21 rows
     * of noise for a translator to skip past. Placeholder art until the real
     * badge illustrations land.
     */
    fun glyph(id: AchievementId): String = when (id) {
        AchievementId.FirstSteps -> "🐾"
        AchievementId.GoodDog -> "🦴"
        AchievementId.BestInShow -> "🏅"
        AchievementId.TopDog -> "👑"
        AchievementId.PerfectForm -> "✨"
        AchievementId.FlawlessTen -> "💎"
        AchievementId.Comeback -> "💪"
        AchievementId.NoHelpNeeded -> "🧠"
        AchievementId.SpeedDemon -> "⚡"
        AchievementId.Blitz -> "🚀"
        AchievementId.HighRoller -> "🎰"
        AchievementId.ChainOfEight -> "⛓️"
        AchievementId.ShowDog -> "🎀"
        AchievementId.Pedigree -> "🏆"
        AchievementId.GridSeven -> "🔷"
        AchievementId.GridTen -> "🔶"
        AchievementId.FetchDaily -> "📅"
        AchievementId.DailyDevotion -> "🔥"
        AchievementId.Faithful -> "🌟"
        AchievementId.NightOwl -> "🦉"
        AchievementId.EarlyBird -> "🌅"
    }
}

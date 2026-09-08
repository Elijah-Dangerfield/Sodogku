package com.sodogku.features.achievements

import com.sodogku.libraries.achievements.AchievementGroup
import com.sodogku.libraries.achievements.AchievementId
import org.jetbrains.compose.resources.StringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.achievement_all_your_own_work_body
import sodogku.libraries.resources.generated.resources.achievement_all_your_own_work_name
import sodogku.libraries.resources.generated.resources.achievement_best_in_show_body
import sodogku.libraries.resources.generated.resources.achievement_best_in_show_name
import sodogku.libraries.resources.generated.resources.achievement_big_league_body
import sodogku.libraries.resources.generated.resources.achievement_big_league_name
import sodogku.libraries.resources.generated.resources.achievement_blink_and_miss_it_body
import sodogku.libraries.resources.generated.resources.achievement_blink_and_miss_it_name
import sodogku.libraries.resources.generated.resources.achievement_blitz_body
import sodogku.libraries.resources.generated.resources.achievement_blitz_name
import sodogku.libraries.resources.generated.resources.achievement_blue_ribbon_body
import sodogku.libraries.resources.generated.resources.achievement_blue_ribbon_name
import sodogku.libraries.resources.generated.resources.achievement_blur_of_fur_body
import sodogku.libraries.resources.generated.resources.achievement_blur_of_fur_name
import sodogku.libraries.resources.generated.resources.achievement_chain_of_eight_body
import sodogku.libraries.resources.generated.resources.achievement_chain_of_eight_name
import sodogku.libraries.resources.generated.resources.achievement_chain_of_five_body
import sodogku.libraries.resources.generated.resources.achievement_chain_of_five_name
import sodogku.libraries.resources.generated.resources.achievement_clean_sheet_body
import sodogku.libraries.resources.generated.resources.achievement_clean_sheet_name
import sodogku.libraries.resources.generated.resources.achievement_comeback_body
import sodogku.libraries.resources.generated.resources.achievement_comeback_name
import sodogku.libraries.resources.generated.resources.achievement_creature_of_habit_body
import sodogku.libraries.resources.generated.resources.achievement_creature_of_habit_name
import sodogku.libraries.resources.generated.resources.achievement_daily_devotion_body
import sodogku.libraries.resources.generated.resources.achievement_daily_devotion_name
import sodogku.libraries.resources.generated.resources.achievement_early_bird_body
import sodogku.libraries.resources.generated.resources.achievement_early_bird_name
import sodogku.libraries.resources.generated.resources.achievement_faithful_body
import sodogku.libraries.resources.generated.resources.achievement_faithful_name
import sodogku.libraries.resources.generated.resources.achievement_fetch_daily_body
import sodogku.libraries.resources.generated.resources.achievement_fetch_daily_name
import sodogku.libraries.resources.generated.resources.achievement_fifty_hours_in_body
import sodogku.libraries.resources.generated.resources.achievement_fifty_hours_in_name
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
import sodogku.libraries.resources.generated.resources.achievement_grudge_match_body
import sodogku.libraries.resources.generated.resources.achievement_grudge_match_name
import sodogku.libraries.resources.generated.resources.achievement_hall_of_fame_body
import sodogku.libraries.resources.generated.resources.achievement_hall_of_fame_name
import sodogku.libraries.resources.generated.resources.achievement_hat_trick_body
import sodogku.libraries.resources.generated.resources.achievement_hat_trick_name
import sodogku.libraries.resources.generated.resources.achievement_heavy_lifting_body
import sodogku.libraries.resources.generated.resources.achievement_heavy_lifting_name
import sodogku.libraries.resources.generated.resources.achievement_high_roller_body
import sodogku.libraries.resources.generated.resources.achievement_high_roller_name
import sodogku.libraries.resources.generated.resources.achievement_hour_with_the_dogs_body
import sodogku.libraries.resources.generated.resources.achievement_hour_with_the_dogs_name
import sodogku.libraries.resources.generated.resources.achievement_hundred_days_body
import sodogku.libraries.resources.generated.resources.achievement_hundred_days_name
import sodogku.libraries.resources.generated.resources.achievement_in_the_zone_body
import sodogku.libraries.resources.generated.resources.achievement_in_the_zone_name
import sodogku.libraries.resources.generated.resources.achievement_iron_nose_body
import sodogku.libraries.resources.generated.resources.achievement_iron_nose_name
import sodogku.libraries.resources.generated.resources.achievement_jackpot_body
import sodogku.libraries.resources.generated.resources.achievement_jackpot_name
import sodogku.libraries.resources.generated.resources.achievement_long_way_round_body
import sodogku.libraries.resources.generated.resources.achievement_long_way_round_name
import sodogku.libraries.resources.generated.resources.achievement_master_of_the_grid_body
import sodogku.libraries.resources.generated.resources.achievement_master_of_the_grid_name
import sodogku.libraries.resources.generated.resources.achievement_midnight_shift_body
import sodogku.libraries.resources.generated.resources.achievement_midnight_shift_name
import sodogku.libraries.resources.generated.resources.achievement_nerves_of_steel_body
import sodogku.libraries.resources.generated.resources.achievement_nerves_of_steel_name
import sodogku.libraries.resources.generated.resources.achievement_never_say_die_body
import sodogku.libraries.resources.generated.resources.achievement_never_say_die_name
import sodogku.libraries.resources.generated.resources.achievement_night_owl_body
import sodogku.libraries.resources.generated.resources.achievement_night_owl_name
import sodogku.libraries.resources.generated.resources.achievement_no_bones_about_it_body
import sodogku.libraries.resources.generated.resources.achievement_no_bones_about_it_name
import sodogku.libraries.resources.generated.resources.achievement_no_help_needed_body
import sodogku.libraries.resources.generated.resources.achievement_no_help_needed_name
import sodogku.libraries.resources.generated.resources.achievement_no_shame_in_it_body
import sodogku.libraries.resources.generated.resources.achievement_no_shame_in_it_name
import sodogku.libraries.resources.generated.resources.achievement_off_the_leash_body
import sodogku.libraries.resources.generated.resources.achievement_off_the_leash_name
import sodogku.libraries.resources.generated.resources.achievement_on_a_roll_body
import sodogku.libraries.resources.generated.resources.achievement_on_a_roll_name
import sodogku.libraries.resources.generated.resources.achievement_part_of_the_routine_body
import sodogku.libraries.resources.generated.resources.achievement_part_of_the_routine_name
import sodogku.libraries.resources.generated.resources.achievement_pedigree_body
import sodogku.libraries.resources.generated.resources.achievement_pedigree_name
import sodogku.libraries.resources.generated.resources.achievement_perfect_form_body
import sodogku.libraries.resources.generated.resources.achievement_perfect_form_name
import sodogku.libraries.resources.generated.resources.achievement_perfect_ten_body
import sodogku.libraries.resources.generated.resources.achievement_perfect_ten_name
import sodogku.libraries.resources.generated.resources.achievement_quick_paws_body
import sodogku.libraries.resources.generated.resources.achievement_quick_paws_name
import sodogku.libraries.resources.generated.resources.achievement_regular_as_clockwork_body
import sodogku.libraries.resources.generated.resources.achievement_regular_as_clockwork_name
import sodogku.libraries.resources.generated.resources.achievement_rocket_recall_body
import sodogku.libraries.resources.generated.resources.achievement_rocket_recall_name
import sodogku.libraries.resources.generated.resources.achievement_seasoned_snout_body
import sodogku.libraries.resources.generated.resources.achievement_seasoned_snout_name
import sodogku.libraries.resources.generated.resources.achievement_second_wind_body
import sodogku.libraries.resources.generated.resources.achievement_second_wind_name
import sodogku.libraries.resources.generated.resources.achievement_self_taught_body
import sodogku.libraries.resources.generated.resources.achievement_self_taught_name
import sodogku.libraries.resources.generated.resources.achievement_show_dog_body
import sodogku.libraries.resources.generated.resources.achievement_show_dog_name
import sodogku.libraries.resources.generated.resources.achievement_showstopper_body
import sodogku.libraries.resources.generated.resources.achievement_showstopper_name
import sodogku.libraries.resources.generated.resources.achievement_solo_run_body
import sodogku.libraries.resources.generated.resources.achievement_solo_run_name
import sodogku.libraries.resources.generated.resources.achievement_speed_demon_body
import sodogku.libraries.resources.generated.resources.achievement_speed_demon_name
import sodogku.libraries.resources.generated.resources.achievement_spotless_body
import sodogku.libraries.resources.generated.resources.achievement_spotless_daily_body
import sodogku.libraries.resources.generated.resources.achievement_spotless_daily_name
import sodogku.libraries.resources.generated.resources.achievement_spotless_name
import sodogku.libraries.resources.generated.resources.achievement_squeaky_clean_body
import sodogku.libraries.resources.generated.resources.achievement_squeaky_clean_name
import sodogku.libraries.resources.generated.resources.achievement_sunrise_ritual_body
import sodogku.libraries.resources.generated.resources.achievement_sunrise_ritual_name
import sodogku.libraries.resources.generated.resources.achievement_ten_by_ten_club_body
import sodogku.libraries.resources.generated.resources.achievement_ten_by_ten_club_name
import sodogku.libraries.resources.generated.resources.achievement_ten_hours_deep_body
import sodogku.libraries.resources.generated.resources.achievement_ten_hours_deep_name
import sodogku.libraries.resources.generated.resources.achievement_ten_in_five_body
import sodogku.libraries.resources.generated.resources.achievement_ten_in_five_name
import sodogku.libraries.resources.generated.resources.achievement_ten_second_dog_body
import sodogku.libraries.resources.generated.resources.achievement_ten_second_dog_name
import sodogku.libraries.resources.generated.resources.achievement_three_in_a_row_body
import sodogku.libraries.resources.generated.resources.achievement_three_in_a_row_name
import sodogku.libraries.resources.generated.resources.achievement_three_paws_up_body
import sodogku.libraries.resources.generated.resources.achievement_three_paws_up_name
import sodogku.libraries.resources.generated.resources.achievement_top_dog_body
import sodogku.libraries.resources.generated.resources.achievement_top_dog_name
import sodogku.libraries.resources.generated.resources.achievement_tough_cookie_body
import sodogku.libraries.resources.generated.resources.achievement_tough_cookie_name
import sodogku.libraries.resources.generated.resources.achievement_treat_money_body
import sodogku.libraries.resources.generated.resources.achievement_treat_money_name
import sodogku.libraries.resources.generated.resources.achievement_unbroken_body
import sodogku.libraries.resources.generated.resources.achievement_unbroken_name
import sodogku.libraries.resources.generated.resources.achievement_untouchable_body
import sodogku.libraries.resources.generated.resources.achievement_untouchable_name
import sodogku.libraries.resources.generated.resources.achievement_well_trained_body
import sodogku.libraries.resources.generated.resources.achievement_well_trained_name
import sodogku.libraries.resources.generated.resources.achievements_group_campaign
import sodogku.libraries.resources.generated.resources.achievements_group_clean_play
import sodogku.libraries.resources.generated.resources.achievements_group_daily
import sodogku.libraries.resources.generated.resources.achievements_group_dedication
import sodogku.libraries.resources.generated.resources.achievements_group_hard_way
import sodogku.libraries.resources.generated.resources.achievements_group_paws
import sodogku.libraries.resources.generated.resources.achievements_group_score
import sodogku.libraries.resources.generated.resources.achievements_group_secrets
import sodogku.libraries.resources.generated.resources.achievements_group_speed

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
@Suppress("LargeClass")
object AchievementCopy {

    fun name(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstSteps -> Res.string.achievement_first_steps_name
        AchievementId.GoodDog -> Res.string.achievement_good_dog_name
        AchievementId.OffTheLeash -> Res.string.achievement_off_the_leash_name
        AchievementId.WellTrained -> Res.string.achievement_well_trained_name
        AchievementId.BestInShow -> Res.string.achievement_best_in_show_name
        AchievementId.SeasonedSnout -> Res.string.achievement_seasoned_snout_name
        AchievementId.TopDog -> Res.string.achievement_top_dog_name
        AchievementId.GridSeven -> Res.string.achievement_grid_seven_name
        AchievementId.GridTen -> Res.string.achievement_grid_ten_name
        AchievementId.BigLeague -> Res.string.achievement_big_league_name
        AchievementId.HeavyLifting -> Res.string.achievement_heavy_lifting_name
        AchievementId.TenByTenClub -> Res.string.achievement_ten_by_ten_club_name
        AchievementId.PerfectForm -> Res.string.achievement_perfect_form_name
        AchievementId.Spotless -> Res.string.achievement_spotless_name
        AchievementId.SqueakyClean -> Res.string.achievement_squeaky_clean_name
        AchievementId.HatTrick -> Res.string.achievement_hat_trick_name
        AchievementId.FlawlessTen -> Res.string.achievement_flawless_ten_name
        AchievementId.Untouchable -> Res.string.achievement_untouchable_name
        AchievementId.CleanSheet -> Res.string.achievement_clean_sheet_name
        AchievementId.IronNose -> Res.string.achievement_iron_nose_name
        AchievementId.Comeback -> Res.string.achievement_comeback_name
        AchievementId.NeverSayDie -> Res.string.achievement_never_say_die_name
        AchievementId.ToughCookie -> Res.string.achievement_tough_cookie_name
        AchievementId.NervesOfSteel -> Res.string.achievement_nerves_of_steel_name
        AchievementId.NoHelpNeeded -> Res.string.achievement_no_help_needed_name
        AchievementId.SelfTaught -> Res.string.achievement_self_taught_name
        AchievementId.AllYourOwnWork -> Res.string.achievement_all_your_own_work_name
        AchievementId.SoloRun -> Res.string.achievement_solo_run_name
        AchievementId.LongWayRound -> Res.string.achievement_long_way_round_name
        AchievementId.SpeedDemon -> Res.string.achievement_speed_demon_name
        AchievementId.QuickPaws -> Res.string.achievement_quick_paws_name
        AchievementId.BlurOfFur -> Res.string.achievement_blur_of_fur_name
        AchievementId.Blitz -> Res.string.achievement_blitz_name
        AchievementId.RocketRecall -> Res.string.achievement_rocket_recall_name
        AchievementId.TenInFive -> Res.string.achievement_ten_in_five_name
        AchievementId.TreatMoney -> Res.string.achievement_treat_money_name
        AchievementId.HighRoller -> Res.string.achievement_high_roller_name
        AchievementId.Jackpot -> Res.string.achievement_jackpot_name
        AchievementId.ChainOfFive -> Res.string.achievement_chain_of_five_name
        AchievementId.ChainOfEight -> Res.string.achievement_chain_of_eight_name
        AchievementId.Unbroken -> Res.string.achievement_unbroken_name
        AchievementId.ThreePawsUp -> Res.string.achievement_three_paws_up_name
        AchievementId.ShowDog -> Res.string.achievement_show_dog_name
        AchievementId.Pedigree -> Res.string.achievement_pedigree_name
        AchievementId.BlueRibbon -> Res.string.achievement_blue_ribbon_name
        AchievementId.HallOfFame -> Res.string.achievement_hall_of_fame_name
        AchievementId.OnARoll -> Res.string.achievement_on_a_roll_name
        AchievementId.InTheZone -> Res.string.achievement_in_the_zone_name
        AchievementId.PerfectTen -> Res.string.achievement_perfect_ten_name
        AchievementId.MasterOfTheGrid -> Res.string.achievement_master_of_the_grid_name
        AchievementId.FetchDaily -> Res.string.achievement_fetch_daily_name
        AchievementId.CreatureOfHabit -> Res.string.achievement_creature_of_habit_name
        AchievementId.PartOfTheRoutine -> Res.string.achievement_part_of_the_routine_name
        AchievementId.RegularAsClockwork -> Res.string.achievement_regular_as_clockwork_name
        AchievementId.ThreeInARow -> Res.string.achievement_three_in_a_row_name
        AchievementId.DailyDevotion -> Res.string.achievement_daily_devotion_name
        AchievementId.Faithful -> Res.string.achievement_faithful_name
        AchievementId.HundredDays -> Res.string.achievement_hundred_days_name
        AchievementId.SpotlessDaily -> Res.string.achievement_spotless_daily_name
        AchievementId.NoBonesAboutIt -> Res.string.achievement_no_bones_about_it_name
        AchievementId.Showstopper -> Res.string.achievement_showstopper_name
        AchievementId.HourWithTheDogs -> Res.string.achievement_hour_with_the_dogs_name
        AchievementId.TenHoursDeep -> Res.string.achievement_ten_hours_deep_name
        AchievementId.FiftyHoursIn -> Res.string.achievement_fifty_hours_in_name
        AchievementId.SecondWind -> Res.string.achievement_second_wind_name
        AchievementId.GrudgeMatch -> Res.string.achievement_grudge_match_name
        AchievementId.NoShameInIt -> Res.string.achievement_no_shame_in_it_name
        AchievementId.TenSecondDog -> Res.string.achievement_ten_second_dog_name
        AchievementId.BlinkAndMissIt -> Res.string.achievement_blink_and_miss_it_name
        AchievementId.NightOwl -> Res.string.achievement_night_owl_name
        AchievementId.MidnightShift -> Res.string.achievement_midnight_shift_name
        AchievementId.EarlyBird -> Res.string.achievement_early_bird_name
        AchievementId.SunriseRitual -> Res.string.achievement_sunrise_ritual_name
    }

    fun description(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstSteps -> Res.string.achievement_first_steps_body
        AchievementId.GoodDog -> Res.string.achievement_good_dog_body
        AchievementId.OffTheLeash -> Res.string.achievement_off_the_leash_body
        AchievementId.WellTrained -> Res.string.achievement_well_trained_body
        AchievementId.BestInShow -> Res.string.achievement_best_in_show_body
        AchievementId.SeasonedSnout -> Res.string.achievement_seasoned_snout_body
        AchievementId.TopDog -> Res.string.achievement_top_dog_body
        AchievementId.GridSeven -> Res.string.achievement_grid_seven_body
        AchievementId.GridTen -> Res.string.achievement_grid_ten_body
        AchievementId.BigLeague -> Res.string.achievement_big_league_body
        AchievementId.HeavyLifting -> Res.string.achievement_heavy_lifting_body
        AchievementId.TenByTenClub -> Res.string.achievement_ten_by_ten_club_body
        AchievementId.PerfectForm -> Res.string.achievement_perfect_form_body
        AchievementId.Spotless -> Res.string.achievement_spotless_body
        AchievementId.SqueakyClean -> Res.string.achievement_squeaky_clean_body
        AchievementId.HatTrick -> Res.string.achievement_hat_trick_body
        AchievementId.FlawlessTen -> Res.string.achievement_flawless_ten_body
        AchievementId.Untouchable -> Res.string.achievement_untouchable_body
        AchievementId.CleanSheet -> Res.string.achievement_clean_sheet_body
        AchievementId.IronNose -> Res.string.achievement_iron_nose_body
        AchievementId.Comeback -> Res.string.achievement_comeback_body
        AchievementId.NeverSayDie -> Res.string.achievement_never_say_die_body
        AchievementId.ToughCookie -> Res.string.achievement_tough_cookie_body
        AchievementId.NervesOfSteel -> Res.string.achievement_nerves_of_steel_body
        AchievementId.NoHelpNeeded -> Res.string.achievement_no_help_needed_body
        AchievementId.SelfTaught -> Res.string.achievement_self_taught_body
        AchievementId.AllYourOwnWork -> Res.string.achievement_all_your_own_work_body
        AchievementId.SoloRun -> Res.string.achievement_solo_run_body
        AchievementId.LongWayRound -> Res.string.achievement_long_way_round_body
        AchievementId.SpeedDemon -> Res.string.achievement_speed_demon_body
        AchievementId.QuickPaws -> Res.string.achievement_quick_paws_body
        AchievementId.BlurOfFur -> Res.string.achievement_blur_of_fur_body
        AchievementId.Blitz -> Res.string.achievement_blitz_body
        AchievementId.RocketRecall -> Res.string.achievement_rocket_recall_body
        AchievementId.TenInFive -> Res.string.achievement_ten_in_five_body
        AchievementId.TreatMoney -> Res.string.achievement_treat_money_body
        AchievementId.HighRoller -> Res.string.achievement_high_roller_body
        AchievementId.Jackpot -> Res.string.achievement_jackpot_body
        AchievementId.ChainOfFive -> Res.string.achievement_chain_of_five_body
        AchievementId.ChainOfEight -> Res.string.achievement_chain_of_eight_body
        AchievementId.Unbroken -> Res.string.achievement_unbroken_body
        AchievementId.ThreePawsUp -> Res.string.achievement_three_paws_up_body
        AchievementId.ShowDog -> Res.string.achievement_show_dog_body
        AchievementId.Pedigree -> Res.string.achievement_pedigree_body
        AchievementId.BlueRibbon -> Res.string.achievement_blue_ribbon_body
        AchievementId.HallOfFame -> Res.string.achievement_hall_of_fame_body
        AchievementId.OnARoll -> Res.string.achievement_on_a_roll_body
        AchievementId.InTheZone -> Res.string.achievement_in_the_zone_body
        AchievementId.PerfectTen -> Res.string.achievement_perfect_ten_body
        AchievementId.MasterOfTheGrid -> Res.string.achievement_master_of_the_grid_body
        AchievementId.FetchDaily -> Res.string.achievement_fetch_daily_body
        AchievementId.CreatureOfHabit -> Res.string.achievement_creature_of_habit_body
        AchievementId.PartOfTheRoutine -> Res.string.achievement_part_of_the_routine_body
        AchievementId.RegularAsClockwork -> Res.string.achievement_regular_as_clockwork_body
        AchievementId.ThreeInARow -> Res.string.achievement_three_in_a_row_body
        AchievementId.DailyDevotion -> Res.string.achievement_daily_devotion_body
        AchievementId.Faithful -> Res.string.achievement_faithful_body
        AchievementId.HundredDays -> Res.string.achievement_hundred_days_body
        AchievementId.SpotlessDaily -> Res.string.achievement_spotless_daily_body
        AchievementId.NoBonesAboutIt -> Res.string.achievement_no_bones_about_it_body
        AchievementId.Showstopper -> Res.string.achievement_showstopper_body
        AchievementId.HourWithTheDogs -> Res.string.achievement_hour_with_the_dogs_body
        AchievementId.TenHoursDeep -> Res.string.achievement_ten_hours_deep_body
        AchievementId.FiftyHoursIn -> Res.string.achievement_fifty_hours_in_body
        AchievementId.SecondWind -> Res.string.achievement_second_wind_body
        AchievementId.GrudgeMatch -> Res.string.achievement_grudge_match_body
        AchievementId.NoShameInIt -> Res.string.achievement_no_shame_in_it_body
        AchievementId.TenSecondDog -> Res.string.achievement_ten_second_dog_body
        AchievementId.BlinkAndMissIt -> Res.string.achievement_blink_and_miss_it_body
        AchievementId.NightOwl -> Res.string.achievement_night_owl_body
        AchievementId.MidnightShift -> Res.string.achievement_midnight_shift_body
        AchievementId.EarlyBird -> Res.string.achievement_early_bird_body
        AchievementId.SunriseRitual -> Res.string.achievement_sunrise_ritual_body
    }

    /** The heading over one shelf of the grid. */
    fun groupName(group: AchievementGroup): StringResource = when (group) {
        AchievementGroup.Campaign -> Res.string.achievements_group_campaign
        AchievementGroup.CleanPlay -> Res.string.achievements_group_clean_play
        AchievementGroup.HardWay -> Res.string.achievements_group_hard_way
        AchievementGroup.Speed -> Res.string.achievements_group_speed
        AchievementGroup.Score -> Res.string.achievements_group_score
        AchievementGroup.Paws -> Res.string.achievements_group_paws
        AchievementGroup.Daily -> Res.string.achievements_group_daily
        AchievementGroup.Dedication -> Res.string.achievements_group_dedication
        AchievementGroup.Secrets -> Res.string.achievements_group_secrets
    }

    /**
     * The badge's face. A glyph rather than a string resource, because there is
     * nothing here to translate and seventy-three emoji in `strings.xml` would
     * be seventy-three rows of noise for a translator to skip past. Placeholder
     * art until the real badge illustrations land.
     */
    @Suppress("CyclomaticComplexMethod")
    fun glyph(id: AchievementId): String = when (id) {
        AchievementId.FirstSteps -> "🐾"
        AchievementId.GoodDog -> "🦴"
        AchievementId.OffTheLeash -> "🌳"
        AchievementId.WellTrained -> "🎓"
        AchievementId.BestInShow -> "🏅"
        AchievementId.SeasonedSnout -> "🧭"
        AchievementId.TopDog -> "👑"
        AchievementId.GridSeven -> "🔷"
        AchievementId.GridTen -> "🔶"
        AchievementId.BigLeague -> "🏟️"
        AchievementId.HeavyLifting -> "🏋️"
        AchievementId.TenByTenClub -> "🔟"
        AchievementId.PerfectForm -> "✨"
        AchievementId.Spotless -> "🧼"
        AchievementId.SqueakyClean -> "🛁"
        AchievementId.HatTrick -> "🎩"
        AchievementId.FlawlessTen -> "💎"
        AchievementId.Untouchable -> "🛡️"
        AchievementId.CleanSheet -> "📋"
        AchievementId.IronNose -> "🧲"
        AchievementId.Comeback -> "💪"
        AchievementId.NeverSayDie -> "🔁"
        AchievementId.ToughCookie -> "🍪"
        AchievementId.NervesOfSteel -> "😤"
        AchievementId.NoHelpNeeded -> "🧠"
        AchievementId.SelfTaught -> "📚"
        AchievementId.AllYourOwnWork -> "✍️"
        AchievementId.SoloRun -> "🏃"
        AchievementId.LongWayRound -> "🥾"
        AchievementId.SpeedDemon -> "⚡"
        AchievementId.QuickPaws -> "💨"
        AchievementId.BlurOfFur -> "🌪️"
        AchievementId.Blitz -> "🚀"
        AchievementId.RocketRecall -> "🛰️"
        AchievementId.TenInFive -> "⏲️"
        AchievementId.TreatMoney -> "🪙"
        AchievementId.HighRoller -> "🎰"
        AchievementId.Jackpot -> "💰"
        AchievementId.ChainOfFive -> "🔗"
        AchievementId.ChainOfEight -> "⛓️"
        AchievementId.Unbroken -> "➰"
        AchievementId.ThreePawsUp -> "🙌"
        AchievementId.ShowDog -> "🎀"
        AchievementId.Pedigree -> "🏆"
        AchievementId.BlueRibbon -> "🎖️"
        AchievementId.HallOfFame -> "🏛️"
        AchievementId.OnARoll -> "📈"
        AchievementId.InTheZone -> "🌊"
        AchievementId.PerfectTen -> "🥇"
        AchievementId.MasterOfTheGrid -> "🧩"
        AchievementId.FetchDaily -> "📅"
        AchievementId.CreatureOfHabit -> "🗓️"
        AchievementId.PartOfTheRoutine -> "📆"
        AchievementId.RegularAsClockwork -> "🕓"
        AchievementId.ThreeInARow -> "🌱"
        AchievementId.DailyDevotion -> "🔥"
        AchievementId.Faithful -> "🌟"
        AchievementId.HundredDays -> "🗻"
        AchievementId.SpotlessDaily -> "🍀"
        AchievementId.NoBonesAboutIt -> "☘️"
        AchievementId.Showstopper -> "🎪"
        AchievementId.HourWithTheDogs -> "⏰"
        AchievementId.TenHoursDeep -> "🕰️"
        AchievementId.FiftyHoursIn -> "🛋️"
        AchievementId.SecondWind -> "🌬️"
        AchievementId.GrudgeMatch -> "🥊"
        AchievementId.NoShameInIt -> "🤝"
        AchievementId.TenSecondDog -> "⏱️"
        AchievementId.BlinkAndMissIt -> "👁️"
        AchievementId.NightOwl -> "🦉"
        AchievementId.MidnightShift -> "🌙"
        AchievementId.EarlyBird -> "🌅"
        AchievementId.SunriseRitual -> "🐓"
    }
}

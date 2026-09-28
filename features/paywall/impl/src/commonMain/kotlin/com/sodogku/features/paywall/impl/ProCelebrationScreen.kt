package com.sodogku.features.paywall.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.HeroBand
import com.sodogku.libraries.ui.components.HeroBandHeroSize
import com.sodogku.libraries.ui.components.HeroBandKickerTop
import com.sodogku.libraries.ui.components.Screen
import com.sodogku.libraries.ui.components.button.ButtonPrimary
import com.sodogku.libraries.ui.components.button.ButtonSize
import com.sodogku.libraries.ui.components.celebration.Arriving
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.text.OutlinedText
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.VerticalSpacerD500
import com.sodogku.system.VerticalSpacerD800
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.paywall_benefit_boosters
import sodogku.libraries.resources.generated.resources.paywall_benefit_free_helps
import sodogku.libraries.resources.generated.resources.paywall_benefit_jump
import sodogku.libraries.resources.generated.resources.paywall_benefit_no_ads
import sodogku.libraries.resources.generated.resources.paywall_benefit_offline
import sodogku.libraries.resources.generated.resources.pro_celebration_done
import sodogku.libraries.resources.generated.resources.pro_celebration_headline
import sodogku.libraries.resources.generated.resources.pro_celebration_kicker
import sodogku.libraries.resources.generated.resources.pro_celebration_thanks

/**
 * What a player sees straight after buying Pro.
 *
 * ## Why it exists at all
 *
 * The sheet used to close onto the board with nothing said, on the reasoning
 * that an interstitial after a purchase is one more thing between a player and
 * the game they just paid for. That reasoning holds for an *ad* and it was
 * wrong here: a payment that produces no visible response is the shape of a
 * payment that failed, and the player is left checking whether the ads really
 * are gone. This page is the receipt.
 *
 * It is also the one moment in the app where thanking someone is not filler.
 *
 * ## Why it looks like this
 *
 * It is deliberately the two pages it sits between, and neither one new. The
 * band, the hanging dog and the staged arrival are the streak ceremony's and
 * the win page's ([Arriving], [HeroBand]); the paw-bulleted benefits are the
 * paywall's own
 * [Benefit] rows with the same strings. A player reads the second list as the
 * first list coming true, which is the whole point, and a third visual
 * language for one screen would have been the wrong kind of special.
 *
 * The headline is the amber outlined display face, which in this app marks a
 * cheer rather than a heading. It is used by the win screen's verdict and the
 * streak count, and nothing else.
 *
 * ## What it is not
 *
 * Not a sheet. A sheet is dismissible by a swipe and leaves the player back in
 * whatever they were doing; this is a place they arrived at. It also has one
 * way out, the button, so it cannot be left half open behind the board.
 *
 * Not shown after a restore. Nothing was bought, and confetti at someone
 * reinstalling on a new phone reads as the app not knowing what happened. See
 * `PaywallEvent.Purchased.celebrate`.
 */
@Composable
fun ProCelebrationScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    Screen(modifier = modifier) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // Top-aligned, and deliberately not centred. Centring it in
                // the leftover space was tried and it lifts the band off the
                // top of the display, leaving a strip of cream above a band
                // whose whole design is to bleed under the status bar. The gap
                // this leaves under the last benefit is the same gap the streak
                // ceremonies leave, which is where this layout comes from.
                //
                // Scrolls when it has to: a short display or a large system
                // font size. The button is outside this column so it is never
                // the thing that scrolls away.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Arriving(order = 0, modifier = Modifier.fillMaxWidth()) {
                    HeroBand(
                        color = AppTheme.colors.accentBrand,
                        // The band bleeds under the status bar on purpose, so
                        // the inset goes into the kicker rather than above the
                        // band. Without it the kicker sits under the clock.
                        kickerTopPadding = HeroBandKickerTop + padding.calculateTopPadding(),
                        kicker = {
                            Text(
                                text = stringResource(Res.string.pro_celebration_kicker),
                                typography = AppTheme.typography.Label.L600,
                                color = AppTheme.colors.onAccentPrimary,
                            )
                        },
                        // No ray burst behind the dog, though the win page has
                        // one. Tried, and the two celebration devices fight:
                        // the burst is drawn unclipped so its rays cross the
                        // kicker above and run down over the cream below, and
                        // the band's own paw watermark is already doing that
                        // job. The burst earns its place on the win page
                        // because there is no band there to carry the moment.
                        hero = { Dog(pose = DogPose.Solved, size = HeroBandHeroSize) },
                    )
                }

                Arriving(order = 1, modifier = Modifier.fillMaxWidth()) {
                    OutlinedText(
                        text = stringResource(Res.string.pro_celebration_headline),
                        typography = AppTheme.typography.Display.D1100,
                        color = AppTheme.colors.accentBrand,
                        strokeColor = AppTheme.colors.textOutline,
                        strokeWidth = HeadlineStroke,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            top = HeadlineTop,
                            start = ScreenGutter,
                            end = ScreenGutter,
                        ),
                    )
                }

                Arriving(order = 2, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.pro_celebration_thanks),
                        typography = AppTheme.typography.Body.B500,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = Dimension.D500,
                                start = ScreenGutter,
                                end = ScreenGutter,
                            ),
                    )
                }

                Arriving(order = 3, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = BenefitsTop, start = ScreenGutter, end = ScreenGutter),
                    ) {
                        // The same five, in the same order, as the sheet that
                        // sold them. Read as a list of promises on one page and
                        // a list of facts on the other.
                        Benefit(stringResource(Res.string.paywall_benefit_no_ads))
                        Benefit(stringResource(Res.string.paywall_benefit_offline))
                        Benefit(stringResource(Res.string.paywall_benefit_free_helps))
                        Benefit(stringResource(Res.string.paywall_benefit_boosters))
                        Benefit(stringResource(Res.string.paywall_benefit_jump))
                    }
                }

                VerticalSpacerD800()
            }

            ButtonPrimary(
                onClick = onDone,
                size = ButtonSize.Hero,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter),
            ) {
                Text(stringResource(Res.string.pro_celebration_done))
            }

            VerticalSpacerD500()
        }
    }
}

private val HeadlineTop = Dimension.D1100
private val BenefitsTop = Dimension.D800
private val ScreenGutter = Dimension.D800
private val HeadlineStroke = Dimension.D100

@Preview
@Composable
private fun ProCelebrationPreview() {
    PreviewContent {
        ProCelebrationScreen(onDone = {})
    }
}

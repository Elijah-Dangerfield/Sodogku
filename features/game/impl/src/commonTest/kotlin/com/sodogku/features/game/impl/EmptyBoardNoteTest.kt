package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the board says out loud that it opened with no dog on it.
 *
 * Every clause of [shouldNoteEmptyBoard] is a decision somebody argued about,
 * and none of it is drawing — so it is a pure function with a test per clause
 * rather than a condition buried in a composable this repo cannot render under
 * test.
 *
 * [GameViewModelTest] holds the other half: that a real board reaches this with
 * the right arguments, and that the flag behind [alreadySeen] is written when
 * the card appears rather than when the board opens.
 */
class EmptyBoardNoteTest {

    /**
     * A board four seconds into a campaign level that opened empty, untouched,
     * on an install that has never been told about it. Every test below is one
     * departure from this.
     */
    private fun note(
        elapsedMs: Long = EmptyBoardNoteAfterMs,
        rehearsing: Boolean = false,
        isDaily: Boolean = false,
        starterDogCell: Int? = null,
        dogsPlaced: Int = 0,
        marks: Int = 0,
        alreadySeen: Boolean = false,
        otherOverlayUp: Boolean = false,
    ) = shouldNoteEmptyBoard(
        elapsedMs = elapsedMs,
        rehearsing = rehearsing,
        isDaily = isDaily,
        starterDogCell = starterDogCell,
        dogsPlaced = dogsPlaced,
        marks = marks,
        alreadySeen = alreadySeen,
        otherOverlayUp = otherOverlayUp,
    )

    @Test
    fun anUntouchedEmptyCampaignBoardIsExplained() {
        assertTrue(note())
    }

    @Test
    fun notBeforeTheBoardHasFinishedDrawingItself() {
        // The entrance wave is still running when a board opens, and a card over
        // a board nobody has looked at yet explains a thing they have not
        // noticed. The threshold is on the attempt clock, which stops when the
        // app is backgrounded.
        assertFalse(note(elapsedMs = EmptyBoardNoteAfterMs - 1))
        assertTrue(note(elapsedMs = EmptyBoardNoteAfterMs + 1))
    }

    @Test
    fun neverTwice() {
        assertFalse(note(alreadySeen = true))
    }

    @Test
    fun neverOnABoardThatCameWithADog() {
        // The whole subject is the dog that is not there. On a board that has
        // one the sentence is a lie.
        assertFalse(note(starterDogCell = 7))
    }

    @Test
    fun neverOnTheRehearsalBoard() {
        // Belt and braces: the rehearsal always opens with a dog, so
        // `starterDogCell` excludes it as well. It is spelled out because the
        // rehearsal is not a campaign board and nothing one-time should be spent
        // on it, whatever the board happens to hold.
        assertFalse(note(rehearsing = true))
        assertFalse(note(rehearsing = true, starterDogCell = null))
    }

    @Test
    fun neverOnTheDaily() {
        // The daily never gets a starter dog at all, so every daily board is
        // empty and none of them is the position on the campaign curve this is
        // about. Firing here would spend the one showing on a board that cannot
        // teach the rule.
        assertFalse(note(isDaily = true))
    }

    @Test
    fun notToSomebodyWhoHasAlreadyStarted() {
        // A player who has placed a dog or crossed a square off has worked out
        // that the board is real. Interrupting that to say so is worse than
        // saying nothing.
        assertFalse(note(dogsPlaced = 1))
        assertFalse(note(marks = 1))
    }

    @Test
    fun notOverSomethingElse() {
        assertFalse(note(otherOverlayUp = true))
    }
}

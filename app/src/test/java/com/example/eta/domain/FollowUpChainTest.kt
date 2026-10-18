package com.example.eta.domain

import com.example.eta.domain.chain.followUpEnteredAt
import com.example.eta.domain.chain.followUpsAfter
import com.example.eta.domain.chain.isOfferedFollowUp
import com.example.eta.domain.chain.isWaitingFollowUp
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import com.example.eta.domain.planning.pullBlockOf
import com.example.eta.domain.staging.daysUntilBan
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpChainTest {

    private val created = Instant.parse("2026-10-01T08:00:00Z")
    private val checkedOff = Instant.parse("2026-10-10T18:00:00Z")
    private val weekEnd = LocalDate(2026, 10, 25)

    private val washing = Item.newTodo(
        name = "Wäsche waschen",
        category = Category.NEBENBEI,
        priority = Priority.MUST,
        targetDate = null,
        estimatedDuration = 1.hours,
        now = created,
    )

    private fun waiting(name: String, of: Item) = Item.newFollowUp(name, of, created)

    @Test
    fun `a new follow-up waits for its predecessor and is not plannable`() {
        val dry = waiting("Aufhängen", washing)

        assertTrue(dry.isWaitingFollowUp())
        assertEquals(PullBlock.AfterPredecessor, pullBlockOf(dry, weekEnd, openGoals = 0))
    }

    @Test
    fun `a waiting follow-up does not age towards the Sperrliste`() {
        // Months in the Sammelliste while waiting must not count: there is no clock.
        val dry = waiting("Aufhängen", washing)

        assertNull(dry.daysUntilBan(Instant.parse("2027-06-01T08:00:00Z"), TimeZone.UTC))
    }

    @Test
    fun `a follow-up created after its predecessor was finished is released at once`() {
        assertEquals(created, followUpEnteredAt(predecessorFinished = true, now = created))
        assertNull(followUpEnteredAt(predecessorFinished = false, now = created))
    }

    @Test
    fun `checking the predecessor off releases the waiting successor and starts its clock`() {
        val dry = waiting("Aufhängen", washing)

        val changed = followUpsAfter(washing.id, predecessorFinished = true, followUps = listOf(dry), now = checkedOff)

        assertEquals(1, changed.size)
        assertEquals(checkedOff, changed.single().enteredCollectionAt)
        assertTrue(changed.single().isOfferedFollowUp())
        assertFalse(changed.single().isWaitingFollowUp())
    }

    @Test
    fun `a released follow-up is offered and no longer waiting`() {
        val released = waiting("Aufhängen", washing).copy(enteredCollectionAt = checkedOff)

        assertTrue(released.isOfferedFollowUp())
        assertEquals(null, pullBlockOf(released, weekEnd, openGoals = 0))
    }

    @Test
    fun `releasing touches only the waiting successors, never one already taken on`() {
        val taken = waiting("Zusammenlegen", washing).copy(stage = Stage.WEEK)
        val waitingOne = waiting("Wegräumen", washing)

        val changed = followUpsAfter(washing.id, true, listOf(taken, waitingOne), now = checkedOff)

        assertEquals(listOf(waitingOne.id), changed.map { it.id })
    }

    @Test
    fun `ticking the predecessor back makes an offered successor wait again`() {
        val released = waiting("Aufhängen", washing).copy(enteredCollectionAt = checkedOff)

        val changed = followUpsAfter(washing.id, predecessorFinished = false, followUps = listOf(released), now = checkedOff)

        assertNull(changed.single().enteredCollectionAt)
        assertTrue(changed.single().isWaitingFollowUp())
    }

    @Test
    fun `ticking back leaves a successor that was already taken into the week alone`() {
        val taken = waiting("Aufhängen", washing).copy(
            stage = Stage.WEEK,
            enteredCollectionAt = checkedOff,
        )

        assertTrue(followUpsAfter(washing.id, false, listOf(taken), now = checkedOff).isEmpty())
    }

    @Test
    fun `in a chain each step is released only by the one before it`() {
        val drying = waiting("Aufhängen", washing)
        val folding = waiting("Zusammenlegen", drying)

        // Checking off the washing releases the drying, not the folding.
        val afterWashing = followUpsAfter(washing.id, true, listOf(drying, folding), now = checkedOff)
        assertEquals(listOf(drying.id), afterWashing.map { it.id })
        val dryingReleased = drying.copy(enteredCollectionAt = checkedOff)

        // Checking off the drying releases the folding in turn.
        val afterDrying = followUpsAfter(drying.id, true, listOf(dryingReleased, folding), now = checkedOff)
        assertEquals(listOf(folding.id), afterDrying.map { it.id })
    }
}

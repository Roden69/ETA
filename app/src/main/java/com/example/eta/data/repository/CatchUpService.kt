package com.example.eta.data.repository

import com.example.eta.data.local.DayPlanDao
import com.example.eta.data.local.PlannedBlockDao
import com.example.eta.domain.reevaluation.DAILY_CATCH_UP_DAYS
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.domain.reevaluation.unsettledDays
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import com.example.eta.domain.streak.CATCH_UP_WINDOW_DAYS
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** What happened when a past day was made up. */
sealed interface CatchUpResult {
    data class Resolved(val date: LocalDate, val settlement: DailySettlement) : CatchUpResult
    data class Refused(val reason: String) : CatchUpResult
}

/**
 * Making up a day that the evening never settled.
 *
 * Two paths, split by age. The last [DAILY_CATCH_UP_DAYS] days are offered on the
 * dashboard and settled by the ordinary reevaluation, one tap each — a missed
 * evening is the most common way the app's rhythm breaks, and hiding its repair
 * made it worse. Older days stay behind the phrase here: they are a different
 * kind of mistake, and that friction is deliberate.
 *
 * A skipped day still costs the streak and its points; catching it up later
 * does not undo that, it only books what is there to book.
 *
 * **Contracts are not paid retroactively.** A contract is a promise attested to
 * on the day; saying a week later that it was kept is a different act, and one
 * this service is in no position to witness. The harvest of what was actually
 * ticked off is another matter — that record exists, and it is paid.
 */
class CatchUpService(
    private val dayPlanDao: DayPlanDao,
    private val blockDao: PlannedBlockDao,
    private val reevaluationService: ReevaluationService,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** The dashboard's days: yesterday and the six before it, oldest first. */
    suspend fun dailyDays(): List<LocalDate> =
        unsettledWithin(clock.todayIn(timeZone), 1..DAILY_CATCH_UP_DAYS)

    /**
     * Older past days the app planned but never settled, newest first.
     *
     * Bounded by the window: a day a month gone cannot meaningfully be recalled,
     * and offering it would only invite guessing.
     */
    suspend fun openDays(): List<LocalDate> =
        unsettledWithin(clock.todayIn(timeZone), (DAILY_CATCH_UP_DAYS + 1)..CATCH_UP_WINDOW_DAYS)
            .sortedDescending()

    private suspend fun unsettledWithin(today: LocalDate, daysBack: IntRange): List<LocalDate> {
        val from = today.minus(DatePeriod(days = daysBack.last))
        val to = today.minus(DatePeriod(days = daysBack.first))
        return unsettledDays(
            today = today,
            daysBack = daysBack,
            planned = blockDao.findDatesWithBlocks(from, to),
            settled = dayPlanDao.findSettledDates(from, to).toSet(),
        )
    }

    /** Settles an older [date] after the fact, if the phrase was typed exactly. */
    suspend fun catchUp(date: LocalDate, phrase: String): CatchUpResult {
        if (phrase.trim() != CATCH_UP_PHRASE) {
            return CatchUpResult.Refused("Die Eingabe stimmt nicht.")
        }
        val today = clock.todayIn(timeZone)
        if (date >= today) {
            return CatchUpResult.Refused("Heute wird über den Tagesabschluss abgerechnet.")
        }
        if (date >= today.minus(DatePeriod(days = DAILY_CATCH_UP_DAYS))) {
            return CatchUpResult.Refused("Dieser Tag steht auf dem Startbildschirm zum Nachholen bereit.")
        }
        if (date < today.minus(DatePeriod(days = CATCH_UP_WINDOW_DAYS))) {
            return CatchUpResult.Refused("Der Tag liegt zu lange zurück.")
        }
        if (dayPlanDao.find(date)?.isSettled == true) {
            return CatchUpResult.Refused("Der Tag ist schon abgerechnet.")
        }

        // No verdicts: contracts are not credited after the fact. The journal keeps
        // the reason, so a made-up day is never indistinguishable from a lived one.
        val settlement = reevaluationService.settle(
            date = date,
            verdicts = emptyList(),
            journal = mapOf(CATCH_UP_PHRASE to "Nachgetragen am $today."),
        )
        return CatchUpResult.Resolved(date, settlement)
    }
}

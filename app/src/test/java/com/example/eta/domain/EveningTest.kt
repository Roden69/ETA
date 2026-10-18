package com.example.eta.domain

import com.example.eta.domain.planning.EveningStep
import com.example.eta.domain.planning.WakeQuestion
import com.example.eta.domain.planning.WakeRoute
import com.example.eta.domain.planning.etaRingsWakeAlarm
import com.example.eta.domain.planning.eveningSteps
import com.example.eta.domain.planning.wakeQuestionFor
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EveningTest {

    private val wakeDate = LocalDate(2026, 10, 19)

    @Test
    fun `Eta rings its own alarm unless the clock app takes it`() {
        assertTrue(etaRingsWakeAlarm(WakeRoute.ETA, clockAppAvailable = true))
        assertFalse(etaRingsWakeAlarm(WakeRoute.CLOCK_APP, clockAppAvailable = true))
    }

    @Test
    fun `a missing clock app gives the alarm back to Eta`() {
        assertTrue(etaRingsWakeAlarm(WakeRoute.CLOCK_APP, clockAppAvailable = false))
    }

    @Test
    fun `the question is asked once tomorrow is confirmed with the clock app as route`() {
        assertEquals(
            WakeQuestion.ASK,
            wakeQuestionFor(true, WakeRoute.CLOCK_APP, true, tomorrowConfirmed = true, askedFor = null, wakeDate = wakeDate),
        )
    }

    @Test
    fun `no question while tomorrow is not confirmed yet`() {
        assertEquals(
            WakeQuestion.NONE,
            wakeQuestionFor(true, WakeRoute.CLOCK_APP, true, tomorrowConfirmed = false, askedFor = null, wakeDate = wakeDate),
        )
    }

    @Test
    fun `no question with Eta as route`() {
        assertEquals(
            WakeQuestion.NONE,
            wakeQuestionFor(true, WakeRoute.ETA, true, tomorrowConfirmed = true, askedFor = null, wakeDate = wakeDate),
        )
    }

    @Test
    fun `no question while the wake alarm is switched off`() {
        assertEquals(
            WakeQuestion.NONE,
            wakeQuestionFor(false, WakeRoute.CLOCK_APP, true, tomorrowConfirmed = true, askedFor = null, wakeDate = wakeDate),
        )
    }

    @Test
    fun `the question is not put again for the same night`() {
        assertEquals(
            WakeQuestion.NONE,
            wakeQuestionFor(true, WakeRoute.CLOCK_APP, true, tomorrowConfirmed = true, askedFor = wakeDate, wakeDate = wakeDate),
        )
    }

    @Test
    fun `a question asked for an earlier night does not block tonight`() {
        assertEquals(
            WakeQuestion.ASK,
            wakeQuestionFor(
                wakeAlarmOn = true,
                route = WakeRoute.CLOCK_APP,
                clockAppAvailable = true,
                tomorrowConfirmed = true,
                askedFor = LocalDate(2026, 10, 18),
                wakeDate = wakeDate,
            ),
        )
    }

    @Test
    fun `a vanished clock app is said so instead of asked about`() {
        assertEquals(
            WakeQuestion.NO_CLOCK_APP,
            wakeQuestionFor(true, WakeRoute.CLOCK_APP, false, tomorrowConfirmed = true, askedFor = null, wakeDate = wakeDate),
        )
    }

    @Test
    fun `the wake question comes before the wind-down hint`() {
        assertEquals(
            listOf(EveningStep.WAKE_ASK, EveningStep.WIND_DOWN),
            eveningSteps(WakeQuestion.ASK, windDownEnabled = true),
        )
    }

    @Test
    fun `a vanished clock app is shown before the wind-down hint too`() {
        assertEquals(
            listOf(EveningStep.WAKE_NO_CLOCK_APP, EveningStep.WIND_DOWN),
            eveningSteps(WakeQuestion.NO_CLOCK_APP, windDownEnabled = true),
        )
    }

    @Test
    fun `with the hint switched off and no question there is nothing to show`() {
        assertEquals(emptyList<EveningStep>(), eveningSteps(WakeQuestion.NONE, windDownEnabled = false))
    }

    @Test
    fun `the wind-down hint alone when there is no alarm to ask about`() {
        assertEquals(listOf(EveningStep.WIND_DOWN), eveningSteps(WakeQuestion.NONE, windDownEnabled = true))
    }
}

package com.example.eta.domain.planning

import kotlinx.datetime.LocalDate

/** Who rings the wake alarm: Eta itself, or the clock app the user already has. */
enum class WakeRoute(val label: String) {
    ETA("Eta"),
    CLOCK_APP("Uhr-App"),
}

/** What the evening puts to the user about tomorrow's wake alarm. */
enum class WakeQuestion {
    /** Offer to set the alarm in the chosen clock app. */
    ASK,

    /** The clock app is gone: say so, since Eta rings instead. */
    NO_CLOCK_APP,

    /** Nothing to ask tonight. */
    NONE,
}

/** One screen of the evening, in the order it is shown. */
enum class EveningStep {
    WAKE_ASK,
    WAKE_NO_CLOCK_APP,
    WIND_DOWN,
}

/**
 * Whether Eta's own wake alarm rings.
 *
 * It stays silent only while the clock app is the route *and* a clock app is
 * still there to take the alarm. Without one, the alarm would be lost, so Eta
 * rings after all.
 */
fun etaRingsWakeAlarm(route: WakeRoute, clockAppAvailable: Boolean): Boolean =
    route == WakeRoute.ETA || !clockAppAvailable

/**
 * Whether tonight's evening asks about the wake alarm for [wakeDate].
 *
 * Only with a wake alarm wanted at all, the clock app as route, tomorrow's plan
 * confirmed, and the question not yet put for [wakeDate] — at most once a night,
 * whatever the answer was.
 */
fun wakeQuestionFor(
    wakeAlarmOn: Boolean,
    route: WakeRoute,
    clockAppAvailable: Boolean,
    tomorrowConfirmed: Boolean,
    askedFor: LocalDate?,
    wakeDate: LocalDate,
): WakeQuestion = when {
    !wakeAlarmOn || route != WakeRoute.CLOCK_APP || !tomorrowConfirmed || askedFor == wakeDate -> WakeQuestion.NONE
    !clockAppAvailable -> WakeQuestion.NO_CLOCK_APP
    else -> WakeQuestion.ASK
}

/**
 * The screens the evening shows, in order: the wake question first, the wind-down
 * hint last. Empty when there is nothing to say, in which case the evening ends
 * where the planner left off.
 */
fun eveningSteps(question: WakeQuestion, windDownEnabled: Boolean): List<EveningStep> = buildList {
    when (question) {
        WakeQuestion.ASK -> add(EveningStep.WAKE_ASK)
        WakeQuestion.NO_CLOCK_APP -> add(EveningStep.WAKE_NO_CLOCK_APP)
        WakeQuestion.NONE -> Unit
    }
    if (windDownEnabled) add(EveningStep.WIND_DOWN)
}

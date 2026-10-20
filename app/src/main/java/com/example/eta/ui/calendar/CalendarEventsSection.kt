package com.example.eta.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.data.repository.SeriesProposal
import com.example.eta.domain.calendar.SeriesPattern
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.attributes.AppointmentAttributeFields
import com.example.eta.ui.attributes.AppointmentAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate

/**
 * The calendar step's contents, without a frame of its own.
 *
 * One composable for both hosts — a page of the weekly planning pager, and a
 * screen of its own in the evening — because it is one step asked twice over
 * different stretches. Mounting it twice with two layouts would be two places for
 * the same question to drift.
 */
@Composable
fun CalendarEventsSection(
    viewModel: CalendarEventsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val seriesOffer by viewModel.seriesOffer.collectAsStateWithLifecycle()
    val seriesNotice by viewModel.seriesNotice.collectAsStateWithLifecycle()
    val proposals by viewModel.proposals.collectAsStateWithLifecycle()

    // The account picker and the consent screen are Google's, and only an Activity
    // can put them on screen — so the `PendingIntent` travels up from the sync and
    // is launched here.
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsent(result.data) }

    LaunchedEffect(consent) {
        val pending = consent ?: return@LaunchedEffect
        viewModel.dismissConsent()
        runCatching {
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        EtaText(text = "Termine", style = EtaTheme.typography.heading)
        EtaText(
            text = "Übernimm zuerst Termine aus dem Google-Kalender, dann plane den Rest.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        // The stretch being read, said out loud: the weekly planning reads up to
        // the day before the next one, and that edge is only understood if shown.
        EtaText(
            text = "Zeitraum: " + viewModel.from.rangeText(viewModel.to),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        state.error?.let { reason ->
            EtaSurface(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EtaTheme.colors.danger,
                contentPadding = EtaTheme.spacing.md,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaText(
                        text = reason,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.danger,
                    )
                    EtaButton(
                        text = "Erneut versuchen",
                        style = EtaButtonStyle.Secondary,
                        onClick = viewModel::refresh,
                    )
                }
            }
        }

        // One question per series, asked here without a tap. The occurrences below it
        // stay as they are: a series' offer decides nothing until it is taken up.
        proposals.forEach { proposal ->
            key(proposal.event.id) {
                SeriesProposalCard(
                    proposal = proposal,
                    onAdopt = viewModel::openSeries,
                    onKeepSingle = { viewModel.keepSeriesSingle(proposal.event) },
                )
            }
        }

        when {
            !state.connected && !state.syncing -> EtaText(
                text = "Kein Google-Kalender verbunden. Verbinde ihn in den Einstellungen; " +
                    "sonst bleibt dieser Schritt ohne Wirkung.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            state.syncing && state.events.isEmpty() -> EtaText(
                text = "Kalender wird gelesen …",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )

            state.events.isEmpty() -> EtaText(
                text = "Keine neuen Termine. Alle Termine sind übernommen oder ignoriert.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )

            else -> state.events.forEach { event ->
                // Keyed, or the open form of one card moves into its neighbour's
                // slot when the card above it is decided — the same trap the
                // concretize cards documented.
                key(event.id) {
                    EventCard(
                        event = event,
                        clashes = state.clashes[event.id].orEmpty(),
                        seriesNotice = seriesNotice?.takeIf { it.eventId == event.id }?.reason,
                        onIgnore = { viewModel.ignore(event) },
                        onImport = { attributes, origin -> viewModel.import(event, attributes, origin) },
                        onSeries = { viewModel.requestSeries(event) },
                    )
                }
            }
        }

        if (state.connected) {
            EtaButton(
                text = "Kalender aktualisieren",
                style = EtaButtonStyle.Secondary,
                onClick = viewModel::refresh,
            )
        }
    }

    conflicts?.let { prompt ->
        ConflictDialog(
            prompt = prompt,
            onCancelBlock = viewModel::cancelConflict,
            onCarryIntoWeek = viewModel::carryConflictIntoWeek,
            onShorten = viewModel::shortenConflict,
            onPush = viewModel::pushConflict,
            onSplit = viewModel::splitConflict,
            onKeep = viewModel::keepConflict,
            onDismiss = viewModel::dismissConflicts,
        )
    }

    seriesOffer?.let { offer ->
        SeriesDialog(
            offer = offer,
            onAdopt = viewModel::adoptSeries,
            onDismiss = viewModel::dismissSeries,
        )
    }
}

/**
 * One undecided event: what the calendar says, and what to do about it.
 *
 * Three decisions, each its own button: **Übernehmen** and **Als Zeitblocker (ohne
 * Punkte)** both open the form, because the calendar only supplies name, time and
 * length — the category and the margins are the user's — and **Ignorieren** files
 * it away at once. A recurring appointment also offers to take its whole series on.
 */
@Composable
private fun EventCard(
    event: CalendarEvent,
    clashes: List<String>,
    seriesNotice: String?,
    onIgnore: () -> Unit,
    onImport: (AppointmentAttributes, BlockOrigin) -> Unit,
    onSeries: () -> Unit,
) {
    // Which decision the form is for; null while the card shows its three buttons.
    var form by remember { mutableStateOf<BlockOrigin?>(null) }
    var attributes by remember {
        mutableStateOf(
            AppointmentAttributes(start = event.start, duration = event.duration),
        )
    }

    EtaSurface(modifier = Modifier.fillMaxWidth(), contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = event.title,
                style = EtaTheme.typography.bodyStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = buildString {
                    append(event.date.formatLong())
                    if (event.allDay) {
                        append(" · ganztägig")
                    } else {
                        append(" · ")
                        append(event.start.formatClock())
                        append(" · ")
                        append(event.duration.formatShort())
                    }
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            if (clashes.isNotEmpty()) {
                // Said before any decision: an appointment that sits on planned time
                // is a fact the day has to be planned around, not a surprise later.
                EtaText(
                    text = "Überschneidet sich mit: " + clashes.joinToString(", "),
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.danger,
                )
            }

            val chosen = form
            if (chosen != null) {
                AppointmentAttributeFields(
                    value = attributes,
                    onChange = { attributes = it },
                    allDay = event.allDay,
                )
                EtaText(
                    text = if (chosen == BlockOrigin.CALENDAR_BLOCKER) {
                        "Der Termin hält seine Zeit frei und bringt keine Punkte."
                    } else {
                        "Der Termin zählt wie gewohnt: je volle Stunde ein halber Punkt."
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Zurück",
                        style = EtaButtonStyle.Secondary,
                        onClick = { form = null },
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(
                        text = if (chosen == BlockOrigin.CALENDAR_BLOCKER) "Als Zeitblocker eintragen" else "Eintragen",
                        onClick = { onImport(attributes, chosen) },
                    )
                }
            } else {
                EtaButton(
                    text = "Übernehmen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { form = BlockOrigin.CALENDAR_IMPORT },
                )
                EtaButton(
                    text = "Als Zeitblocker (ohne Punkte)",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { form = BlockOrigin.CALENDAR_BLOCKER },
                )
                if (event.seriesId != null) {
                    EtaButton(
                        text = "Als wiederkehrende Aufgabe übernehmen",
                        style = EtaButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSeries,
                    )
                }
                seriesNotice?.let { reason ->
                    EtaText(
                        text = reason,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.danger,
                    )
                }
                EtaButton(
                    text = "Ignorieren",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onIgnore,
                )
                EtaText(
                    text = "Ignorierte Termine findest du unter Einstellungen → Ignorierte Events. " +
                        "Der Google-Kalender bleibt unverändert.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

/**
 * The appointment landed on something that was already planned.
 *
 * The appointment itself is not on the table: it comes from a calendar, which
 * means somebody else's expectation as often as not, and the app has no business
 * moving it. What it *can* offer is what to do with each activity now underneath
 * it — and there are several answers, because a collision is usually **partial**.
 *
 * Every answer says what it does to the activity, and each is shown only where the
 * arithmetic allows it, so a button that appears is a button that works. An
 * appointment that swallows a block whole offers only the whole-activity answers.
 * Leaving it alone is a real answer too: a walk during a phone meeting is two
 * things at once on purpose.
 */
@Composable
private fun ConflictDialog(
    prompt: ConflictPrompt,
    onCancelBlock: (BlockWithItem) -> Unit,
    onCarryIntoWeek: (BlockWithItem) -> Unit,
    onShorten: (ConflictingBlock) -> Unit,
    onPush: (ConflictingBlock) -> Unit,
    onSplit: (ConflictingBlock) -> Unit,
    onKeep: (BlockWithItem) -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Terminkonflikt", onDismiss = onDismiss) {
        EtaText(
            text = "»${prompt.appointment}« (${prompt.time}) bleibt, wie der Kalender es sagt. " +
                "Diese Aufgaben liegen darauf. Jede entscheidest du einzeln; die Serie bleibt " +
                "in jedem Fall unverändert.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )

        prompt.refusal?.let { reason ->
            EtaText(
                text = reason,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.danger,
            )
        }

        prompt.conflicts.forEach { conflict ->
            key(conflict.entry.block.id) {
                ConflictCard(
                    conflict = conflict,
                    appointment = prompt.appointment,
                    time = prompt.time,
                    onCancelBlock = { onCancelBlock(conflict.entry) },
                    onCarryIntoWeek = { onCarryIntoWeek(conflict.entry) },
                    onShorten = { onShorten(conflict) },
                    onPush = { onPush(conflict) },
                    onSplit = { onSplit(conflict) },
                    onKeep = { onKeep(conflict.entry) },
                )
            }
        }

        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Fertig", onClick = onDismiss)
        }
        Spacer(Modifier.size(EtaTheme.spacing.xs))
    }
}

/** One collision, named with what it collides with, and every answer that applies to it. */
@Composable
private fun ConflictCard(
    conflict: ConflictingBlock,
    appointment: String,
    time: String,
    onCancelBlock: () -> Unit,
    onCarryIntoWeek: () -> Unit,
    onShorten: () -> Unit,
    onPush: () -> Unit,
    onSplit: () -> Unit,
    onKeep: () -> Unit,
) {
    val block = conflict.entry.block
    val shortened = conflict.shortened

    EtaSurface(modifier = Modifier.fillMaxWidth(), contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = conflict.entry.item.name,
                style = EtaTheme.typography.bodyStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = "Geplant ${block.start.formatClock()} – " +
                    minuteToLocalTime(block.endMinute()).formatClock() +
                    " · überschneidet sich mit »$appointment« ($time)",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )

            // The partial answers first: they keep the activity, and keeping it is
            // what the user most often wants. Each says the time it would produce,
            // so the consequence is read rather than guessed at.
            shortened?.let {
                EtaButton(
                    text = "Kürzen bis " + minuteToLocalTime(it.endMinute()).formatClock(),
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onShorten,
                )
                EtaText(
                    text = "Die Aufgabe endet vor dem Termin.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            conflict.pushed?.let { pushed ->
                EtaButton(
                    text = "Verschieben auf " + pushed.start.formatClock(),
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPush,
                )
                EtaText(
                    text = "Die Aufgabe beginnt nach dem Termin, mit ihrer ganzen Länge.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            val back = conflict.split
            if (shortened != null && back != null) {
                EtaButton(
                    text = "Aufteilen: davor bis ${minuteToLocalTime(shortened.endMinute()).formatClock()}, " +
                        "danach ${back.formatShort()}",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSplit,
                )
                EtaText(
                    text = "Der Teil vor dem Termin bleibt hier. Der Rest danach kommt als " +
                        "Nachholen in die Wochenliste.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            if (shortened != null || conflict.pushed != null) {
                EtaText(
                    text = "Nur die Aktivität ändert sich. Anfahrt, Rückweg und Pause " +
                        "behalten ihre Länge.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Fällt aus",
                    style = EtaButtonStyle.Secondary,
                    onClick = onCancelBlock,
                )
                EtaButton(
                    text = "In die Woche",
                    style = EtaButtonStyle.Secondary,
                    onClick = onCarryIntoWeek,
                )
            }
            EtaText(
                text = "»Fällt aus« sagt diese Aufgabe nur für diesen Tag ab, kostenlos. " +
                    "»In die Woche« legt sie als Nachholen-Karte in die Wochenliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(
                text = "Beides stehen lassen",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onKeep,
            )
        }
    }
}

/**
 * The series, before it becomes recurring tasks: how it repeats, and the hour the
 * cards will have. The same form as an ordinary appointment, asked once for the
 * whole series.
 */
@Composable
private fun SeriesDialog(
    offer: SeriesOffer,
    onAdopt: (AppointmentAttributes) -> Unit,
    onDismiss: () -> Unit,
) {
    var attributes by remember {
        mutableStateOf(AppointmentAttributes(start = offer.event.start, duration = offer.event.duration))
    }
    EtaDialog(title = "Wiederkehrende Aufgabe", onDismiss = onDismiss) {
        EtaText(
            text = "»${offer.event.title}« wiederholt sich ${offer.pattern.describe()}. Eta legt dafür " +
                "wiederkehrende Aufgaben an, eine je Wochentag. Änderungen an der Serie in Google " +
                "werden nicht übernommen.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        AppointmentAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            allDay = offer.event.allDay,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Übernehmen", onClick = { onAdopt(attributes) })
        }
    }
}

/** "jede Woche (Mi, Fr)", "alle 3 Wochen (Mi)" — how a series repeats, as the dialog says it. */
private fun SeriesPattern.describe(): String {
    val days = formatWeekdays(weekdays)
    return if (weeks == 1) "jede Woche ($days)" else "alle $weeks Wochen ($days)"
}

/** "Mo, 19. Oktober bis Sa, 24. Oktober", or the one day when both ends are the same. */
private fun LocalDate.rangeText(to: LocalDate): String =
    if (this == to) formatLong() else "${formatLong()} bis ${to.formatLong()}"

/**
 * A recurring series found among the undecided appointments: one question for the
 * whole series, or the reason Eta cannot take it on. The occurrences stay listed
 * below either way, so the question decides nothing for the user by itself.
 */
@Composable
private fun SeriesProposalCard(
    proposal: SeriesProposal,
    onAdopt: (SeriesProposal.Offer) -> Unit,
    onKeepSingle: () -> Unit,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth(), contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            when (proposal) {
                is SeriesProposal.Offer -> {
                    EtaText(
                        text = "„${proposal.event.title}“ wiederholt sich ${proposal.repeats()} – " +
                            "als wiederkehrende Aufgabe übernehmen?",
                        style = EtaTheme.typography.body,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "Einzeln lassen",
                            style = EtaButtonStyle.Secondary,
                            onClick = onKeepSingle,
                        )
                        Spacer(Modifier.weight(1f))
                        EtaButton(text = "Übernehmen", onClick = { onAdopt(proposal) })
                    }
                }

                is SeriesProposal.Refused -> {
                    EtaText(
                        text = "„${proposal.event.title}“ wiederholt sich, Eta kann das nicht übernehmen. " +
                            proposal.reason,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                    EtaButton(
                        text = "Verstanden",
                        style = EtaButtonStyle.Secondary,
                        onClick = onKeepSingle,
                    )
                }
            }
        }
    }
}

/** "jeden Mo 08:00 bis Sa, 5. Februar", "alle 3 Wochen Mi 08:00" — the offer's sentence. */
private fun SeriesProposal.Offer.repeats(): String {
    val days = formatWeekdays(pattern.weekdays)
    val rhythm = if (pattern.weeks == 1) "jeden $days" else "alle ${pattern.weeks} Wochen $days"
    val at = if (event.allDay) "" else " " + event.start.formatClock()
    val until = pattern.lastDate?.let { " bis ${it.formatLong()}" }.orEmpty()
    return rhythm + at + until
}

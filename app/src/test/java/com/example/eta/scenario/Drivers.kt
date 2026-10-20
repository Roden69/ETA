package com.example.eta.scenario

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.CatchUpResult
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.PointsTransaction
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.WeekBudget
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.concretize.ConcretizeViewModel
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.lists.DoneEntry
import com.example.eta.ui.lists.SmartListsViewModel
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.quickadd.QuickAddFeedback
import com.example.eta.ui.quickadd.QuickAddViewModel
import com.example.eta.ui.quickadd.quickAddViewModelFactory
import com.example.eta.ui.reevaluation.ReevaluationViewModel
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import com.example.eta.ui.root.concretizeViewModelFactory
import com.example.eta.ui.root.dashboardViewModelFactory
import com.example.eta.ui.root.plannerViewModelFactory
import com.example.eta.ui.root.reevaluationViewModelFactory
import com.example.eta.ui.root.smartListsViewModelFactory
import com.example.eta.ui.root.weekPlannerViewModelFactory
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * Flow drivers: thin wrappers that act the way a screen does, through the real
 * ViewModels and services. They add no rules of their own. Every driver waits for
 * the state it is about to act on to be loaded, then acts, then waits for the
 * result to show. Add a driver here when a scenario needs a new flow; keep it a
 * wrapper.
 */

/**
 * Sets up the app with [persona]: the answers and the standing tasks through
 * `SetupRepository.complete` (what the questionnaire's last step calls), then the
 * morning steps. The questionnaire's screens themselves are not driven.
 */
suspend fun Scenario.completeSetup(persona: SetupPersona) {
    container.setupRepository.complete(persona.setup, persona.standingTasks)
    container.setupRepository.saveMorningSteps(persona.morningSteps)
}

/** Stores [cards] in the Sammelliste as the app stores them: steps and follow-ups included. */
suspend fun Scenario.seedCollection(cards: List<SeedCard>) {
    cards.forEach { card -> container.itemRepository.add(card.item, card.subtasks, followUps = card.followUps) }
}

/** The Schnellerfassung: types [name] into the quick-add field and returns the stored card. */
suspend fun Scenario.quickAdd(name: String): Item {
    val vm = viewModel<QuickAddViewModel>(quickAddViewModelFactory(container))
    return subscribed(vm.feedback) {
        vm.dismissFeedback()
        vm.quickAdd(name)
        awaitState(vm.feedback, "quick-add confirmation for \"$name\"") {
            it is QuickAddFeedback.Added && it.name == name
        }
        awaitItem(Stage.COLLECTION, name)
    }
}

/** Turns a jotted card into a plannable ToDo, as the evening's concretize step does. */
suspend fun Scenario.concretize(item: Item, category: Category, priority: Priority, duration: Duration): Item {
    val vm = viewModel<ConcretizeViewModel>(concretizeViewModelFactory(container))
    subscribed(vm.pending) {
        awaitState(vm.pending, "\"${item.name}\" in the concretize list") { pending -> pending.any { it.id == item.id } }
        vm.concretize(
            item,
            item.name,
            TodoAttributes(category = category, priority = priority, duration = duration),
        )
        awaitState(vm.pending, "\"${item.name}\" leaves the concretize list") { pending ->
            pending.none { it.id == item.id }
        }
    }
    return checkNotNull(container.itemRepository.findById(item.id)) { "\"${item.name}\" vanished" }
}

/** The actions of the weekly planning screen, on a loaded screen. */
class WeekPlannerDriver internal constructor(
    private val scenario: Scenario,
    private val vm: WeekPlannerViewModel,
) {
    /** Takes [item] from the Sammelliste column into the week list. */
    suspend fun pull(item: Item) {
        scenario.awaitState(vm.uiState, "\"${item.name}\" offered in the Sammelliste column") { state ->
            (state.collection + state.held.map { it.first }).any { it.id == item.id }
        }
        vm.pullIntoWeek(item)
        scenario.awaitState(vm.uiState, "\"${item.name}\" in the week list") { state ->
            state.weekList.any { it.id == item.id }
        }
    }

    /** Hands [item] back from the week list to the Sammelliste. */
    suspend fun returnToCollection(item: Item) {
        scenario.awaitState(vm.uiState, "\"${item.name}\" in the week list") { state ->
            state.weekList.any { it.id == item.id }
        }
        vm.returnToCollection(item)
        scenario.awaitState(vm.uiState, "\"${item.name}\" back in the Sammelliste") { state ->
            state.collection.any { it.id == item.id }
        }
    }

    /**
     * An unfinished card of an earlier cycle, taken into the week through the
     * GoalDialog: [attributes] concretize it and the same save puts it in the week.
     */
    suspend fun goal(item: Item, attributes: TodoAttributes) {
        scenario.awaitState(vm.uiState, "\"${item.name}\" offered as an open goal") { state ->
            (state.unfinished + state.collection).any { it.id == item.id }
        }
        vm.concretizeGoal(item, item.name, attributes, intoWeek = true)
        scenario.awaitState(vm.uiState, "\"${item.name}\" in the week list") { state ->
            state.weekList.any { it.id == item.id }
        }
    }

    /** "In diese Woche übernehmen" on an open goal of an earlier cycle. */
    suspend fun takeUnfinished(item: Item) {
        scenario.awaitState(vm.uiState, "\"${item.name}\" among the open goals") { state ->
            state.unfinished.any { it.id == item.id }
        }
        vm.takeUnfinishedIntoWeek(item)
        scenario.awaitState(vm.uiState, "\"${item.name}\" in the week list") { state ->
            state.weekList.any { it.id == item.id }
        }
    }

    /** The week's budget as the screen shows it, once the planning has loaded. */
    suspend fun budget(): WeekBudget = scenario.awaitState(vm.uiState, "the week budget loaded") { state ->
        state.weekStart != null
    }.budget

    /** The open goals of an earlier cycle the screen shows at the top of the Sammelliste column. */
    suspend fun openGoals(): List<Item> = scenario.awaitState(vm.uiState, "the week planner loaded") { state ->
        state.weekEnd != state.weekStart
    }.unfinished
}

/** The weekly planning screen for [scope], for the duration of [body]. */
suspend fun Scenario.weekPlanner(scope: WeekScope = WeekScope.SCHEDULED, body: suspend WeekPlannerDriver.() -> Unit) {
    val vm = viewModel<WeekPlannerViewModel>(weekPlannerViewModelFactory(container, scope), key = "week-$scope")
    subscribed(vm.uiState) { WeekPlannerDriver(this@weekPlanner, vm).body() }
}

/** The actions of a day planner screen, on a loaded screen. */
class DayPlanDriver internal constructor(
    private val scenario: Scenario,
    private val vm: DayPlannerViewModel,
) {
    /** Drops [item] from the revolver onto [at]; an overlap would be a prompt, so the test fails on one. */
    suspend fun place(item: Item, at: LocalTime) {
        scenario.awaitState(vm.uiState, "\"${item.name}\" offered in the day revolver") { state ->
            state.revolver.any { it.id == item.id }
        }
        vm.place(item, at.hour * MINUTES_PER_HOUR + at.minute)
        scenario.awaitState(vm.uiState, "\"${item.name}\" placed at $at") { state ->
            state.blocks.any { it.item.id == item.id }
        }
    }

    /** "Tagesplan bestätigen": ends the planning phase for the day. */
    suspend fun confirm() {
        vm.confirm()
        scenario.awaitState(
            scenario.container.planRepository.observeDayPlan(vm.date),
            "the plan of ${vm.date} confirmed",
        ) { it?.isConfirmed == true }
    }

    /** Drags the placed [entry] to [at] on the same day. */
    suspend fun move(entry: BlockWithItem, at: LocalTime) {
        scenario.awaitState(vm.uiState, "\"${entry.item.name}\" placed on the day") { state ->
            state.blocks.any { it.block.id == entry.block.id }
        }
        vm.move(entry.block.id, at.hour * MINUTES_PER_HOUR + at.minute)
        scenario.awaitState(vm.uiState, "\"${entry.item.name}\" moved to $at") { state ->
            state.blocks.any { it.block.id == entry.block.id && it.block.start == at }
        }
    }

    /** "Zurück in die Woche": the placed [entry] goes back to the week list. */
    suspend fun returnToWeek(entry: BlockWithItem) {
        scenario.awaitState(vm.uiState, "\"${entry.item.name}\" placed on the day") { state ->
            state.blocks.any { it.block.id == entry.block.id }
        }
        vm.returnToWeek(entry.block.id)
        scenario.awaitState(vm.uiState, "\"${entry.item.name}\" gone from the day") { state ->
            state.blocks.none { it.block.id == entry.block.id }
        }
    }

    /** Creates a ToDo right on the day at [at], as the planner's "Neue Aufgabe" does; returns it. */
    suspend fun createTodo(name: String, attributes: TodoAttributes, at: LocalTime): Item {
        vm.createTodo(name, attributes, note = "", atMinute = at.hour * MINUTES_PER_HOUR + at.minute)
        scenario.awaitState(vm.uiState, "\"$name\" placed at $at") { state ->
            state.blocks.any { it.item.name == name }
        }
        return checkNotNull(scenario.container.itemRepository.observeStage(Stage.DAY).first().firstOrNull { it.name == name }) {
            "\"$name\" is not in the day list"
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}

/** The day planner for [day] (today or tomorrow), for the duration of [body]. */
suspend fun Scenario.planDay(day: PlannerDay, body: suspend DayPlanDriver.() -> Unit) {
    val vm = viewModel<DayPlannerViewModel>(plannerViewModelFactory(container, day), key = "planner-$day")
    subscribed(vm.uiState) { DayPlanDriver(this@planDay, vm).body() }
}

/**
 * Ticks off today's block of [item] on the dashboard, as "Gerade"'s checkbox does.
 *
 * Ticking is the only act here. Points arrive later, with the evening settlement.
 */
suspend fun Scenario.tick(item: Item) {
    val vm = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
    subscribed(vm.uiState) {
        val entry = awaitState(vm.uiState, "\"${item.name}\" on today's dashboard") { state ->
            state.todayBlocks.any { it.item.id == item.id }
        }.todayBlocks.first { it.item.id == item.id }
        vm.toggleCompleted(entry)
        awaitState(vm.uiState, "\"${item.name}\" ticked off") { state ->
            state.todayBlocks.any { it.item.id == item.id && it.block.isCompleted }
        }
    }
}

/** The evening's review of one day. */
class EveningChain internal constructor(
    private val scenario: Scenario,
    private val vm: ReevaluationViewModel,
) {
    /**
     * Settles the day: harvests the points, settles contracts, growth and rewards.
     *
     * Returns the settlement, or null when the day had already been settled before
     * this call. Nothing is booked then: the ledger is what a scenario checks.
     */
    suspend fun settle(): DailySettlement? {
        scenario.awaitState(vm.uiState, "the evening review of ${vm.date} loaded") { !it.loading }
        val state = vm.uiState.value
        if (state.settled != null || state.alreadySettled) return null
        vm.settle {}
        return scenario.awaitState(vm.uiState, "the settlement of ${vm.date}") {
            it.settled != null || it.alreadySettled
        }.settled
    }

    /**
     * "Verwerfen" on the evening's open block of [item]: the block is called off, and
     * the card goes back to the Sammelliste with its clock.
     */
    suspend fun discard(item: Item) {
        val entry = scenario.awaitState(vm.uiState, "\"${item.name}\" open in the evening review") { state ->
            state.blocks.any { it.item.id == item.id && it.block.isOpen }
        }.blocks.first { it.item.id == item.id && it.block.isOpen }
        vm.discard(entry)
        scenario.awaitState(
            scenario.container.itemRepository.observeStage(Stage.COLLECTION),
            "\"${item.name}\" back in the Sammelliste",
        ) { cards -> cards.any { it.id == item.id } }
    }
}

/** The evening reevaluation of today, for the duration of [body]. */
suspend fun <T> Scenario.eveningChain(body: suspend EveningChain.() -> T): T {
    val vm = viewModel<ReevaluationViewModel>(reevaluationViewModelFactory(container), key = "evening-${clock.today}")
    return subscribed(vm.uiState) { EveningChain(this@eveningChain, vm).body() }
}

/**
 * Catches an older evening up, typing [phrase] as the screen asks. Returns the
 * service's answer; a wrong phrase is refused.
 */
suspend fun Scenario.catchUp(date: LocalDate, phrase: String): CatchUpResult =
    container.catchUpService.catchUp(date, phrase)

/** The Sammelliste, the week list, … : the cards on [stage] right now. */
suspend fun Scenario.items(stage: Stage): List<Item> = container.itemRepository.observeStage(stage).first()

/** The blocks planned on [date], with their items. */
suspend fun Scenario.blocksOn(date: LocalDate): List<BlockWithItem> = container.planRepository.findDay(date)

/** The points balance: the sum of every transaction. */
suspend fun Scenario.balance(): Double = container.pointsRepository.observeBalance().first()

/** Every points transaction, oldest last. */
suspend fun Scenario.transactions(): List<PointsTransaction> =
    container.pointsRepository.observeRecent(Int.MAX_VALUE).first()

/** The Erfolgsliste entry for [name]; waits until the list shows it. */
suspend fun Scenario.awaitErfolgsliste(name: String): DoneEntry {
    val vm = viewModel<SmartListsViewModel>(smartListsViewModelFactory(container))
    return subscribed(vm.uiState) {
        awaitState(vm.uiState, "\"$name\" in the Erfolgsliste") { state ->
            state.done.any { it.name == name }
        }.done.first { it.name == name }
    }
}

/** The block of [item] planned on [date]; fails when there is none. */
suspend fun Scenario.blockOf(item: Item, date: LocalDate): PlannedBlock =
    checkNotNull(blocksOn(date).firstOrNull { it.item.id == item.id }) {
        "no block of \"${item.name}\" on $date"
    }.block

private suspend fun Scenario.awaitItem(stage: Stage, name: String): Item =
    awaitState(container.itemRepository.observeStage(stage), "\"$name\" in $stage") { cards ->
        cards.any { it.name == name }
    }.first { it.name == name }

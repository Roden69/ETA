package com.example.eta.scenario

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.concretize.ConcretizeViewModel
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.lists.DoneEntry
import com.example.eta.ui.lists.SmartListsViewModel
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.quickadd.QuickAddFeedback
import com.example.eta.ui.quickadd.QuickAddViewModel
import com.example.eta.ui.quickadd.quickAddViewModelFactory
import com.example.eta.ui.root.concretizeViewModelFactory
import com.example.eta.ui.root.dashboardViewModelFactory
import com.example.eta.ui.root.plannerViewModelFactory
import com.example.eta.ui.root.settingsViewModelFactory
import com.example.eta.ui.root.smartListsViewModelFactory
import com.example.eta.ui.root.weekPlannerViewModelFactory
import com.example.eta.ui.settings.SettingsMessage
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/*
 * Drivers for the scenario areas A to E. Each one is a thin wrapper over the
 * ViewModel or service a screen would call, waiting for the state it acts on.
 * No rule lives here: a check reads the database or the ViewModel state and is
 * asserted in the test itself.
 */

/** Every card in every stage, the way the database holds them. */
suspend fun Scenario.everyItem(): List<Item> = Stage.entries.flatMap { items(it) }

/** The one card called [name]; fails when there is none or more than one. */
suspend fun Scenario.itemNamed(name: String): Item = everyItem().single { it.name == name }

/** The weekly planning ViewModel for [scope], as the screen gets it. */
fun Scenario.weekPlannerVm(scope: WeekScope = WeekScope.SCHEDULED): WeekPlannerViewModel =
    viewModel(weekPlannerViewModelFactory(container, scope), key = "week-$scope")

/** The day planner ViewModel for [day], as the screen gets it. */
fun Scenario.plannerVm(day: PlannerDay): DayPlannerViewModel =
    viewModel(plannerViewModelFactory(container, day), key = "planner-$day")

/** The Schnellerfassung ViewModel. */
fun Scenario.quickAddVm(): QuickAddViewModel =
    viewModel(quickAddViewModelFactory(container), key = "quick-add")

/** Types [name] into quick-add and waits for the feedback that names it. */
suspend fun Scenario.quickAddFeedback(name: String): QuickAddFeedback {
    val vm = quickAddVm()
    return subscribed(vm.feedback) {
        vm.dismissFeedback()
        vm.quickAdd(name)
        awaitState(vm.feedback, "quick-add feedback for \"$name\"") { it?.nameOf() == name }!!
    }
}

private fun QuickAddFeedback.nameOf(): String = when (this) {
    is QuickAddFeedback.Added -> name
    is QuickAddFeedback.AddedRecurring -> name
    is QuickAddFeedback.Blocked -> name
}

/** The concretize step's ViewModel. */
fun Scenario.concretizeVm(): ConcretizeViewModel =
    viewModel(concretizeViewModelFactory(container), key = "concretize")

/** The dashboard's ViewModel, which lists today's blocks. */
fun Scenario.dashboardVm(): DashboardViewModel =
    viewModel(dashboardViewModelFactory(container), key = "dashboard")

/** Unticks [item]'s block of today: the checkbox again, on a block already ticked. */
suspend fun Scenario.untick(item: Item) {
    val vm = dashboardVm()
    subscribed(vm.uiState) {
        val entry = awaitState(vm.uiState, "\"${item.name}\" ticked off today") { state ->
            state.todayBlocks.any { it.item.id == item.id && it.block.isCompleted }
        }.todayBlocks.first { it.item.id == item.id }
        vm.toggleCompleted(entry)
        awaitState(vm.uiState, "\"${item.name}\" open again") { state ->
            state.todayBlocks.any { it.item.id == item.id && it.block.isOpen }
        }
    }
}

/** The Listen tab's ViewModel: the Erfolgsliste and the other lists. */
fun Scenario.smartListsVm(): SmartListsViewModel =
    viewModel(smartListsViewModelFactory(container), key = "smart-lists")

/** The settings ViewModel, as the settings tab gets it. */
fun Scenario.settingsVm(): SettingsViewModel =
    viewModel(settingsViewModelFactory(container), key = "settings")

/**
 * Changes the stored answers through the settings tab: [transform] is applied to
 * the draft and "Sichern" is pressed. Returns once the save reported success.
 */
suspend fun Scenario.changeSettings(transform: (UserSetup) -> UserSetup) {
    val vm = settingsVm()
    subscribed(vm.draft, vm.message) {
        awaitState(vm.draft, "the settings draft loaded") { it != null }
        vm.dismissMessage()
        vm.update(transform)
        vm.save()
        awaitState(vm.message, "the settings saved") { it is SettingsMessage.Saved }
    }
}

/** The stored setup as the database holds it. */
suspend fun Scenario.storedSetup(): UserSetup = checkNotNull(container.setupRepository.find()) { "no setup stored" }

/** The block of standing role [role] on [date]; fails when there is none. */
suspend fun Scenario.roleBlockOn(role: ItemRole, date: LocalDate): BlockWithItem =
    checkNotNull(blocksOn(date).firstOrNull { it.item.role == role }) { "no $role block on $date" }

/** Every standing definition (recurring item), live or retired. */
suspend fun Scenario.standingDefinitions(): List<Item> =
    everyItem().filter { it.type == ItemType.RECURRING }

/** The live standing definition called [name] that repeats weekly on [weekday]. */
suspend fun Scenario.liveDefinition(name: String, weekday: DayOfWeek): Item =
    standingDefinitions().single { def ->
        def.name == name && def.completedAt == null &&
            (def.recurrenceRule as? RecurrenceRule.Weekly)?.weekday == weekday
    }

/** Every block of [item] between [from] and [to], inclusive. */
suspend fun Scenario.blocksOf(item: Item, from: LocalDate, to: LocalDate): List<BlockWithItem> =
    container.planRepository.findRange(from, to).filter { it.item.id == item.id }

/** Waits until [item] is listed in [stage] in the database. */
suspend fun Scenario.awaitStage(item: Item, stage: Stage): Item =
    awaitState(container.itemRepository.observeStage(stage), "\"${item.name}\" in $stage") { cards ->
        cards.any { it.id == item.id }
    }.single { it.id == item.id }

/** The Erfolgsliste once [name] is on it; the full list is returned for the other checks. */
suspend fun Scenario.achievementsIncluding(name: String): List<DoneEntry> {
    val vm = smartListsVm()
    return subscribed(vm.uiState) {
        awaitState(vm.uiState, "\"$name\" in the Erfolgsliste") { state ->
            state.done.any { it.name == name }
        }.done
    }
}

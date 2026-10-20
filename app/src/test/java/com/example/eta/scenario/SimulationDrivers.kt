package com.example.eta.scenario

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PullBlock
import com.example.eta.domain.streak.Streak
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.reevaluation.MakeUpOffer
import com.example.eta.ui.reevaluation.ReevaluationViewModel
import com.example.eta.ui.root.dashboardViewModelFactory
import com.example.eta.ui.root.plannerViewModelFactory
import com.example.eta.ui.root.reevaluationViewModelFactory
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import com.example.eta.ui.root.weekPlannerViewModelFactory
import com.example.eta.data.repository.WeekScope
import kotlin.time.Duration
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.flow.first

/*
 * Drivers the simulation needs beyond the basic ones in [Drivers.kt]. Each one is a
 * thin wrapper over the ViewModel or service a screen calls.
 */

/** The dashboard's live streak, read through the real ViewModel. */
suspend fun Scenario.streak(): Streak {
    val vm = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
    return subscribed(vm.uiState) {
        awaitState(vm.uiState, "the dashboard loaded for ${clock.today}") { it.today == clock.today }.streak
    }
}

/** Ticks the block of [item] off the dashboard again ("Gerade" checkbox). */
suspend fun Scenario.untick(item: Item) {
    val vm = viewModel<DashboardViewModel>(dashboardViewModelFactory(container))
    subscribed(vm.uiState) {
        val entry = awaitState(vm.uiState, "\"${item.name}\" ticked on today's dashboard") { state ->
            state.todayBlocks.any { it.item.id == item.id && it.block.isCompleted }
        }.todayBlocks.first { it.item.id == item.id }
        vm.toggleCompleted(entry)
        awaitState(vm.uiState, "\"${item.name}\" unticked") { state ->
            state.todayBlocks.any { it.item.id == item.id && !it.block.isCompleted }
        }
    }
}

/**
 * Places [item] on today's plan at [at] and reports whether the planner asked a
 * question instead: a drop onto occupied time opens an overlap prompt, which is
 * answered by [dismissOverlap] (nothing placed) or left for the caller.
 */
suspend fun Scenario.placeToday(item: Item, at: LocalTime): Boolean {
    val vm = viewModel<DayPlannerViewModel>(plannerViewModelFactory(container, PlannerDay.TODAY), key = "planner-TODAY")
    return subscribed(vm.uiState, vm.overlapPrompt) {
        awaitState(vm.uiState, "\"${item.name}\" offered in the day revolver") { state ->
            state.revolver.any { it.id == item.id }
        }
        vm.place(item, at.hour * 60 + at.minute)
        val asked = awaitState(vm.overlapPrompt, "the planner answered the drop of \"${item.name}\" at $at") { prompt ->
            prompt != null || vm.uiState.value.blocks.any { it.item.id == item.id }
        } != null
        if (asked) vm.dismissOverlap()
        !asked
    }
}

/** The week planner's answer for a card that cannot be pulled yet: the reason it is held back. */
suspend fun Scenario.heldBack(item: Item): PullBlock {
    val vm = viewModel<WeekPlannerViewModel>(weekPlannerViewModelFactory(container, WeekScope.SCHEDULED), key = "week-SCHEDULED")
    return subscribed(vm.uiState) {
        vm.pullIntoWeek(item)
        awaitState(vm.uiState, "\"${item.name}\" held back") { state ->
            state.held.any { it.first.id == item.id }
        }.held.first { it.first.id == item.id }.second
    }
}


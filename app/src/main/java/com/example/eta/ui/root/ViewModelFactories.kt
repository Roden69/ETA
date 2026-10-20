package com.example.eta.ui.root

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eta.data.repository.WeekScope
import com.example.eta.di.AppContainer
import com.example.eta.ui.calendar.CalendarEventsViewModel
import com.example.eta.ui.concretize.ConcretizeViewModel
import com.example.eta.ui.contracts.ContractsViewModel
import com.example.eta.ui.dashboard.DashboardViewModel
import com.example.eta.ui.evening.EveningViewModel
import com.example.eta.ui.growth.GrowthTasksViewModel
import com.example.eta.ui.lists.SmartListsViewModel
import com.example.eta.ui.notes.NotesViewModel
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.PlannerDay
import com.example.eta.ui.reevaluation.CatchUpBannerViewModel
import com.example.eta.ui.reevaluation.ReevaluationViewModel
import com.example.eta.ui.reminders.RemindersViewModel
import com.example.eta.ui.rewards.RewardsViewModel
import com.example.eta.ui.settings.CalendarSettingsViewModel
import com.example.eta.ui.settings.SettingsViewModel
import com.example.eta.ui.setup.FeatureSuggestionViewModel
import com.example.eta.ui.setup.SetupViewModel
import com.example.eta.ui.stats.StatisticsViewModel
import com.example.eta.ui.vacation.VacationViewModel
import com.example.eta.ui.week.WeekTabViewModel
import com.example.eta.ui.weekplanner.WeekPlannerViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.todayIn

/*
 * Every ViewModel the root screens obtain, wired from the [AppContainer].
 *
 * The factories take the container's clock and time zone wherever the
 * ViewModel accepts them, so a scenario test that builds its container on a
 * scripted clock gets the same wiring the app gets. The only exceptions are
 * `quickAddViewModelFactory` and `tutorialViewModelFactory`, which live next to
 * their screens.
 */

/**
 * Manual factories, matching the container-based wiring. Swapping in Hilt later
 * would replace these without touching the view models themselves.
 */
internal fun rootViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RootViewModel(
                container.setupRepository,
                container.planningAlarmCoordinator,
                container.taskStartCoordinator,
                container.reminderCoordinator,
                container.calendarRefreshCoordinator,
                container.scheduleMaintenance,
                container.weekPlanningService,
                container.tutorialStore,
                resetLocalStores = {
                    container.eveningStore.reset()
                    container.featureSuggestionStore.reset()
                },
            )
        }
    }

internal fun setupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            SetupViewModel(
                setupRepository = container.setupRepository,
                calendarRepository = container.calendarRepository,
                calendarSync = container.calendarSyncService,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun featureSuggestionViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            FeatureSuggestionViewModel(
                setupRepository = container.setupRepository,
                store = container.featureSuggestionStore,
                tutorialStore = container.tutorialStore,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun dashboardViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DashboardViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                pointsRepository = container.pointsRepository,
                contractRepository = container.contractRepository,
                conflictRepository = container.conflictRepository,
                setupRepository = container.setupRepository,
                subtaskRepository = container.subtaskRepository,
                taskStartCoordinator = container.taskStartCoordinator,
                clock = container.clock,
                timeZone = container.timeZone,
                // Not in the practice app: its day is simulated.
                sleepLogRepository = container.sleepLogRepository.takeIf { !container.sandbox },
                nightService = container.nightService.takeIf { !container.sandbox },
            )
        }
    }

internal fun plannerViewModelFactory(
    container: AppContainer,
    day: PlannerDay,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            DayPlannerViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                subtaskRepository = container.subtaskRepository,
                subtaskGroupService = container.subtaskGroupService,
                setupRepository = container.setupRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                weekPlanningService = container.weekPlanningService,
                pointsRepository = container.pointsRepository,
                nightService = container.nightService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                day = day,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun weekPlannerViewModelFactory(
    container: AppContainer,
    scope: WeekScope,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            WeekPlannerViewModel(
                scope = scope,
                weekPlanningService = container.weekPlanningService,
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                setupRepository = container.setupRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                clock = container.clock,
            )
        }
    }

internal fun weekTabViewModelFactory(
    container: AppContainer,
    from: LocalDate? = null,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            WeekTabViewModel(
                nightService = container.nightService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                taskStartCoordinator = container.taskStartCoordinator,
                recurringTaskService = container.recurringTaskService,
                planRepository = container.planRepository,
                setupRepository = container.setupRepository,
                subtaskRepository = container.subtaskRepository,
                itemRepository = container.itemRepository,
                clock = container.clock,
                timeZone = container.timeZone,
                from = from,
                calendarRepository = container.calendarRepository,
            )
        }
    }

internal fun concretizeViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ConcretizeViewModel(
                itemRepository = container.itemRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                subtaskRepository = container.subtaskRepository,
                setupRepository = container.setupRepository,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun vacationViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            VacationViewModel(
                vacationRepository = container.vacationRepository,
                itemRepository = container.itemRepository,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

/**
 * The calendar step, over whichever stretch its host is planning.
 *
 * The weekly phase builds its own over the week; this one is for the evening and
 * covers tomorrow alone.
 */
internal fun calendarViewModelFactory(
    container: AppContainer,
    from: LocalDate,
    to: LocalDate,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CalendarEventsViewModel(
                calendarRepository = container.calendarRepository,
                planRepository = container.planRepository,
                syncService = container.calendarSyncService,
                importService = container.calendarImportService,
                scheduleMaintenance = container.scheduleMaintenance,
                from = from,
                to = to,
            )
        }
    }

internal fun calendarSettingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CalendarSettingsViewModel(
                repository = container.calendarRepository,
                syncService = container.calendarSyncService,
                clock = container.clock,
                timeZone = container.timeZone,
                onConnectionChanged = container.calendarRefreshCoordinator::reschedule,
            )
        }
    }

internal fun settingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            SettingsViewModel(
                setupRepository = container.setupRepository,
                backupService = container.backupService,
                catchUpService = container.catchUpService,
                phaseService = container.planningPhaseService,
                wakeAlarmCoordinator = container.wakeAlarmCoordinator,
                taskStartCoordinator = container.taskStartCoordinator,
                planRepository = container.planRepository,
                reminderRepository = container.reminderRepository,
                eveningStore = container.eveningStore,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun smartListsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            SmartListsViewModel(
                itemRepository = container.itemRepository,
                planRepository = container.planRepository,
                scheduleMaintenance = container.scheduleMaintenance,
                recurringTaskService = container.recurringTaskService,
                taskStartCoordinator = container.taskStartCoordinator,
                reevaluationService = container.reevaluationService,
                subtaskRepository = container.subtaskRepository,
                setupRepository = container.setupRepository,
                weekPlanningService = container.weekPlanningService,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun rewardsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RewardsViewModel(
                rewardRepository = container.rewardRepository,
                recurringTaskService = container.recurringTaskService,
            )
        }
    }

internal fun statisticsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            StatisticsViewModel(
                planRepository = container.planRepository,
                sleepLogRepository = container.sleepLogRepository,
                recurringTaskService = container.recurringTaskService,
                setupRepository = container.setupRepository,
                nightService = container.nightService,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun notesViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            NotesViewModel(
                noteRepository = container.noteRepository,
                itemRepository = container.itemRepository,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun growthTasksViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            GrowthTasksViewModel(
                growthService = container.growthService,
                recurringTaskService = container.recurringTaskService,
                taskStartCoordinator = container.taskStartCoordinator,
                subtaskRepository = container.subtaskRepository,
                itemRepository = container.itemRepository,
            )
        }
    }

internal fun remindersViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            RemindersViewModel(
                repository = container.reminderRepository,
                coordinator = container.reminderCoordinator,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun contractsViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { ContractsViewModel(container.contractRepository, clock = container.clock, timeZone = container.timeZone) }
    }

/**
 * The reevaluation of [date]: the day being lived when none is given, or an earlier
 * evening that is being caught up.
 */
internal fun reevaluationViewModelFactory(container: AppContainer, date: LocalDate? = null): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            ReevaluationViewModel(
                date = date ?: container.clock.todayIn(container.timeZone),
                reevaluationService = container.reevaluationService,
                planRepository = container.planRepository,
                itemRepository = container.itemRepository,
                dayClosingService = container.dayClosingService,
                subtaskRepository = container.subtaskRepository,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun catchUpBannerViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            CatchUpBannerViewModel(
                catchUpService = container.catchUpService,
                planRepository = container.planRepository,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

internal fun eveningViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            EveningViewModel(
                setupRepository = container.setupRepository,
                nightService = container.nightService,
                planRepository = container.planRepository,
                subtaskRepository = container.subtaskRepository,
                eveningStore = container.eveningStore,
                rearmWakeAlarm = container.wakeAlarmCoordinator::reschedule,
                clock = container.clock,
                timeZone = container.timeZone,
            )
        }
    }

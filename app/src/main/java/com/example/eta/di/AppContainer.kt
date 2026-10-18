package com.example.eta.di

import android.content.Context
import androidx.room3.Room
import com.example.eta.alarm.PlanningAlarmCoordinator
import com.example.eta.alarm.ReminderCoordinator
import com.example.eta.alarm.TaskStartCoordinator
import com.example.eta.alarm.EveningStore
import com.example.eta.alarm.WakeAlarmCoordinator
import com.example.eta.data.backup.BackupService
import com.example.eta.data.calendar.GoogleAuth
import com.example.eta.data.calendar.GoogleCalendarApi
import com.example.eta.data.local.EtaDatabase
import com.example.eta.data.local.ETA_DATABASE_MIGRATIONS
import com.example.eta.data.repository.CalendarImportService
import com.example.eta.data.repository.CalendarRepository
import com.example.eta.data.repository.CalendarSyncService
import com.example.eta.data.repository.CatchUpService
import com.example.eta.data.repository.ConflictRepository
import com.example.eta.data.repository.ContractRepository
import com.example.eta.data.repository.DayClosingService
import com.example.eta.data.repository.GrowthService
import com.example.eta.data.repository.NoteRepository
import com.example.eta.data.repository.SleepLogRepository
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.PlanningPhaseService
import com.example.eta.data.repository.PointsRepository
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.ReevaluationService
import com.example.eta.data.repository.ReminderRepository
import com.example.eta.data.repository.RewardRepository
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskGroupService
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.data.repository.TaskReminderService
import com.example.eta.data.repository.VacationRepository
import com.example.eta.data.repository.WeekPlanningService
import com.example.eta.data.tutorial.TutorialSeed
import com.example.eta.ui.theme.DesignStore
import com.example.eta.ui.tutorial.TutorialStore
import kotlin.time.Clock

/**
 * Manual dependency wiring.
 *
 * Deliberately not a DI framework yet: the graph is small and a container keeps
 * the build free of another annotation processor. Swapping this for Hilt later
 * only touches construction sites, not the repositories themselves.
 */
class AppContainer(
    context: Context,
    /** Asked by the planning alarm, which stays quiet while the app is open. */
    isAppInForeground: () -> Boolean = { false },
    /**
     * A practice copy for the tutorial: the same wiring over a database that
     * exists only in memory, so nothing in it can reach the user's own — and no
     * alarm is ever laid down for a task that is not real.
     */
    val sandbox: Boolean = false,
    /** The tutorial's simulated day runs on a clock of its own. */
    val clock: Clock = Clock.System,
) {
    private val appContext = context.applicationContext

    private val database: EtaDatabase = if (sandbox) {
        Room.inMemoryDatabaseBuilder(appContext, EtaDatabase::class.java).build()
    } else {
        Room
            .databaseBuilder(
                context.applicationContext,
                EtaDatabase::class.java,
                EtaDatabase.NAME,
            )
            .addMigrations(*ETA_DATABASE_MIGRATIONS)
            .build()
    }

    val backupService: BackupService = BackupService(appContext)

    /** Which design is on; read by every activity before its first frame. */
    val designStore: DesignStore = DesignStore(appContext)

    /** The steps inside a task, and which of them a day has ticked off. */
    val subtaskRepository: SubtaskRepository = SubtaskRepository(database.subtaskDao(), clock = clock)

    val itemRepository: ItemRepository = ItemRepository(
        database.itemDao(),
        subtaskRepository,
        clock = clock,
    )

    val pointsRepository: PointsRepository = PointsRepository(database.pointsDao(), clock = clock)

    val planRepository: PlanRepository = PlanRepository(
        database.plannedBlockDao(),
        database.dayPlanDao(),
        clock = clock,
    )

    /** Folding one task into another: every merge comes down to this. */
    val subtaskGroupService: SubtaskGroupService = SubtaskGroupService(
        itemDao = database.itemDao(),
        planRepository = planRepository,
        subtaskRepository = subtaskRepository,
        clock = clock,
    )

    val dayClosingService: DayClosingService = DayClosingService(
        database.itemDao(),
        database.plannedBlockDao(),
        subtaskRepository,
        clock = clock,
    )

    val vacationRepository: VacationRepository = VacationRepository(
        database.vacationDao(),
        database.plannedBlockDao(),
        clock = clock,
    )

    val scheduleMaintenance: ScheduleMaintenance = ScheduleMaintenance(
        itemDao = database.itemDao(),
        planRepository = planRepository,
        vacationRepository = vacationRepository,
        clock = clock,
    )

    /** Editing the standing schedule from the Listen tab, and the settings' own two rows. */
    val recurringTaskService: RecurringTaskService = RecurringTaskService(
        itemDao = database.itemDao(),
        subtaskRepository = subtaskRepository,
        planRepository = planRepository,
        scheduleMaintenance = scheduleMaintenance,
        clock = clock,
    )

    /** Growth tasks: their order, and the one thing that makes them grow. */
    val growthService: GrowthService = GrowthService(
        itemDao = database.itemDao(),
        planRepository = planRepository,
        scheduleMaintenance = scheduleMaintenance,
        // Read when an evening is settled, by which time the repository below
        // has long been built.
        allowPartialGrowth = { setupRepository.find()?.allowPartialTimerGrowth == true },
        clock = clock,
    )

    /** The collisions the dashboard has been told to keep quiet about. */
    val conflictRepository: ConflictRepository = ConflictRepository(database.conflictDao(), clock = clock)

    val setupRepository: SetupRepository = SetupRepository(
        database.setupDao(),
        database.itemDao(),
        database.resetDao(),
        planRepository,
        vacationRepository,
        recurringTaskService,
        subtaskRepository,
        clock = clock,
    )

    val contractRepository: ContractRepository = ContractRepository(database.contractDao(), clock = clock)

    val weekPlanningService: WeekPlanningService = WeekPlanningService(
        pointsDao = database.pointsDao(),
        journalDao = database.journalDao(),
        itemRepository = itemRepository,
        setupRepository = setupRepository,
        clock = clock,
    )

    /** The Belohn-o-mat: long-term rewards, filled by what the evening harvests. */
    val rewardRepository: RewardRepository = RewardRepository(
        rewardDao = database.rewardDao(),
        itemDao = database.itemDao(),
        clock = clock,
    )

    /** A night changed by hand, and the planner's warnings waved away. */
    val nightService: NightService = NightService(
        dao = database.plannerExtrasDao(),
        setupRepository = setupRepository,
        planRepository = planRepository,
        clock = clock,
    )

    /** What the user said about getting up and going to bed — step 43. */
    val sleepLogRepository: SleepLogRepository = SleepLogRepository(database.trackingDao(), clock)

    /** The notes of the Notiz-Merger. */
    val noteRepository: NoteRepository = NoteRepository(database.trackingDao(), clock)

    val reevaluationService: ReevaluationService = ReevaluationService(
        itemDao = database.itemDao(),
        blockDao = database.plannedBlockDao(),
        journalDao = database.journalDao(),
        contractRepository = contractRepository,
        itemRepository = itemRepository,
        planRepository = planRepository,
        pointsRepository = pointsRepository,
        setupRepository = setupRepository,
        growthService = growthService,
        rewardRepository = rewardRepository,
        clock = clock,
        nightService = nightService,
        sleepLogRepository = sleepLogRepository,
    )

    val catchUpService: CatchUpService = CatchUpService(
        dayPlanDao = database.dayPlanDao(),
        blockDao = database.plannedBlockDao(),
        reevaluationService = reevaluationService,
        clock = clock,
    )

    /**
     * Google Calendar, read-only.
     *
     * Three pieces, kept apart on purpose: [GoogleAuth] knows about tokens and
     * nothing about calendars, [CalendarRepository] about rows and decisions and
     * nothing about Play Services, and [CalendarSyncService] is the only thing
     * that needs both. No client id or secret appears anywhere — Play Services
     * matches the app by package name and signing certificate against the Android
     * OAuth client in the Cloud project, so there is nothing here to leak.
     */
    private val googleAuth: GoogleAuth = GoogleAuth(appContext)

    val calendarRepository: CalendarRepository = CalendarRepository(
        dao = database.calendarDao(),
        api = GoogleCalendarApi(),
        clock = clock,
    )

    val calendarSyncService: CalendarSyncService = CalendarSyncService(
        auth = googleAuth,
        repository = calendarRepository,
    )

    val calendarImportService: CalendarImportService = CalendarImportService(
        itemRepository = itemRepository,
        planRepository = planRepository,
        calendarRepository = calendarRepository,
        dayClosingService = dayClosingService,
        weekPlanningService = weekPlanningService,
        clock = clock,
    )

    val planningPhaseService: PlanningPhaseService = PlanningPhaseService(
        setupRepository = setupRepository,
        planRepository = planRepository,
        weekPlanningService = weekPlanningService,
        clock = clock,
    )

    val planningAlarmCoordinator: PlanningAlarmCoordinator = PlanningAlarmCoordinator(
        context = appContext,
        phaseService = planningPhaseService,
        isAppInForeground = isAppInForeground,
        clock = clock,
        enabled = !sandbox,
    )

    /**
     * Announces the start of every planned block. Takes no foreground flag: a
     * task beginning is about the world, not about which screen is open.
     */
    val taskStartCoordinator: TaskStartCoordinator = TaskStartCoordinator(
        context = appContext,
        planRepository = planRepository,
        setupRepository = setupRepository,
        clock = clock,
        enabled = !sandbox,
    )

    val reminderRepository: ReminderRepository = ReminderRepository(database.reminderDao(), clock = clock)

    /** The reminders tasks owe, kept in step with the plan they are derived from. */
    val taskReminderService: TaskReminderService = TaskReminderService(
        dao = database.reminderDao(),
        planRepository = planRepository,
        clock = clock,
    )

    /** The Erinnerungen tab's one alarm, always aimed at the soonest reminder. */
    val reminderCoordinator: ReminderCoordinator = ReminderCoordinator(
        context = appContext,
        repository = reminderRepository,
        taskReminders = taskReminderService,
        clock = clock,
        enabled = !sandbox,
    )

    /** The evening's choices: where the wake alarm rings, and whether the wind-down hint shows. */
    val eveningStore: EveningStore = EveningStore(appContext)

    /** Off unless the setup says otherwise; rings at the setup's wake time, or leaves it to the clock app. */
    val wakeAlarmCoordinator: WakeAlarmCoordinator = WakeAlarmCoordinator(
        context = appContext,
        setupRepository = setupRepository,
        preferences = eveningStore,
        clock = clock,
        enabled = !sandbox,
        nightService = nightService,
    )

    /** Whether the tutorial is owed or was asked for; outside the database on purpose. */
    val tutorialStore: TutorialStore = TutorialStore(appContext)

    /**
     * Lays the tutorial's example day down. **Null on the real container**, so
     * there is no call that could write practice tasks into the user's data.
     */
    val tutorialSeed: TutorialSeed? = if (sandbox) {
        TutorialSeed(
            setupDao = database.setupDao(),
            itemDao = database.itemDao(),
            planRepository = planRepository,
            clock = clock,
        )
    } else {
        null
    }

    /** Throws the practice database away. Only a sandbox is ever closed. */
    fun close() {
        if (sandbox) database.close()
    }
}

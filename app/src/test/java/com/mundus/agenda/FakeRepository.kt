package com.mundus.agenda

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Deterministic fixture with the same shape as the desktop seed dataset
 * (`agenda-core::seed` at fixed day 2026-10-12, UTC+3): every smart list
 * populated, all task-field variants represented. Roborazzi tests render
 * real screens off `boardFlow()` with no native .so involved.
 */
class FakeRepository : BoardRepository, com.mundus.agenda.SyncApi {
    private val day = "2026-10-12"

    private fun task(id: String, title: String): Task =
        Task(id = id, title = title, createdAt = "${day}T09:00:00")

    private val tasks =
        listOf(
            task("dev-inbox-1", "Перечитать бриф по лендингу")
                .copy(notes = "Обратить внимание на секцию про ценообразование."),
            task("dev-inbox-2", "Записаться к стоматологу").copy(priority = 1),
            task("dev-inbox-3", "Идея: виджет погоды в сайдбаре"),
            task("dev-today-1", "Созвон по дизайну")
                .copy(
                    status = "todo",
                    isToday = true,
                    priority = 3,
                    tagIds = listOf("dev-tag-urgent", "dev-tag-focus"),
                    fuelCost = 35.0,
                ),
            task("dev-today-2", "Отправить инвойс клиенту")
                .copy(
                    status = "todo",
                    scheduledDate = day,
                    deadline = day,
                    billable = true,
                    price = 15000.0,
                    projectId = "dev-proj-release",
                ),
            task("dev-today-evening", "Почитать главу книги")
                .copy(status = "todo", isToday = true, isEvening = true, areaId = "dev-area-life"),
            task("dev-overdue-1", "Вернуть правки по макетам")
                .copy(
                    status = "todo",
                    scheduledDate = "2026-10-11",
                    priority = 3,
                    tagIds = listOf("dev-tag-urgent"),
                    fuelCost = 20.0,
                ),
            task("dev-checklist-1", "Подготовить демо для команды")
                .copy(
                    status = "todo",
                    scheduledDate = "2026-10-13",
                    projectId = "dev-proj-release",
                    headingId = "dev-head-prep",
                    checklist =
                        listOf(
                            ChecklistItem(isCompleted = true),
                            ChecklistItem(),
                            ChecklistItem(),
                        ),
                ),
            task("dev-week-2", "Ревью PR по навигации")
                .copy(
                    status = "started",
                    scheduledDate = "2026-10-14",
                    projectId = "dev-proj-release",
                    headingId = "dev-head-polish",
                    significance = 7,
                    fuelCost = 15.0,
                ),
            task("dev-week-3", "Оплатить интернет")
                .copy(
                    status = "todo",
                    deadline = "2026-10-15",
                    reminderDate = "2026-10-15T09:00:00",
                    projectId = "dev-proj-home",
                ),
            task("dev-week-4", "Купить подарок Маше")
                .copy(
                    status = "todo",
                    scheduledDate = "2026-10-16",
                    areaId = "dev-area-life",
                    tagIds = listOf("dev-tag-home"),
                ),
            task("dev-recurring-1", "Поливать цветы")
                .copy(
                    status = "todo",
                    scheduledDate = day,
                    areaId = "dev-area-life",
                    recurrence = RecurrenceRule(frequency = 1, interval = 1, daysOfWeek = listOf(1, 3, 5)),
                ),
            task("dev-next-week-1", "Запланировать отпуск")
                .copy(status = "todo", scheduledDate = "2026-10-20", priority = 2),
            task("dev-someday-1", "Собрать домашний кинотеатр")
                .copy(status = "deferred", isSomeday = true, projectId = "dev-proj-someday"),
            task("dev-done-today", "Утренняя планёрка")
                .copy(
                    status = "done",
                    isCompleted = true,
                    completedAt = "${day}T09:30:00",
                    scheduledDate = day,
                ),
            task("dev-done-yesterday", "Позвонить в банк")
                .copy(status = "done", isCompleted = true, completedAt = "2026-10-11T14:00:00"),
            task("dev-canceled-1", "Встреча с брокером")
                .copy(
                    status = "canceled",
                    isCancelled = true,
                    completedAt = "${day}T11:00:00",
                    scheduledDate = "2026-10-13",
                ),
            task("dev-trash-1", "Черновик: старый план переезда").copy(isTrashed = true),
        )
        .mapIndexed { i, t -> t.copy(sortOrder = i) }

    private val projects =
        listOf(
            Project("dev-proj-release", "Релиз Agenda 1.0", status = 0, deadline = "2026-10-22", sortOrder = 0, areaId = "dev-area-work"),
            Project("dev-proj-home", "Дом и быт", status = 0, sortOrder = 1, areaId = "dev-area-life"),
            Project("dev-proj-someday", "Путешествие в Японию", status = 1, sortOrder = 2, areaId = "dev-area-life"),
            Project("dev-proj-archive", "Старый сайт", status = 2, sortOrder = 3, areaId = "dev-area-work"),
        )

    private val tags =
        listOf(
            Tag("dev-tag-urgent", "срочно", "red"),
            Tag("dev-tag-focus", "фокус", "blue"),
            Tag("dev-tag-home", "дом", "green"),
        )

    // Mirrors the seed-parity golden for 2026-10-12 (Monday, UTC+3).
    private val board: Board =
        Board(
            todos = tasks,
            projects = projects,
            tags = tags,
            lists =
                mapOf(
                    SmartList.INBOX to
                        listOf("dev-inbox-1", "dev-inbox-2", "dev-inbox-3"),
                    SmartList.WEEK to
                        listOf(
                            "dev-overdue-1",
                            "dev-today-1",
                            "dev-today-2",
                            "dev-today-evening",
                            "dev-checklist-1",
                            "dev-week-2",
                            "dev-week-3",
                            "dev-week-4",
                            "dev-recurring-1",
                            "dev-done-today",
                        ),
                    SmartList.NEXT_WEEK to listOf("dev-next-week-1"),
                    SmartList.SOMEDAY to listOf("dev-someday-1"),
                    SmartList.LOGBOOK to listOf("dev-done-yesterday"),
                    SmartList.TRASH to listOf("dev-trash-1"),
                ).mapValues { (_, ids) -> ids.map { id -> tasks.first { it.id == id } } },
            projectTasks =
                mapOf(
                    "dev-proj-release" to
                        listOf("dev-today-2", "dev-checklist-1", "dev-week-2"),
                    "dev-proj-home" to listOf("dev-week-3"),
                    "dev-proj-someday" to listOf("dev-someday-1"),
                ).mapValues { (_, ids) -> ids.map { id -> tasks.first { it.id == id } } },
            dateViews =
                tasks.associate { t ->
                    val key = t.deadline ?: t.scheduledDate
                    t.id to
                        TaskDateView(
                            dateKey = key,
                            label =
                                when {
                                    key == null -> "Без даты"
                                    key == day -> "Сегодня"
                                    else ->
                                        java.time.LocalDate.parse(key).let {
                                            "${it.dayOfMonth} окт"
                                        }
                                },
                            overdue =
                                key != null && key < day && !t.isCompleted && !t.isCancelled &&
                                    t.status != "inbox" && !t.isSomeday,
                            deadlineLabel =
                                t.deadline?.let {
                                    java.time.LocalDate.parse(it.take(10)).let { d ->
                                        "${d.dayOfMonth} окт"
                                    }
                                },
                            reminderLabel =
                                t.reminderDate?.take(16)?.replace('T', ' '),
                            completedLabel =
                                t.completedAt?.take(16)?.replace('T', ' '),
                            createdLabel =
                                t.createdAt.take(16).replace('T', ' '),
                        )
                },
        )

    override fun boardFlow(): Flow<BoardState> = flowOf(BoardState.Ready(board))

    var quickEntryCalls = mutableListOf<String>()
    var createdTasks = mutableListOf<Task>()

    override suspend fun quickEntry(
        rawTitle: String,
        notes: String?,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): Task {
        quickEntryCalls += rawTitle
        return task("dev-created-${createdTasks.size + 1}", rawTitle)
            .copy(notes = notes, scheduledDate = selectedDate, isToday = week,
                isSomeday = someday, projectId = projectId)
            .also { createdTasks += it }
    }

    override suspend fun toggleComplete(task: Task) {}

    override suspend fun setStatus(task: Task, status: String) {}

    override suspend fun setScheduled(task: Task, date: String?) {}

    override suspend fun moveToWeek(task: Task) {}

    override suspend fun moveToProject(task: Task, projectId: String?) {}

    override suspend fun editTitleNotes(task: Task, title: String, notes: String?) {}

    override suspend fun setTrashed(task: Task, trashed: Boolean) {}

    var createdProjects = mutableListOf<Project>()

    override suspend fun createProject(): String {
        val id = "dev-proj-created-${createdProjects.size + 1}"
        createdProjects += Project(id = id, title = "Новый проект", sortOrder = projects.size + createdProjects.size + 1)
        return id
    }

    var renames = mutableListOf<Pair<String, String>>()

    override suspend fun renameProject(project: Project, title: String) {
        renames += project.id to title
    }

    var statusChanges = mutableListOf<Pair<String, Int>>()

    override suspend fun setProjectStatus(project: Project, status: Int) {
        statusChanges += project.id to status
    }

    var deletedProjects = mutableListOf<String>()

    override suspend fun deleteProject(project: Project) {
        deletedProjects += project.id
    }

    override suspend fun quickEntryPreview(
        rawTitle: String,
        selectedDate: String?,
        week: Boolean,
        someday: Boolean,
        projectId: String?,
        projectTouched: Boolean,
    ): QuickEntryPreview {
        // Enough of the real parser for screenshots: «завтра» resolves to
        // the next day and @Релиз… to the release project.
        var title = rawTitle
        var date = selectedDate
        if ("завтра" in rawTitle && selectedDate == null) {
            title = rawTitle.replace("завтра", "").trim()
            date = "2026-10-13"
        }
        var pid = projectId
        val mention = Regex("@(.+)$").find(rawTitle)?.groupValues?.get(1)?.trim()
        if (projectId == null && mention != null) {
            title = rawTitle.replace(Regex("@.+$"), "").trim()
            pid = projects.firstOrNull { it.title == mention }?.id
        }
        return QuickEntryPreview(
            title = title,
            scheduledDate = date,
            projectId = pid,
            isToday = week,
            isSomeday = someday,
        )
    }

    override suspend fun dayLabel(date: String) =
        when (date) {
            day -> "Сегодня"
            "2026-10-19" -> "Следующая"
            else -> java.time.LocalDate.parse(date).let { "${it.dayOfMonth} окт" }
        }

    override suspend fun nextMondayKey() = "2026-10-19"

    // -- SyncApi -------------------------------------------------------
    var snapshot = com.mundus.agenda.SyncSnapshot(
        running = true,
        transport = "iroh",
        localDevice = com.mundus.agenda.SyncLocalDevice("dev-phone", "Pixel"),
    )

    override suspend fun startSync(deviceName: String) {
        snapshot = snapshot.copy(running = true)
    }

    override suspend fun syncSnapshot() = snapshot

    override suspend fun showPairingCode() = "ticket-phone-abc123"

    override suspend fun ownIrohTicket() = "ticket-phone-abc123"

    var connectedCode: String? = null
    override suspend fun connectWithPairingCode(code: String): String {
        connectedCode = code
        snapshot = snapshot.copy(
            outgoingPairing = com.mundus.agenda.OutgoingPairing("pending")
        )
        return "pending"
    }

    var accepted = mutableListOf<String>()
    override suspend fun acceptPairing(deviceId: String) {
        accepted += deviceId
        snapshot = snapshot.copy(incomingPairingRequests = emptyList())
    }

    override suspend fun declinePairing(deviceId: String) {
        snapshot = snapshot.copy(incomingPairingRequests = emptyList())
    }

    override suspend fun cancelPairing() {
        snapshot = snapshot.copy(outgoingPairing = null)
    }

    override suspend fun disconnectPeer(deviceId: String) {
        snapshot = snapshot.copy(peers = snapshot.peers.filter { it.deviceId != deviceId })
    }
}

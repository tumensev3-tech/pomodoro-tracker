package com.drklo.pomodoro.data.repository

import androidx.room.withTransaction
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.db.ProjectDao
import com.drklo.pomodoro.data.db.ProjectEntity
import com.drklo.pomodoro.data.db.toDomain
import com.drklo.pomodoro.data.db.toEntity
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ProjectIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val db: AppDatabase) : ProjectStore {

    private val dao: ProjectDao = db.projectDao()

    /** What the carousel and the settings list show: everything the user can still pick. */
    override val projects: Flow<List<Project>> =
        dao.observeActive().map { list -> list.map(ProjectEntity::toDomain) }

    /** What the reports read: archived projects still own their history and must keep naming it. */
    override val allProjects: Flow<List<Project>> =
        dao.observeAll().map { list -> list.map(ProjectEntity::toDomain) }

    override suspend fun getById(id: Long): Project? = dao.getById(id)?.toDomain()

    /**
     * Inserts a new project at the end of the carousel; returns the generated id. Reading the last
     * position and inserting must be one operation: two adds racing over the same `maxOrderIndex`
     * produce two projects claiming the same slot, and `ORDER BY orderIndex, id` stops being a
     * stable answer to "what comes after what".
     */
    override suspend fun add(project: Project): Long = db.withTransaction {
        val nextOrder = dao.maxOrderIndex() + 1
        dao.insert(project.copy(id = 0, orderIndex = nextOrder).toEntity())
    }

    override suspend fun update(project: Project) = dao.update(project.toEntity())

    /**
     * Removes [project] from the carousel while keeping its history readable (F-R2-01). Refuses to
     * archive the last active project — an empty carousel leaves the main screen with nothing to
     * show and no way back (F-R5-03), so the rule is enforced here and not only by a greyed-out
     * button. Returns false when the request was refused.
     */
    override suspend fun archive(project: Project, now: Long): Boolean = db.withTransaction {
        if (dao.countActive() <= 1) return@withTransaction false
        dao.archive(project.id, now)
        true
    }

    /** Populates default projects on first launch. */
    /**
     * Creates the default projects, once ever. "Once" is recorded as a flag rather than inferred
     * from an empty table: an empty table is also what a user who cleared everything out sees, and
     * silently handing them "Работа / Учёба / Чтение" back on the next launch is not seeding, it is
     * overruling them. The check and the insert share a transaction so two starts cannot both pass
     * it. Returns true if it seeded now.
     */
    suspend fun ensureSeeded(alreadySeeded: Boolean): Boolean = db.withTransaction {
        if (alreadySeeded || dao.count() > 0) return@withTransaction false
        dao.insertAll(DefaultProjects.entities())
        true
    }
}

/**
 * Default projects created on first launch, mirroring the PRD example
 * (work / study / reading with their own presets). Names are editable by the user.
 */
private object DefaultProjects {
    fun entities(): List<ProjectEntity> = listOf(
        ProjectEntity(
            name = "Работа",
            focusMinutes = 30, shortBreakMinutes = 10,
            pomodorosPerSession = 8,
            pomodoroColor = 0xFFE53935.toInt(), breakColor = 0xFF43A047.toInt(),
            dailyGoal = 8,
            longBreakEnabled = true, longBreakMinutes = 20, longBreakInterval = 4,
            orderIndex = 0,
            iconName = ProjectIcon.COMPUTER.name
        ),
        ProjectEntity(
            name = "Учёба",
            focusMinutes = 25, shortBreakMinutes = 5,
            pomodorosPerSession = 3,
            pomodoroColor = 0xFF1E88E5.toInt(), breakColor = 0xFF43A047.toInt(),
            dailyGoal = 3,
            longBreakEnabled = false, longBreakMinutes = 15, longBreakInterval = 4,
            orderIndex = 1,
            iconName = ProjectIcon.STUDY.name
        ),
        ProjectEntity(
            name = "Чтение",
            focusMinutes = 45, shortBreakMinutes = 15,
            pomodorosPerSession = 2,
            pomodoroColor = 0xFF8E24AA.toInt(), breakColor = 0xFFFB8C00.toInt(),
            dailyGoal = 2,
            longBreakEnabled = false, longBreakMinutes = 30, longBreakInterval = 4,
            orderIndex = 2,
            iconName = ProjectIcon.BOOK.name
        )
    )
}

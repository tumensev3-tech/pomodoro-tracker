package com.drklo.pomodoro.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.model.ProjectIcon

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val pomodorosPerSession: Int,
    val pomodoroColor: Int,
    val breakColor: Int,
    val dailyGoal: Int,
    val longBreakEnabled: Boolean,
    val longBreakMinutes: Int,
    val longBreakInterval: Int,
    val orderIndex: Int,
    val iconName: String = ProjectIcon.NONE.name,
    /**
     * When the user removed the project, or null while it is active. Archived projects leave the
     * carousel and the settings list but keep their row, so the pomodoros they own stay attributable
     * in the reports — history must survive a deletion (owner's decision, F-R2-01).
     */
    val archivedAt: Long? = null
)

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    pomodorosPerSession = pomodorosPerSession,
    pomodoroColor = pomodoroColor,
    breakColor = breakColor,
    dailyGoal = dailyGoal,
    longBreakEnabled = longBreakEnabled,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    orderIndex = orderIndex,
    icon = ProjectIcon.fromName(iconName),
    archivedAt = archivedAt
)

fun Project.toEntity() = ProjectEntity(
    id = id,
    name = name,
    focusMinutes = focusMinutes,
    shortBreakMinutes = shortBreakMinutes,
    pomodorosPerSession = pomodorosPerSession,
    pomodoroColor = pomodoroColor,
    breakColor = breakColor,
    dailyGoal = dailyGoal,
    longBreakEnabled = longBreakEnabled,
    longBreakMinutes = longBreakMinutes,
    longBreakInterval = longBreakInterval,
    orderIndex = orderIndex,
    iconName = icon.name,
    archivedAt = archivedAt
)

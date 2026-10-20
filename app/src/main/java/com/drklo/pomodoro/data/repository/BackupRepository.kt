package com.drklo.pomodoro.data.repository

import androidx.room.withTransaction
import com.drklo.pomodoro.data.backup.ProjectBackup
import com.drklo.pomodoro.data.db.AppDatabase
import java.io.Reader
import java.io.Writer
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Moves projects, history and global Focuslot settings in and out of a file the user picked.
 *
 * Old backups that predate settings remain valid: when the optional settings block is absent, the
 * current install keeps its own settings. New backups restore the app as a whole instead of only
 * restoring the project list.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    private val projects = db.projectDao()
    private val dayStats = db.dayStatDao()
    private val log = db.pomodoroLogDao()

    /**
     * Writes every project and the last [ProjectBackup.HISTORY_DAYS] of its history to [out].
     *
     * Projects travel whole regardless of age — they are configuration, and a project last used two
     * years ago is still a project. History is windowed because it is the part that grows without
     * bound. Archived projects are included: their pomodoros are visible in the reports, so leaving
     * them out would mean the same reports show smaller numbers after a restore than before it.
     *
     * @return how many projects were written.
     */
    suspend fun export(out: Writer, nowMs: Long, today: LocalDate, appVersion: String): Int {
        val from = today.minusDays(ProjectBackup.HISTORY_DAYS)
        val fromKey = from.toString()
        val all = projects.getAll()

        val settings = settingsRepository.settings.first()
        ProjectBackup.Sink(out).use { sink ->
            sink.begin(
                exportedAtMs = nowMs,
                exportedOn = today,
                historyFrom = from,
                appVersion = appVersion,
                settings = settings
            )
            all.forEach { project ->
                sink.project(
                    ProjectBackup.Record(
                        project = project,
                        dayStats = dayStats.since(project.id, fromKey),
                        log = log.since(project.id, fromKey)
                    )
                )
            }
            sink.end()
        }
        return all.size
    }

    /**
     * Replaces everything with the contents of [source].
     *
     * Replace is the only mode there is. Merging would have to decide what makes two projects "the
     * same" across two installs, and every answer to that is either a new field in the file or a
     * guess based on the name — which breaks the moment someone renames something. A restore that
     * simply is the file is one the user can predict.
     *
     * The file is parsed and staged first, then written in a single transaction, so a file that
     * turns out to be broken half-way leaves the existing data untouched. Row ids are assigned by
     * this database, not taken from the file, and the history is re-pointed at them as it lands.
     *
     * @return how many projects were restored.
     */
    suspend fun import(source: Reader): Int {
        val staged = mutableListOf<ProjectBackup.Record>()
        // Reading first and writing second costs one pass in memory, bounded by the one-year window
        // the export applies. What it buys is that a truncated file cannot leave a half-restored
        // database — the transaction never opens.
        val header = ProjectBackup.read(source) { record -> staged += record }

        db.withTransaction {
            log.deleteAll()
            dayStats.deleteAll()
            projects.deleteAll()
            staged.forEach { record ->
                val newId = projects.insert(record.project.copy(id = 0))
                dayStats.insertAll(record.dayStats.map { it.copy(projectId = newId) })
                log.insertAll(record.log.map { it.copy(id = 0, projectId = newId) })
            }
        }
        header.settings?.let { settingsRepository.replaceAll(it) }
        return staged.size
    }
}

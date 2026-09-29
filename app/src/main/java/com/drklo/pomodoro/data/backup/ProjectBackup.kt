package com.drklo.pomodoro.data.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.MalformedJsonException
import com.drklo.pomodoro.data.db.DayStatEntity
import com.drklo.pomodoro.data.db.PomodoroLogEntity
import com.drklo.pomodoro.data.db.ProjectEntity
import com.drklo.pomodoro.data.db.toDomain
import com.drklo.pomodoro.data.model.ProjectIcon
import com.drklo.pomodoro.data.model.ProjectLimits
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The backup file format: projects, their per-day counters and their pomodoro log.
 *
 * Two tables travel rather than one, and the counters are carried verbatim instead of being counted
 * back out of the log. `day_stats` answers "how many today" for the timer's bullets and daily goal;
 * `pomodoro_log` answers everything the reports ask. Recomputing one from the other at import time
 * would put a second implementation of "how many pomodoros that day" in the codebase, and this
 * project has already been bitten by exactly that: the review's sharpest finding was that the
 * question had three different answers. Import restores; it does not calculate.
 *
 * Reading is streamed and handed out one project at a time, so neither side ever holds the whole
 * file. What keeps a project's own chunk small is the one-year window the export applies.
 */
object ProjectBackup {

    /** Bumped when the shape changes in a way an older reader could not survive. */
    const val SCHEMA_VERSION = 1

    /** How far back history travels. Older projects still travel; their old history does not. */
    const val HISTORY_DAYS = 365L

    private const val FIELD_SCHEMA = "schemaVersion"
    private const val FIELD_EXPORTED_AT = "exportedAt"
    private const val FIELD_EXPORTED_ON = "exportedOn"
    private const val FIELD_APP_VERSION = "appVersion"
    private const val FIELD_HISTORY_FROM = "historyFrom"
    private const val FIELD_PROJECTS = "projects"

    /** Why a file was refused. Each maps to a sentence the user can act on. */
    enum class Failure {
        /** Written by a newer version of the app than this one. */
        UNSUPPORTED_VERSION,

        /** Not this format at all, or truncated part-way. */
        MALFORMED,

        /** Well-formed, but a value is outside what the app accepts — see [ProjectLimits]. */
        INVALID_VALUE,

        /** No projects in it. Restoring would leave the app with none, which it cannot be in. */
        NO_PROJECTS
    }

    class BackupException(val failure: Failure, message: String) : IOException(message)

    /** Header values, read before any project so a bad file is refused early. */
    data class Header(
        val schemaVersion: Int,
        val exportedAtMs: Long,
        val appVersion: String?,
        val historyFrom: String?
    )

    /**
     * One project with the history that belongs to it. The `id` fields are all zero: identity comes
     * from the database that receives them, not from the file, so a restore never depends on the
     * row numbers the exporting install happened to use.
     */
    data class Record(
        val project: ProjectEntity,
        val dayStats: List<DayStatEntity>,
        val log: List<PomodoroLogEntity>
    )

    /** Writes the file. Closing it finishes the JSON; a half-written file is never a valid one. */
    class Sink(out: Writer, private val json: JsonWriter = JsonWriter(out)) : Closeable {

        fun begin(exportedAtMs: Long, exportedOn: LocalDate, historyFrom: LocalDate, appVersion: String) {
            json.setIndent("  ")
            json.beginObject()
            json.name(FIELD_SCHEMA).value(SCHEMA_VERSION.toLong())
            json.name(FIELD_EXPORTED_AT).value(exportedAtMs)
            json.name(FIELD_EXPORTED_ON).value(exportedOn.toString())
            json.name(FIELD_APP_VERSION).value(appVersion)
            json.name(FIELD_HISTORY_FROM).value(historyFrom.toString())
            json.name(FIELD_PROJECTS).beginArray()
        }

        fun project(record: Record) {
            json.beginObject()
            with(record.project) {
                json.name("name").value(name)
                json.name("focusMinutes").value(focusMinutes.toLong())
                json.name("shortBreakMinutes").value(shortBreakMinutes.toLong())
                json.name("pomodorosPerSession").value(pomodorosPerSession.toLong())
                json.name("pomodoroColor").value(pomodoroColor.toLong())
                json.name("breakColor").value(breakColor.toLong())
                json.name("dailyGoal").value(dailyGoal.toLong())
                json.name("longBreakEnabled").value(longBreakEnabled)
                json.name("longBreakMinutes").value(longBreakMinutes.toLong())
                json.name("longBreakInterval").value(longBreakInterval.toLong())
                json.name("orderIndex").value(orderIndex.toLong())
                json.name("iconName").value(iconName)
                // Carried so an archived project comes back archived: hidden from the list, still
                // named and coloured in the reports its pomodoros appear in.
                json.name("archivedAt").value(archivedAt)
            }
            json.name("dayStats").beginArray()
            record.dayStats.forEach { stat ->
                json.beginObject()
                json.name("dayKey").value(stat.dayKey)
                json.name("completed").value(stat.completedPomodoros.toLong())
                json.endObject()
            }
            json.endArray()
            json.name("log").beginArray()
            record.log.forEach { entry ->
                json.beginObject()
                json.name("start").value(entry.startEpochMs)
                json.name("end").value(entry.endEpochMs)
                json.name("durationSeconds").value(entry.durationSeconds.toLong())
                json.name("dayKey").value(entry.dayKey)
                json.endObject()
            }
            json.endArray()
            json.endObject()
        }

        fun end() {
            json.endArray()
            json.endObject()
        }

        override fun close() = json.close()
    }

    /**
     * Reads the file, handing each project to [onProject] as it arrives.
     *
     * Throws [BackupException] on anything unacceptable. Nothing is written by this function — the
     * caller decides what a record means, and can abandon the whole thing without having touched
     * the database.
     */
    @Suppress("ThrowsCount")
    suspend fun read(source: Reader, onProject: suspend (Record) -> Unit): Header = try {
        readOrThrow(source, onProject)
    } catch (e: MalformedJsonException) {
        // Not valid JSON at all. Without this the caller would report "the file could not be read",
        // which points at the storage rather than at the file, and sends the user looking in the
        // wrong place.
        throw BackupException(Failure.MALFORMED, "the file is not valid JSON").initCause(e) as BackupException
    } catch (e: EOFException) {
        // Truncated: an export interrupted part-way, or a partial copy out of cloud storage.
        throw BackupException(Failure.MALFORMED, "the file ends part-way through").initCause(e) as BackupException
    } catch (e: IllegalStateException) {
        // JsonReader's own complaint when the shape is not what the position calls for.
        throw BackupException(Failure.MALFORMED, "the file is not shaped like a backup").initCause(e)
            as BackupException
    }

    @Suppress("ThrowsCount")
    private suspend fun readOrThrow(source: Reader, onProject: suspend (Record) -> Unit): Header {
        var header: Header? = null
        var projectCount = 0
        JsonReader(source).use { json ->
            json.expect(JsonToken.BEGIN_OBJECT) { json.beginObject() }
            while (json.hasNext()) {
                when (val field = json.nextName()) {
                    FIELD_SCHEMA -> header = Header(json.readSupportedVersion(field), 0, null, null)

                    FIELD_EXPORTED_AT -> header = header.orMalformed().copy(exportedAtMs = json.nextLong(field))
                    FIELD_APP_VERSION -> header = header.orMalformed().copy(appVersion = json.nextString(field))
                    FIELD_HISTORY_FROM -> header = header.orMalformed().copy(historyFrom = json.nextString(field))

                    FIELD_PROJECTS -> {
                        // The version is checked first because it is written first. A file that
                        // reaches its projects without one is not this format.
                        header.orMalformed()
                        projectCount += json.readProjects(onProject)
                    }

                    else -> json.skipValue()
                }
            }
            json.endObject()
        }

        val complete = header.orMalformed()
        if (projectCount == 0) {
            throw BackupException(Failure.NO_PROJECTS, "the file describes no projects")
        }
        return complete
    }

    private fun JsonReader.readSupportedVersion(field: String): Int {
        val version = nextInt(field)
        if (version != SCHEMA_VERSION) {
            throw BackupException(
                Failure.UNSUPPORTED_VERSION,
                "schemaVersion $version, this build reads $SCHEMA_VERSION"
            )
        }
        return version
    }

    private suspend fun JsonReader.readProjects(onProject: suspend (Record) -> Unit): Int {
        var count = 0
        expect(JsonToken.BEGIN_ARRAY) { beginArray() }
        while (hasNext()) {
            onProject(readRecord())
            count++
        }
        endArray()
        return count
    }

    /** A field the format does not work without. Absent means this is not one of our files. */
    private fun <T : Any> T?.required(what: String): T =
        this ?: throw BackupException(Failure.MALFORMED, what)

    private fun Header?.orMalformed(): Header =
        this ?: throw BackupException(Failure.MALFORMED, "no $FIELD_SCHEMA before the rest of the file")

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun JsonReader.readRecord(): Record {
        var name: String? = null
        var focus = -1
        var shortBreak = -1
        var perSession = -1
        var pomodoroColor: Int? = null
        var breakColor: Int? = null
        var dailyGoal = -1
        var longBreakEnabled = false
        var longBreakMinutes = -1
        var longBreakInterval = -1
        var orderIndex = 0
        var iconName = ProjectIcon.NONE.name
        var archivedAt: Long? = null
        val dayStats = mutableListOf<DayStatEntity>()
        val log = mutableListOf<PomodoroLogEntity>()

        expect(JsonToken.BEGIN_OBJECT) { beginObject() }
        while (hasNext()) {
            when (val field = nextName()) {
                "name" -> name = nextString(field)
                "focusMinutes" -> focus = nextInt(field)
                "shortBreakMinutes" -> shortBreak = nextInt(field)
                "pomodorosPerSession" -> perSession = nextInt(field)
                "pomodoroColor" -> pomodoroColor = nextInt(field)
                "breakColor" -> breakColor = nextInt(field)
                "dailyGoal" -> dailyGoal = nextInt(field)
                "longBreakEnabled" -> longBreakEnabled = nextBooleanOrFail(field)
                "longBreakMinutes" -> longBreakMinutes = nextInt(field)
                "longBreakInterval" -> longBreakInterval = nextInt(field)
                "orderIndex" -> orderIndex = nextInt(field)
                "iconName" -> iconName = ProjectIcon.fromName(nextString(field)).name
                "archivedAt" -> archivedAt = if (peek() == JsonToken.NULL) {
                    nextNull()
                    null
                } else {
                    nextLong(field)
                }

                "dayStats" -> readArray { dayStats += readDayStat() }
                "log" -> readArray { log += readLogEntry() }
                else -> skipValue()
            }
        }
        endObject()

        val entity = ProjectEntity(
            id = 0,
            name = name.required("a project without a name"),
            focusMinutes = focus,
            shortBreakMinutes = shortBreak,
            pomodorosPerSession = perSession,
            pomodoroColor = pomodoroColor.required("project \"$name\" has no colour"),
            breakColor = breakColor.required("project \"$name\" has no break colour"),
            dailyGoal = dailyGoal,
            longBreakEnabled = longBreakEnabled,
            longBreakMinutes = longBreakMinutes,
            longBreakInterval = longBreakInterval,
            orderIndex = orderIndex,
            iconName = iconName,
            archivedAt = archivedAt
        )
        if (!ProjectLimits.accepts(entity.toDomain())) {
            throw BackupException(Failure.INVALID_VALUE, "project \"$name\" is outside the accepted ranges")
        }
        return Record(entity, dayStats, log)
    }

    private fun JsonReader.readDayStat(): DayStatEntity {
        var dayKey: String? = null
        var completed = -1
        expect(JsonToken.BEGIN_OBJECT) { beginObject() }
        while (hasNext()) {
            when (val field = nextName()) {
                "dayKey" -> dayKey = nextString(field)
                "completed" -> completed = nextInt(field)
                else -> skipValue()
            }
        }
        endObject()
        val key = dayKey.validDayKey()
        if (completed < 0) {
            throw BackupException(Failure.INVALID_VALUE, "$key has a negative count")
        }
        return DayStatEntity(projectId = 0, dayKey = key, completedPomodoros = completed)
    }

    private fun JsonReader.readLogEntry(): PomodoroLogEntity {
        var start = -1L
        var end = -1L
        var duration = -1
        var dayKey: String? = null
        expect(JsonToken.BEGIN_OBJECT) { beginObject() }
        while (hasNext()) {
            when (val field = nextName()) {
                "start" -> start = nextLong(field)
                "end" -> end = nextLong(field)
                "durationSeconds" -> duration = nextInt(field)
                "dayKey" -> dayKey = nextString(field)
                else -> skipValue()
            }
        }
        endObject()
        val key = dayKey.validDayKey()
        // A pomodoro that ended before it started would draw backwards in the reports; a
        // zero-length one would draw nothing and still be counted.
        if (start < 0 || end < start || duration <= 0) {
            throw BackupException(Failure.INVALID_VALUE, "a pomodoro on $key has impossible times")
        }
        return PomodoroLogEntity(
            id = 0,
            projectId = 0,
            startEpochMs = start,
            endEpochMs = end,
            durationSeconds = duration,
            dayKey = key
        )
    }

    /** Day keys are ISO dates and are compared as strings all over the reports, so shape matters. */
    private fun String?.validDayKey(): String {
        val raw = this ?: throw BackupException(Failure.MALFORMED, "a history row without a day")
        return try {
            LocalDate.parse(raw).toString()
        } catch (e: DateTimeParseException) {
            throw BackupException(Failure.INVALID_VALUE, "\"$raw\" is not a date").initCause(e) as BackupException
        }
    }
}

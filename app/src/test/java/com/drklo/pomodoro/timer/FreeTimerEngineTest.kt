package com.drklo.pomodoro.timer

import com.drklo.pomodoro.data.repository.ActivitySessionKind
import com.drklo.pomodoro.data.repository.ActivitySessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreeTimerEngineTest {

    private data class Record(
        val name: String,
        val kind: ActivitySessionKind,
        val projectId: Long?,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val durationSeconds: Int,
        val dayKey: String
    )

    private class RecordingStore : ActivitySessionStore {
        val records = mutableListOf<Record>()

        override suspend fun record(
            name: String,
            kind: ActivitySessionKind,
            projectId: Long?,
            startEpochMs: Long,
            endEpochMs: Long,
            durationSeconds: Int,
            dayKey: String
        ) {
            records += Record(
                name = name,
                kind = kind,
                projectId = projectId,
                startEpochMs = startEpochMs,
                endEpochMs = endEpochMs,
                durationSeconds = durationSeconds,
                dayKey = dayKey
            )
        }
    }

    @Test
    fun `name is required and stop records real elapsed time`() = runTest {
        val store = RecordingStore()
        val time = FakeTimeSource(testScheduler)
        val engine = FreeTimerEngine(
            settingsSource = FakeSettings(),
            sessions = store,
            time = time,
            scope = backgroundScope
        )
        runCurrent()

        assertFalse(engine.start("   "))
        assertTrue(engine.start("  Cooking  "))
        assertEquals("Cooking", engine.state.value.name)

        advanceTimeBy(65_000L)
        runCurrent()
        assertEquals(65, engine.state.value.elapsedSeconds)

        assertTrue(engine.stop())
        runCurrent()

        assertFalse(engine.state.value.running)
        val record = store.records.single()
        assertEquals("Cooking", record.name)
        assertEquals(ActivitySessionKind.FREE, record.kind)
        assertEquals(null, record.projectId)
        assertEquals(65, record.durationSeconds)
        assertEquals(65_000L, record.endEpochMs - record.startEpochMs)
        assertEquals("2026-05-13", record.dayKey)
    }
}

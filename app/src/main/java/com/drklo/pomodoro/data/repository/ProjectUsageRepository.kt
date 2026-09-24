package com.drklo.pomodoro.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.projectUsageDataStore by preferencesDataStore(name = "project_usage")

/**
 * Lightweight rolling history used only to rank the quick activity picker.
 *
 * Each entry is one manual transition from IDLE to RUNNING. Resuming a paused timer and automatic
 * phase changes are deliberately not recorded, so the ranking answers "what activity do I choose
 * most often?" rather than "which timer happens to have the most cycles?".
 */
class ProjectUsageRepository(private val context: Context) {

    val recentStartCounts: Flow<Map<Long, Int>> = context.projectUsageDataStore.data.map { prefs ->
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        prefs[Keys.STARTS].orEmpty()
            .mapNotNull(::decode)
            .filter { it.timestampMs >= cutoff }
            .groupingBy { it.projectId }
            .eachCount()
    }

    suspend fun recordManualStart(projectId: Long) {
        val now = System.currentTimeMillis()
        val cutoff = now - WINDOW_MS
        context.projectUsageDataStore.edit { prefs ->
            val kept = prefs[Keys.STARTS].orEmpty()
                .mapNotNull(::decode)
                .filter { it.timestampMs >= cutoff }
                .mapTo(linkedSetOf()) { encode(it.timestampMs, it.projectId) }

            // Millisecond timestamps make collisions practically impossible; adding projectId keeps
            // simultaneous records for different projects distinct as well.
            kept += encode(now, projectId)
            prefs[Keys.STARTS] = kept
        }
    }

    private data class StartEntry(val timestampMs: Long, val projectId: Long)

    private fun decode(raw: String): StartEntry? {
        val separator = raw.indexOf(':')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val timestamp = raw.substring(0, separator).toLongOrNull() ?: return null
        val projectId = raw.substring(separator + 1).toLongOrNull() ?: return null
        return StartEntry(timestamp, projectId)
    }

    private fun encode(timestampMs: Long, projectId: Long): String = "$timestampMs:$projectId"

    private object Keys {
        val STARTS = stringSetPreferencesKey("manual_starts")
    }

    private companion object {
        const val WINDOW_DAYS = 30L
        const val WINDOW_MS = WINDOW_DAYS * 24L * 60L * 60L * 1000L
    }
}

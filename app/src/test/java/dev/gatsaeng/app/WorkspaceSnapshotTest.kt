package dev.gatsaeng.app

import dev.gatsaeng.app.data.WorkspaceSnapshot
import dev.gatsaeng.app.model.Routine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class WorkspaceSnapshotTest {
    private val shared = Routine(id = "00000000-0000-4000-8000-000000000001", title = "운동",
        createdOn = LocalDate.of(2026, 1, 1), completedDates = setOf(LocalDate.of(2026, 9, 30)),
        lastRemindedDate = LocalDate.of(2026, 10, 1))
    private val personal = Routine(id = "00000000-0000-4000-8000-000000000002", title = "개인 루틴")

    @Test fun sharedSettingsExcludePersonalHistory() {
        val value = WorkspaceSnapshot.sharedSettings(shared)
        assertFalse(value.has("completedDates"))
        assertFalse(value.has("lastRemindedDate"))
    }

    @Test fun mergeKeepsPrivateRoutineAndOnlyOwnCompletion() {
        val snapshot = JSONObject().put("routines", JSONArray().put(WorkspaceSnapshot.sharedSettings(shared)))
            .put("completions", JSONArray()
                .put(JSONObject().put("routineId", shared.id).put("personId", "me").put("date", "2026-10-01"))
                .put(JSONObject().put("routineId", shared.id).put("personId", "friend").put("date", "2026-09-29")))
        val merged = WorkspaceSnapshot.merge(listOf(shared, personal), setOf(shared.id), snapshot.toString(), "me")
        val ownShared = merged.routines.first { it.id == shared.id }
        assertEquals(setOf(LocalDate.of(2026, 10, 1)), ownShared.completedDates)
        assertEquals(LocalDate.of(2026, 10, 1), ownShared.lastRemindedDate)
        assertEquals(personal, merged.routines.first { it.id == personal.id })
    }

    @Test fun deletedSharedRoutineDoesNotDeletePrivateRoutine() {
        val snapshot = JSONObject().put("routines", JSONArray()).put("completions", JSONArray())
        val merged = WorkspaceSnapshot.merge(listOf(shared, personal), setOf(shared.id), snapshot.toString(), "me")
        assertEquals(listOf(personal), merged.routines)
    }
}

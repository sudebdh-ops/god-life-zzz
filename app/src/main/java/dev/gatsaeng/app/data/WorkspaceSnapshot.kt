package dev.gatsaeng.app.data

import dev.gatsaeng.app.model.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class WorkspaceMerge(val routines: List<Routine>, val sharedIds: Set<String>)

object WorkspaceSnapshot {
    // A remote routine contains shared settings only. Dates belong to an individual member.
    fun sharedSettings(routine: Routine): JSONObject = JSONObject().apply {
        put("id", routine.id)
        put("title", routine.title)
        put("note", routine.note)
        put("days", JSONArray(routine.days.sortedBy { it.value }.map { it.value }))
        put("hour", routine.hour)
        put("minute", routine.minute)
        put("reminderEnabled", routine.reminderEnabled)
        put("active", routine.active)
        put("createdOn", routine.createdOn.toString())
    }

    fun merge(local: List<Routine>, oldSharedIds: Set<String>, raw: String, personId: String): WorkspaceMerge {
        require(raw.length <= 2_000_000) { "공유 공간의 응답이 너무 큽니다." }
        val snapshot = JSONObject(raw)
        val settings = snapshot.getJSONArray("routines")
        val completions = snapshot.getJSONArray("completions")
        val backup = JSONObject().put("version", 1).put("routines", JSONArray().apply {
            for (index in 0 until settings.length()) {
                put(JSONObject(settings.getJSONObject(index).toString()).apply {
                    put("completedDates", JSONArray())
                    put("lastRemindedDate", JSONObject.NULL)
                })
            }
        })
        val shared = RoutineCodec.decode(backup.toString())
        val sharedIds = shared.map { it.id }.toSet()
        require(sharedIds.size == shared.size) { "공유 루틴 ID가 중복됐어요." }
        val ownCompletions = sharedIds.associateWith { mutableSetOf<LocalDate>() }
        for (index in 0 until completions.length()) {
            val completion = completions.getJSONObject(index)
            if (completion.getString("personId") != personId) continue
            ownCompletions[completion.getString("routineId")]?.add(LocalDate.parse(completion.getString("date")))
        }
        val previous = local.associateBy { it.id }
        val projected = shared.map { routine ->
            val old = previous[routine.id]
            val sameReminder = old != null && old.hour == routine.hour && old.minute == routine.minute &&
                old.reminderEnabled == routine.reminderEnabled && old.active == routine.active && old.days == routine.days
            routine.copy(
                completedDates = ownCompletions.getValue(routine.id),
                lastRemindedDate = if (sameReminder) old?.lastRemindedDate else null,
            )
        }
        val personal = local.filter { it.id !in oldSharedIds && it.id !in sharedIds }
        require(personal.size + projected.size <= 500) { "공유 후 루틴이 500개를 넘어요." }
        return WorkspaceMerge(personal + projected, sharedIds)
    }
}

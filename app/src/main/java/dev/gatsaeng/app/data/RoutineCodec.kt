package dev.gatsaeng.app.data

import dev.gatsaeng.app.model.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

object RoutineCodec {
    private const val VERSION = 1

    fun encode(routines: List<Routine>): String = JSONObject().apply {
        put("version", VERSION)
        put("routines", JSONArray().apply {
            routines.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("note", r.note)
                    put("days", JSONArray(r.days.sortedBy { it.value }.map { it.value }))
                    put("hour", r.hour)
                    put("minute", r.minute)
                    put("reminderEnabled", r.reminderEnabled)
                    put("active", r.active)
                    put("completedDates", JSONArray(r.completedDates.sorted().map { it.toString() }))
                    put("lastRemindedDate", r.lastRemindedDate?.toString() ?: JSONObject.NULL)
                    put("createdOn", r.createdOn.toString())
                })
            }
        })
    }.toString(2)

    fun decode(text: String): List<Routine> {
        require(text.length <= 2_000_000) { "백업 파일이 너무 큽니다." }
        val root = JSONObject(text)
        require(root.getInt("version") == VERSION) { "지원하지 않는 백업 버전입니다." }
        val items = root.getJSONArray("routines")
        require(items.length() <= 500) { "루틴은 최대 500개까지 복원할 수 있어요." }
        val routines = (0 until items.length()).map { i ->
            val obj = items.getJSONObject(i)
            val days = obj.getJSONArray("days")
            val completions = obj.getJSONArray("completedDates")
            Routine(
                id = obj.getString("id"),
                title = obj.getString("title"),
                note = obj.optString("note", ""),
                days = (0 until days.length()).map { DayOfWeek.of(days.getInt(it)) }.toSet(),
                hour = obj.getInt("hour"),
                minute = obj.getInt("minute"),
                reminderEnabled = obj.getBoolean("reminderEnabled"),
                active = obj.getBoolean("active"),
                completedDates = (0 until completions.length()).map { LocalDate.parse(completions.getString(it)) }.toSet(),
                lastRemindedDate = if (obj.isNull("lastRemindedDate")) null else LocalDate.parse(obj.getString("lastRemindedDate")),
                createdOn = LocalDate.parse(obj.getString("createdOn")),
            )
        }
        require(routines.map { it.id }.distinct().size == routines.size) { "중복된 루틴 ID가 있어요." }
        return routines
    }
}

package dev.gatsaeng.app.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val hour: Int = 8,
    val minute: Int = 0,
    val reminderEnabled: Boolean = true,
    val active: Boolean = true,
    val completedDates: Set<LocalDate> = emptySet(),
    val lastRemindedDate: LocalDate? = null,
    val createdOn: LocalDate = LocalDate.now(),
) {
    init {
        require(title.isNotBlank() && title.length <= 80) { "루틴 이름은 1~80자로 입력해주세요." }
        require(note.length <= 500) { "메모는 500자까지 입력할 수 있어요." }
        require(days.isNotEmpty()) { "반복 요일을 하나 이상 선택해주세요." }
        require(hour in 0..23 && minute in 0..59) { "알림 시간을 확인해주세요." }
        require(runCatching { UUID.fromString(id) }.isSuccess) { "잘못된 루틴 ID입니다." }
    }

    fun isDue(date: LocalDate): Boolean = active && date >= createdOn && date.dayOfWeek in days
    fun isCompleted(date: LocalDate): Boolean = date in completedDates
    val timeText: String get() = "%02d:%02d".format(hour, minute)

    fun nextReminder(now: ZonedDateTime): ZonedDateTime? {
        if (!active || !reminderEnabled) return null
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (!isDue(date) || isCompleted(date) || lastRemindedDate == date) continue
            // Local-time scheduling follows the phone's zone, including DST transitions.
            val candidate = date.atTime(LocalTime.of(hour, minute)).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    fun streak(today: LocalDate): Int {
        var date = today
        var count = 0
        // An unfinished today does not erase the streak earned through yesterday.
        if (!isDue(date) || !isCompleted(date)) date = date.minusDays(1)
        while (date >= createdOn) {
            if (date.dayOfWeek in days) {
                if (!isCompleted(date)) break
                count++
            }
            date = date.minusDays(1)
        }
        return count
    }
}

package dev.gatsaeng.app

import dev.gatsaeng.app.model.Routine
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineTest {
    private val today = LocalDate.of(2026, 10, 1) // Thursday
    private val zone = ZoneId.of("Asia/Seoul")
    private fun routine() = Routine(title = "산책", hour = 8, createdOn = today.minusDays(30))
    private fun now(hour: Int, minute: Int = 0) = today.atTime(hour, minute).atZone(zone)

    @Test fun upcomingAlarmUsesToday() {
        assertEquals(now(8), routine().nextReminder(now(7)))
    }
    @Test fun elapsedAlarmUsesTomorrow() {
        assertEquals(today.plusDays(1).atTime(8, 0).atZone(zone), routine().nextReminder(now(8)))
    }
    @Test fun completedTodayDoesNotRemindAgain() {
        val r = routine().copy(completedDates = setOf(today))
        assertEquals(today.plusDays(1), r.nextReminder(now(7))!!.toLocalDate())
    }
    @Test fun alreadyRemindedTodayDoesNotRepeat() {
        val r = routine().copy(lastRemindedDate = today)
        assertEquals(today.plusDays(1), r.nextReminder(now(7))!!.toLocalDate())
    }
    @Test fun pausedAndSilentRoutinesHaveNoAlarm() {
        assertNull(routine().copy(active = false).nextReminder(now(7)))
        assertNull(routine().copy(reminderEnabled = false).nextReminder(now(7)))
    }
    @Test fun weeklyRoutineSchedulesNextSelectedWeekday() {
        val r = routine().copy(days = setOf(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 5), r.nextReminder(now(7))!!.toLocalDate())
    }
    @Test fun streakIgnoresUnfinishedTodayButStopsAtMissedScheduledDay() {
        val r = routine().copy(completedDates = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4)))
        assertEquals(2, r.streak(today))
        assertEquals(3, r.copy(completedDates = r.completedDates + today).streak(today))
    }
    @Test fun streakSkipsNonScheduledDays() {
        val monday = LocalDate.of(2026, 10, 5)
        val friday = LocalDate.of(2026, 10, 2)
        val r = routine().copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), completedDates = setOf(friday))
        assertEquals(1, r.streak(monday))
    }
    @Test fun newRoutineCannotBeDueBeforeCreation() {
        assertFalse(routine().copy(createdOn = today).isDue(today.minusDays(1)))
    }
    @Test fun daylightSavingGapStillProducesAFutureValidInstant() {
        val ny = ZoneId.of("America/New_York")
        val before = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny)
        val r = routine().copy(hour = 2, minute = 30, createdOn = before.toLocalDate())
        val next = r.nextReminder(before)!!
        assertEquals(before.toLocalDate(), next.toLocalDate())
        assertTrue(next.isAfter(before))
        assertEquals(3, next.hour)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyWeekdaysRejected() {
        routine().copy(days = emptySet())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTimeRejected() {
        routine().copy(hour = 24)
    }
}

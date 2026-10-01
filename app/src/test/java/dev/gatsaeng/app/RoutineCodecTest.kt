package dev.gatsaeng.app

import dev.gatsaeng.app.data.RoutineCodec
import dev.gatsaeng.app.model.Routine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RoutineCodecTest {
    @Test fun roundTripPreservesAllFieldsAndKoreanText() {
        val date = LocalDate.of(2026, 10, 1)
        val r = Routine(title = "god life zzz", note = "책 10쪽 📖\n꾸준히!", completedDates = setOf(date), lastRemindedDate = date, createdOn = date)
        assertEquals(listOf(r), RoutineCodec.decode(RoutineCodec.encode(listOf(r))))
    }
    @Test fun emptyBackupRemainsEmpty() {
        assertEquals(emptyList<Routine>(), RoutineCodec.decode(RoutineCodec.encode(emptyList())))
    }
    @Test(expected = IllegalArgumentException::class) fun unknownVersionRejected() {
        RoutineCodec.decode("{\"version\":99,\"routines\":[]}")
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() {
        val r = Routine(title = "산책")
        RoutineCodec.decode(RoutineCodec.encode(listOf(r, r)))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidWeekdayRejected() {
        val r = Routine(title = "산책")
        val root = JSONObject(RoutineCodec.encode(listOf(r)))
        root.getJSONArray("routines").getJSONObject(0).getJSONArray("days").put(0, 99)
        try { RoutineCodec.decode(root.toString()) } catch (error: java.time.DateTimeException) { throw IllegalArgumentException(error) }
    }
}

package com.github.codeworkscreativehub.mlauncher.mooncat.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProductivityTest {
    // 2026-11-23 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun daytimeScheduleCoversOnlyItsDaysAndHours() {
        val weekdays = FocusSchedule(1, setOf(1, 2, 3, 4, 5), 9 * 60, 13 * 60)
        assertTrue(Productivity.isActive(weekdays, at(23, 9)))
        assertTrue(Productivity.isActive(weekdays, at(27, 12, 59)))
        assertFalse(Productivity.isActive(weekdays, at(23, 13)))
        assertFalse(Productivity.isActive(weekdays, at(23, 8, 59)))
        assertFalse(Productivity.isActive(weekdays, at(28, 10))) // Saturday
    }

    @Test
    fun overnightScheduleBelongsToTheDayItStarts() {
        val fridayNight = FocusSchedule(1, setOf(5), 22 * 60, 6 * 60)
        assertTrue(Productivity.isActive(fridayNight, at(27, 23)))
        assertTrue(Productivity.isActive(fridayNight, at(28, 5, 59))) // Saturday morning
        assertFalse(Productivity.isActive(fridayNight, at(28, 6)))
        assertFalse(Productivity.isActive(fridayNight, at(27, 5))) // Friday morning belongs to Thursday
    }

    @Test
    fun invalidSchedulesNeverActivate() {
        assertFalse(Productivity.isActive(FocusSchedule(1, emptySet(), 0, 60), at(23, 0, 30)))
        assertFalse(Productivity.isActive(FocusSchedule(1, setOf(8), 0, 60), at(23, 0, 30)))
        assertFalse(Productivity.isActive(FocusSchedule(1, setOf(1), 60, 60), at(23, 1)))
        assertFalse(Productivity.isValid(ProductivityData(schedules = listOf(FocusSchedule(1, setOf(1), 0, 1440)))))
    }

    @Test
    fun onlyChosenAppsArePausedAndOnlyWhileFocusIsActive() {
        val data = ProductivityData(focusApps = setOf("com.example.social"), sessionEndMillis = 2_000)
        assertTrue(Productivity.isPaused(data, "com.example.social", at(28, 10), 1_000))
        assertFalse(Productivity.isPaused(data, "com.example.maps", at(28, 10), 1_000))
        assertFalse(Productivity.isPaused(data, "com.example.social", at(28, 10), 2_000)) // session ended
    }

    @Test
    fun tasksAreCleanedLimitedAndSorted() {
        var data = ProductivityData()
        data = Productivity.addTask(data, "  buy   milk ", null, 10)
        data = Productivity.addTask(data, "   ", null, 11)
        data = Productivity.addTask(data, "pay rent", 20_000, 10) // id collision is resolved
        assertEquals(listOf("buy milk", "pay rent"), data.tasks.map { it.text })
        assertEquals(2, data.tasks.map { it.id }.toSet().size)
        assertTrue(Productivity.isValid(data))

        val rent = data.tasks.first { it.text == "pay rent" }
        data = Productivity.updateTask(data, data.tasks.first { it.text == "buy milk" }.copy(done = true))
        assertEquals(listOf("pay rent", "buy milk"), Productivity.sorted(data.tasks).map { it.text })
        assertEquals(setOf(20_000L), Productivity.busyDays(data))
        assertEquals(listOf(rent), Productivity.tasksOn(data, 20_000))
        assertEquals(listOf("pay rent"), Productivity.clearDone(data).tasks.map { it.text })

        val full = ProductivityData(tasks = (1L..Productivity.MAX_TASKS).map { TaskItem(it, "t$it") })
        assertEquals(full, Productivity.addTask(full, "one more", null, 9_999))
        assertEquals(Productivity.MAX_TASK_LENGTH, Productivity.normalizeText("x".repeat(900)).length)
    }

    @Test
    fun invalidStoredDataIsRejected() {
        assertFalse(Productivity.isValid(ProductivityData(focusMinutes = 0)))
        assertFalse(Productivity.isValid(ProductivityData(tasks = listOf(TaskItem(1, "a"), TaskItem(1, "b")))))
        assertFalse(Productivity.isValid(ProductivityData(schemaVersion = 2)))
        assertTrue(Productivity.isValid(ProductivityData()))
    }
}

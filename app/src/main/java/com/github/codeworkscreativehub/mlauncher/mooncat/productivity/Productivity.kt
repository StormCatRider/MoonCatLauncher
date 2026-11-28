package com.github.codeworkscreativehub.mlauncher.mooncat.productivity

import com.squareup.moshi.JsonClass
import java.time.DayOfWeek
import java.time.LocalDateTime

/** A local to-do item. [dueEpochDay] is a LocalDate epoch day, or null when it has no date. */
@JsonClass(generateAdapter = true)
data class TaskItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val dueEpochDay: Long? = null
)

/**
 * A weekly focus window. [days] uses ISO numbers (1 = Monday ... 7 = Sunday).
 * When [endMinute] <= [startMinute] the window crosses midnight and belongs to the day it starts on.
 */
@JsonClass(generateAdapter = true)
data class FocusSchedule(
    val id: Long,
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int
)

@JsonClass(generateAdapter = true)
data class ProductivityData(
    val schemaVersion: Int = 1,
    val tasks: List<TaskItem> = emptyList(),
    /** Package names the launcher pauses while focus is active. */
    val focusApps: Set<String> = emptySet(),
    val schedules: List<FocusSchedule> = emptyList(),
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    /** End of the current Pomodoro focus session in epoch millis, or 0 when none is running. */
    val sessionEndMillis: Long = 0,
    /** End of the current break in epoch millis, or 0. */
    val breakEndMillis: Long = 0
)

object Productivity {
    const val MAX_TASKS = 500
    const val MAX_TASK_LENGTH = 500
    const val MAX_SCHEDULES = 20
    const val MAX_FOCUS_APPS = 500
    val FOCUS_RANGE = 1..180
    val BREAK_RANGE = 1..60
    private const val MINUTES_PER_DAY = 24 * 60

    fun isValid(data: ProductivityData): Boolean =
        data.schemaVersion == 1 &&
            data.tasks.size <= MAX_TASKS &&
            data.tasks.all { it.text.isNotBlank() && it.text.length <= MAX_TASK_LENGTH } &&
            data.tasks.map { it.id }.toSet().size == data.tasks.size &&
            data.focusApps.size <= MAX_FOCUS_APPS &&
            data.focusApps.all { it.isNotBlank() && it.length <= 255 } &&
            data.schedules.size <= MAX_SCHEDULES &&
            data.schedules.all(::isValid) &&
            data.focusMinutes in FOCUS_RANGE &&
            data.breakMinutes in BREAK_RANGE &&
            data.sessionEndMillis >= 0 && data.breakEndMillis >= 0

    fun isValid(schedule: FocusSchedule): Boolean =
        schedule.days.isNotEmpty() && schedule.days.all { it in 1..7 } &&
            schedule.startMinute in 0 until MINUTES_PER_DAY &&
            schedule.endMinute in 0 until MINUTES_PER_DAY &&
            schedule.startMinute != schedule.endMinute

    /** True when [now] falls inside the schedule, including the part of an overnight window after midnight. */
    fun isActive(schedule: FocusSchedule, now: LocalDateTime): Boolean {
        if (!isValid(schedule)) return false
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.dayOfWeek.minus(1).value
        return if (schedule.startMinute < schedule.endMinute) {
            today in schedule.days && minute >= schedule.startMinute && minute < schedule.endMinute
        } else {
            (today in schedule.days && minute >= schedule.startMinute) ||
                (yesterday in schedule.days && minute < schedule.endMinute)
        }
    }

    fun sessionRunning(data: ProductivityData, nowMillis: Long): Boolean = data.sessionEndMillis > nowMillis

    fun breakRunning(data: ProductivityData, nowMillis: Long): Boolean = data.breakEndMillis > nowMillis

    fun focusActive(data: ProductivityData, now: LocalDateTime, nowMillis: Long): Boolean =
        sessionRunning(data, nowMillis) || data.schedules.any { isActive(it, now) }

    /** Whether the launcher should pause [packageName] right now. */
    fun isPaused(data: ProductivityData, packageName: String, now: LocalDateTime, nowMillis: Long): Boolean =
        packageName in data.focusApps && focusActive(data, now, nowMillis)

    /** Open tasks first (dated ones by date), then finished ones; newest first inside each group. */
    fun sorted(tasks: List<TaskItem>): List<TaskItem> = tasks.sortedWith(
        compareBy<TaskItem> { it.done }
            .thenBy { it.dueEpochDay ?: Long.MAX_VALUE }
            .thenByDescending { it.id }
    )

    fun normalizeText(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(MAX_TASK_LENGTH)

    fun addTask(data: ProductivityData, text: String, dueEpochDay: Long?, id: Long): ProductivityData {
        val clean = normalizeText(text)
        if (clean.isEmpty() || data.tasks.size >= MAX_TASKS) return data
        val uniqueId = generateSequence(id) { it + 1 }.first { candidate -> data.tasks.none { it.id == candidate } }
        return data.copy(tasks = data.tasks + TaskItem(uniqueId, clean, false, dueEpochDay))
    }

    fun updateTask(data: ProductivityData, task: TaskItem): ProductivityData =
        data.copy(tasks = data.tasks.map { if (it.id == task.id) task.copy(text = normalizeText(task.text)) else it }
            .filter { it.text.isNotEmpty() })

    fun removeTask(data: ProductivityData, id: Long): ProductivityData = data.copy(tasks = data.tasks.filterNot { it.id == id })

    fun clearDone(data: ProductivityData): ProductivityData = data.copy(tasks = data.tasks.filterNot { it.done })

    fun tasksOn(data: ProductivityData, epochDay: Long): List<TaskItem> = sorted(data.tasks.filter { it.dueEpochDay == epochDay })

    /** Days that have at least one unfinished task, for the calendar marks. */
    fun busyDays(data: ProductivityData): Set<Long> = data.tasks.filter { !it.done }.mapNotNull { it.dueEpochDay }.toSet()

    fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** Day numbers in the order the week starts on for [firstDay]. */
    fun weekOrder(firstDay: DayOfWeek): List<Int> = (0 until 7).map { firstDay.plus(it.toLong()).value }
}

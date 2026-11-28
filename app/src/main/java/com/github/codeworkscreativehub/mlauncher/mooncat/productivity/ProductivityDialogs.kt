package com.github.codeworkscreativehub.mlauncher.mooncat.productivity

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.AlarmClock
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.launchCalendar
import com.github.codeworkscreativehub.common.showLongToast
import com.github.codeworkscreativehub.mlauncher.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Local productivity tools: tasks, a calendar of task dates, a Pomodoro timer and focus mode.
 * Everything is stored on the device. The Pomodoro alarm is set in the user's own clock app,
 * so it rings even if Android stops the launcher, and no extra permission is needed.
 */
class ProductivityDialogs(
    private val activity: FragmentActivity,
    private val openNotes: (() -> Unit)? = null
) {
    private val store = ProductivityStore(activity)
    private val locale: Locale get() = Locale.getDefault()
    private val dateFormat get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    private fun builder() = MaterialAlertDialogBuilder(activity)

    // ---- Hub -------------------------------------------------------------------------------

    fun showHub() {
        val data = store.load()
        val now = System.currentTimeMillis()
        val open = data.tasks.count { !it.done }
        val pomodoro = when {
            Productivity.sessionRunning(data, now) -> getLocalizedString(R.string.mooncat_prod_pomodoro_focus_left, remaining(data.sessionEndMillis))
            Productivity.breakRunning(data, now) -> getLocalizedString(R.string.mooncat_prod_pomodoro_break_left, remaining(data.breakEndMillis))
            else -> getLocalizedString(R.string.mooncat_prod_pomodoro_idle)
        }
        val focus = if (Productivity.focusActive(data, LocalDateTime.now(), now))
            getLocalizedString(R.string.mooncat_prod_focus_on) else getLocalizedString(R.string.mooncat_prod_focus_off)

        val entries = mutableListOf<Pair<String, () -> Unit>>(
            getLocalizedString(R.string.mooncat_prod_tasks_entry, open) to ::showTasks,
            getLocalizedString(R.string.mooncat_prod_calendar) to { showCalendar(YearMonth.now()) },
            "${getLocalizedString(R.string.mooncat_prod_pomodoro)} · $pomodoro" to ::showPomodoro,
            "${getLocalizedString(R.string.mooncat_prod_focus)} · $focus" to ::showFocus
        )
        openNotes?.let { entries += getLocalizedString(R.string.mooncat_prod_notes) to it }
        builder()
            .setTitle(R.string.mooncat_prod_title)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    // ---- Tasks -----------------------------------------------------------------------------

    fun showTasks() {
        val data = store.load()
        val tasks = Productivity.sorted(data.tasks)
        val b = builder().setTitle(R.string.mooncat_prod_tasks)
        if (tasks.isEmpty()) {
            b.setMessage(R.string.mooncat_prod_tasks_empty)
        } else {
            b.setItems(tasks.map(::taskLabel).toTypedArray()) { _, which -> taskActions(tasks[which]) { showTasks() } }
        }
        b.setPositiveButton(R.string.mooncat_prod_task_add) { _, _ -> addTask(null) { showTasks() } }
        if (tasks.any { it.done }) {
            b.setNeutralButton(R.string.mooncat_prod_tasks_clear_done) { _, _ ->
                store.update(Productivity::clearDone)
                showTasks()
            }
        }
        b.setNegativeButton(R.string.mooncat_prod_close, null).show()
    }

    private fun taskLabel(task: TaskItem): String {
        val box = if (task.done) "☑ " else "☐ "
        val date = task.dueEpochDay?.let { " · " + LocalDate.ofEpochDay(it).format(dateFormat) }.orEmpty()
        return box + task.text + date
    }

    private fun taskActions(task: TaskItem, back: () -> Unit) {
        val actions = listOf(
            getLocalizedString(if (task.done) R.string.mooncat_prod_task_undone else R.string.mooncat_prod_task_done),
            getLocalizedString(R.string.mooncat_prod_task_edit),
            getLocalizedString(R.string.mooncat_prod_task_date),
            getLocalizedString(R.string.mooncat_prod_task_delete)
        )
        builder()
            .setTitle(task.text)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> { store.update { Productivity.updateTask(it, task.copy(done = !task.done)) }; back() }
                    1 -> askText(R.string.mooncat_prod_task_edit, task.text) { text ->
                        store.update { Productivity.updateTask(it, task.copy(text = text)) }; back()
                    }
                    2 -> pickDate(task.dueEpochDay, allowNone = true) { day ->
                        store.update { Productivity.updateTask(it, task.copy(dueEpochDay = day)) }; back()
                    }
                    else -> builder()
                        .setMessage(getLocalizedString(R.string.mooncat_prod_task_delete_confirm, task.text))
                        .setPositiveButton(R.string.mooncat_prod_task_delete) { _, _ ->
                            store.update { Productivity.removeTask(it, task.id) }; back()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> back() }
                        .show()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> back() }
            .show()
    }

    /** With [day] the task gets that date; otherwise the user may pick one. */
    private fun addTask(day: Long?, back: () -> Unit) {
        if (store.load().tasks.size >= Productivity.MAX_TASKS) {
            activity.showLongToast(getLocalizedString(R.string.mooncat_prod_tasks_full, Productivity.MAX_TASKS))
            return
        }
        askText(R.string.mooncat_prod_task_add, "", if (day == null) R.string.mooncat_prod_task_add_with_date else null, { text ->
            pickDate(null, allowNone = false) { picked -> saveNewTask(text, picked); back() }
        }) { text ->
            saveNewTask(text, day); back()
        }
    }

    private fun saveNewTask(text: String, day: Long?) {
        store.update { Productivity.addTask(it, text, day, System.currentTimeMillis()) }
    }

    private fun askText(
        title: Int,
        initial: String,
        alternative: Int? = null,
        onAlternative: ((String) -> Unit)? = null,
        onDone: (String) -> Unit
    ) {
        val b = builder().setTitle(title)
        val input = EditText(b.context).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getLocalizedString(R.string.mooncat_prod_task_hint)
            setText(initial)
            setSelection(text.length)
        }
        val box = LinearLayout(b.context).apply {
            val pad = dp(20)
            setPadding(pad, dp(8), pad, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        b.setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> Productivity.normalizeText(input.text.toString()).takeIf { it.isNotEmpty() }?.let(onDone) }
            .setNegativeButton(android.R.string.cancel, null)
        if (alternative != null && onAlternative != null) {
            b.setNeutralButton(alternative) { _, _ -> Productivity.normalizeText(input.text.toString()).takeIf { it.isNotEmpty() }?.let(onAlternative) }
        }
        val dialog = b.show()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); true
            } else false
        }
        input.requestFocus()
    }

    private fun pickDate(current: Long?, allowNone: Boolean, onPicked: (Long?) -> Unit) {
        val start = current?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
        val picker = DatePickerDialog(activity, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).toEpochDay()) },
            start.year, start.monthValue - 1, start.dayOfMonth)
        if (allowNone && current != null) {
            picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, getLocalizedString(R.string.mooncat_prod_task_no_date)) { _, _ -> onPicked(null) }
        }
        picker.show()
    }

    // ---- Calendar --------------------------------------------------------------------------

    fun showCalendar(month: YearMonth) {
        val data = store.load()
        val busy = Productivity.busyDays(data)
        val b = builder()
        val ctx = b.context
        val today = LocalDate.now()
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        lateinit var dialog: AlertDialog

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val header = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        fun arrow(text: String, description: Int, target: YearMonth) = TextView(ctx).apply {
            this.text = text
            textSize = 22f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            contentDescription = getLocalizedString(description)
            setOnClickListener { dialog.dismiss(); showCalendar(target) }
        }
        val title = TextView(ctx).apply {
            text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(arrow("‹", R.string.mooncat_prod_calendar_previous, month.minusMonths(1)))
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(arrow("›", R.string.mooncat_prod_calendar_next, month.plusMonths(1)))
        root.addView(header)

        val grid = GridLayout(ctx).apply { columnCount = 7 }
        fun cellParams() = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
            width = 0
        }
        Productivity.weekOrder(firstDay).forEach { dayNumber ->
            grid.addView(TextView(ctx).apply {
                text = DayOfWeek.of(dayNumber).getDisplayName(TextStyle.NARROW, locale)
                gravity = Gravity.CENTER
                alpha = 0.7f
                setPadding(0, dp(4), 0, dp(4))
            }, cellParams())
        }
        val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        repeat(leading) { grid.addView(TextView(ctx), cellParams()) }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            val date = month.atDay(dayOfMonth)
            val epochDay = date.toEpochDay()
            val hasTasks = epochDay in busy
            grid.addView(TextView(ctx).apply {
                text = if (hasTasks) "$dayOfMonth\n•" else "$dayOfMonth\n "
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                if (date == today) {
                    setTypeface(typeface, Typeface.BOLD)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), currentTextColor)
                    }
                }
                contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) +
                    if (hasTasks) ", " + getLocalizedString(R.string.mooncat_prod_calendar_has_tasks) else ""
                setOnClickListener { dialog.dismiss(); showDay(epochDay) }
            }, cellParams())
        }
        root.addView(grid)

        dialog = b.setView(root)
            .setPositiveButton(R.string.mooncat_prod_calendar_today) { _, _ -> showDay(today.toEpochDay()) }
            .setNeutralButton(R.string.mooncat_prod_calendar_open_app) { _, _ -> activity.launchCalendar() }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    private fun showDay(epochDay: Long) {
        val tasks = Productivity.tasksOn(store.load(), epochDay)
        val date = LocalDate.ofEpochDay(epochDay)
        val back = { showDay(epochDay) }
        val b = builder().setTitle(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)))
        if (tasks.isEmpty()) b.setMessage(R.string.mooncat_prod_calendar_day_empty)
        else b.setItems(tasks.map(::taskLabel).toTypedArray()) { _, which -> taskActions(tasks[which], back) }
        b.setPositiveButton(R.string.mooncat_prod_task_add) { _, _ -> addTask(epochDay, back) }
            .setNegativeButton(R.string.mooncat_prod_calendar_back) { _, _ -> showCalendar(YearMonth.from(date)) }
            .show()
    }

    // ---- Pomodoro --------------------------------------------------------------------------

    fun showPomodoro() {
        val b = builder().setTitle(R.string.mooncat_prod_pomodoro)
        val status = TextView(b.context).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        val detail = TextView(b.context).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val root = LinearLayout(b.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(detail)
        }
        val handler = Handler(Looper.getMainLooper())
        lateinit var dialog: AlertDialog

        fun render() {
            val data = store.load()
            val now = System.currentTimeMillis()
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            when {
                Productivity.sessionRunning(data, now) -> {
                    status.text = remaining(data.sessionEndMillis)
                    detail.text = getLocalizedString(R.string.mooncat_prod_pomodoro_focusing, clock(data.sessionEndMillis))
                    positive.text = getLocalizedString(R.string.mooncat_prod_pomodoro_stop)
                }
                Productivity.breakRunning(data, now) -> {
                    status.text = remaining(data.breakEndMillis)
                    detail.text = getLocalizedString(R.string.mooncat_prod_pomodoro_resting, clock(data.breakEndMillis))
                    positive.text = getLocalizedString(R.string.mooncat_prod_pomodoro_stop)
                }
                data.sessionEndMillis != 0L -> {
                    status.text = "✓"
                    detail.text = getLocalizedString(R.string.mooncat_prod_pomodoro_done, data.breakMinutes)
                    positive.text = getLocalizedString(R.string.mooncat_prod_pomodoro_start_break, data.breakMinutes)
                }
                else -> {
                    status.text = "%02d:00".format(data.focusMinutes)
                    detail.text = getLocalizedString(R.string.mooncat_prod_pomodoro_explain)
                    positive.text = getLocalizedString(R.string.mooncat_prod_pomodoro_start, data.focusMinutes)
                }
            }
        }

        val tick = object : Runnable {
            override fun run() {
                render()
                handler.postDelayed(this, 1000)
            }
        }

        dialog = b.setView(root)
            .setPositiveButton(R.string.mooncat_prod_pomodoro_stop, null)
            .setNeutralButton(R.string.mooncat_prod_pomodoro_durations, null)
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .create()
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val data = store.load()
                val now = System.currentTimeMillis()
                when {
                    Productivity.sessionRunning(data, now) || Productivity.breakRunning(data, now) -> {
                        store.update { it.copy(sessionEndMillis = 0, breakEndMillis = 0) }
                        activity.showLongToast(getLocalizedString(R.string.mooncat_prod_pomodoro_stopped))
                    }
                    data.sessionEndMillis != 0L -> startPhase(focus = false)
                    else -> startPhase(focus = true)
                }
                render()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { chooseDurations { render() } }
            tick.run()
        }
        dialog.show()
    }

    private fun startPhase(focus: Boolean) {
        val data = store.load()
        val minutes = if (focus) data.focusMinutes else data.breakMinutes
        val end = System.currentTimeMillis() + minutes * 60_000L
        store.update { if (focus) it.copy(sessionEndMillis = end, breakEndMillis = 0) else it.copy(sessionEndMillis = 0, breakEndMillis = end) }
        val message = getLocalizedString(if (focus) R.string.mooncat_prod_pomodoro_alarm_focus else R.string.mooncat_prod_pomodoro_alarm_break)
        val timer = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        try {
            activity.startActivity(timer)
        } catch (_: ActivityNotFoundException) {
            activity.showLongToast(getLocalizedString(R.string.mooncat_prod_pomodoro_no_clock))
        } catch (_: SecurityException) {
            activity.showLongToast(getLocalizedString(R.string.mooncat_prod_pomodoro_no_clock))
        }
    }

    private fun chooseDurations(after: () -> Unit) {
        val focusOptions = listOf(15, 20, 25, 30, 45, 50, 60, 90)
        val breakOptions = listOf(3, 5, 10, 15, 20, 30)
        val data = store.load()
        builder()
            .setTitle(R.string.mooncat_prod_pomodoro_focus_length)
            .setSingleChoiceItems(focusOptions.map { getLocalizedString(R.string.mooncat_prod_minutes, it) }.toTypedArray(),
                focusOptions.indexOf(data.focusMinutes)) { focusDialog, f ->
                focusDialog.dismiss()
                builder()
                    .setTitle(R.string.mooncat_prod_pomodoro_break_length)
                    .setSingleChoiceItems(breakOptions.map { getLocalizedString(R.string.mooncat_prod_minutes, it) }.toTypedArray(),
                        breakOptions.indexOf(data.breakMinutes)) { breakDialog, r ->
                        breakDialog.dismiss()
                        store.update { it.copy(focusMinutes = focusOptions[f], breakMinutes = breakOptions[r]) }
                        after()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Focus mode ------------------------------------------------------------------------

    fun showFocus() {
        val data = store.load()
        val active = Productivity.focusActive(data, LocalDateTime.now(), System.currentTimeMillis())
        val entries = listOf(
            getLocalizedString(R.string.mooncat_prod_focus_apps, data.focusApps.size),
            getLocalizedString(R.string.mooncat_prod_focus_schedules, data.schedules.size),
            getLocalizedString(R.string.mooncat_prod_focus_about)
        )
        builder()
            .setTitle(getLocalizedString(R.string.mooncat_prod_focus) + " · " +
                getLocalizedString(if (active) R.string.mooncat_prod_focus_on else R.string.mooncat_prod_focus_off))
            .setItems(entries.toTypedArray()) { _, which ->
                when (which) {
                    0 -> chooseFocusApps()
                    1 -> showSchedules()
                    else -> builder()
                        .setTitle(R.string.mooncat_prod_focus)
                        .setMessage(R.string.mooncat_prod_focus_explain)
                        .setPositiveButton(android.R.string.ok) { _, _ -> showFocus() }
                        .show()
                }
            }
            .setNegativeButton(R.string.mooncat_prod_close, null)
            .show()
    }

    private fun chooseFocusApps() {
        val own = activity.packageName
        val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val installed = launcherApps.getActivityList(null, Process.myUserHandle())
            .map { it.applicationInfo.packageName to it.label.toString() }
            .filter { it.first != own }
            .distinctBy { it.first }
        val selected = store.load().focusApps
        val missing = selected.filter { pkg -> installed.none { it.first == pkg } }.map { it to it }
        val apps = (installed + missing).sortedBy { it.second.lowercase(locale) }
        val checked = BooleanArray(apps.size) { apps[it].first in selected }
        builder()
            .setTitle(R.string.mooncat_prod_focus_pick)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                store.update { it.copy(focusApps = chosen.take(Productivity.MAX_FOCUS_APPS).toSet()) }
                showFocus()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showFocus() }
            .show()
    }

    private fun scheduleLabel(schedule: FocusSchedule): String {
        val order = Productivity.weekOrder(WeekFields.of(locale).firstDayOfWeek)
        val days = order.filter { it in schedule.days }
            .joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
        return "$days · ${Productivity.formatMinute(schedule.startMinute)}–${Productivity.formatMinute(schedule.endMinute)}"
    }

    private fun showSchedules() {
        val schedules = store.load().schedules
        val b = builder().setTitle(R.string.mooncat_prod_focus_schedules_title)
        if (schedules.isEmpty()) b.setMessage(R.string.mooncat_prod_focus_schedules_empty)
        else b.setItems(schedules.map(::scheduleLabel).toTypedArray()) { _, which ->
            val schedule = schedules[which]
            builder()
                .setMessage(getLocalizedString(R.string.mooncat_prod_focus_schedule_delete_confirm, scheduleLabel(schedule)))
                .setPositiveButton(R.string.mooncat_prod_task_delete) { _, _ ->
                    store.update { data -> data.copy(schedules = data.schedules.filterNot { it.id == schedule.id }) }
                    showSchedules()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> showSchedules() }
                .show()
        }
        if (schedules.size < Productivity.MAX_SCHEDULES) {
            b.setPositiveButton(R.string.mooncat_prod_focus_schedule_add) { _, _ -> addSchedule() }
        }
        b.setNegativeButton(R.string.mooncat_prod_calendar_back) { _, _ -> showFocus() }.show()
    }

    private fun addSchedule() {
        val order = Productivity.weekOrder(WeekFields.of(locale).firstDayOfWeek)
        val names = order.map { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, locale).replaceFirstChar { c -> c.titlecase(locale) } }
        val checked = BooleanArray(order.size) { order[it] in 1..5 }
        val is24 = DateFormat.is24HourFormat(activity)
        builder()
            .setTitle(R.string.mooncat_prod_focus_schedule_days)
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val days = order.filterIndexed { i, _ -> checked[i] }.toSet()
                if (days.isEmpty()) { showSchedules(); return@setPositiveButton }
                timePicker(R.string.mooncat_prod_focus_schedule_start, 9 * 60, is24) { start ->
                    timePicker(R.string.mooncat_prod_focus_schedule_end, (start + 120) % (24 * 60), is24) { end ->
                        val schedule = FocusSchedule(System.currentTimeMillis(), days, start, end)
                        if (Productivity.isValid(schedule)) {
                            store.update { it.copy(schedules = it.schedules + schedule) }
                        } else {
                            activity.showLongToast(getLocalizedString(R.string.mooncat_prod_focus_schedule_invalid))
                        }
                        showSchedules()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> showSchedules() }
            .show()
    }

    private fun timePicker(title: Int, initial: Int, is24: Boolean, onPicked: (Int) -> Unit) {
        TimePickerDialog(activity, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, is24).apply {
            setTitle(title)
            show()
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private fun remaining(endMillis: Long): String {
        val seconds = ((endMillis - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun clock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        /**
         * Asks before opening an app the user paused for focus. This is a launcher-level pause:
         * the app stays reachable from notifications, recents and other launchers.
         */
        fun confirmLaunch(activity: FragmentActivity, packageName: String, label: String, proceed: () -> Unit) {
            val store = ProductivityStore(activity)
            if (!store.isPaused(packageName)) {
                proceed(); return
            }
            val data = store.load()
            val dialogs = ProductivityDialogs(activity)
            val until = if (Productivity.sessionRunning(data, System.currentTimeMillis()))
                getLocalizedString(R.string.mooncat_prod_focus_paused_session, dialogs.clock(data.sessionEndMillis))
            else getLocalizedString(R.string.mooncat_prod_focus_paused_schedule)
            MaterialAlertDialogBuilder(activity)
                .setTitle(getLocalizedString(R.string.mooncat_prod_focus_paused_title, label))
                .setMessage(until)
                .setPositiveButton(R.string.mooncat_prod_focus_stay, null)
                .setNegativeButton(R.string.mooncat_prod_focus_open_anyway) { _, _ -> proceed() }
                .show()
        }
    }
}

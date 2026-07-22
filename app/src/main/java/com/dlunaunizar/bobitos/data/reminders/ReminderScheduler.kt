package com.dlunaunizar.bobitos.data.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private data class Reminder(val key: String, val at: Instant, val title: String, val text: String)

/**
 * Programa notificaciones locales (WorkManager) para las tareas con fecha y los eventos de las
 * próximas [HORIZON]. Se llama al abrir la app; una vez programadas disparan aunque la app se cierre.
 * Coste 0: no hay servidor ni trabajos periódicos. Lectura puntual (un snapshot por espacio).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
) {
    suspend fun reschedule(userId: String, spaces: List<SpaceSummary>) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(ReminderWorker.TAG)
        val now = Instant.now()
        val end = now.plus(HORIZON)
        val reminders = spaces.flatMap { space -> remindersForSpace(space, userId, now, end) }
        reminders.forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, reminder.at))
                .addTag(ReminderWorker.TAG)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_TITLE, reminder.title)
                        .putString(ReminderWorker.KEY_TEXT, reminder.text)
                        .putInt(ReminderWorker.KEY_NOTIFICATION_ID, reminder.key.hashCode())
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(reminder.key, ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ReminderWorker.TAG)
    }

    private suspend fun remindersForSpace(
        space: SpaceSummary,
        userId: String,
        now: Instant,
        end: Instant,
    ): List<Reminder> {
        val tasks = taskRepository.tasks(space.id).first()
            .filter { it.status == TaskStatus.TODO && it.dueAt != null && it.dueAt in now..end }
            .map { task ->
                Reminder(
                    key = "task-${task.id}",
                    at = task.dueAt!!,
                    title = context.getString(R.string.reminder_task, task.title),
                    text = space.name,
                )
            }
        val events = calendarRepository.events(space.id, now, end).first()
            .filter { it.startAt in now..end && userId in it.participantIds }
            .map { event ->
                Reminder(
                    key = "event-${event.id}",
                    at = event.startAt,
                    title = context.getString(R.string.reminder_event, event.title),
                    text = space.name,
                )
            }
        return tasks + events
    }

    private companion object {
        val HORIZON: Duration = Duration.ofHours(48)
    }
}

package com.mundus.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.Task

private fun statusLabel(task: Task): String =
    when {
        task.isTrashed -> "В корзине"
        task.isCancelled -> "Отменена"
        task.isCompleted -> "Завершена"
        task.status == "deferred" || task.isSomeday -> "Отложена"
        task.status == "started" -> "В работе"
        task.status == "inbox" -> "Входящие"
        else -> "Запланирована"
    }

private fun priorityLabel(priority: Int): String? =
    when (priority) {
        1 -> "Низкий"
        2 -> "Средний"
        3 -> "Высокий"
        else -> null
    }

private fun recurrenceLabel(rule: com.mundus.agenda.RecurrenceRule): String {
    // frequency: 0 daily, 1 weekly, 2 monthly, 3 yearly — accusative and
    // genitive plural forms for RU.
    val acc = arrayOf("день", "неделю", "месяц", "год").getOrElse(rule.frequency) { "день" }
    val genPlural =
        arrayOf("дней", "недель", "месяцев", "лет").getOrElse(rule.frequency) { "дней" }
    val base =
        if (rule.interval > 1) "Каждые ${rule.interval} $genPlural" else "Каждый $acc"
    return if (rule.recurrenceType == 1) "$base, после выполнения" else base
}

@Composable
private fun FieldRow(label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TagChip(title: String, colorName: String) {
    val color = tagColor(colorName, MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun TaskScreen(board: Board, taskId: String) {
    val task = board.task(taskId)
    if (task == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Задача не найдена", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val dateView = board.dateView(task)
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            statusLabel(task),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (task.isCompleted) AgendaTheme.extra.success
                else MaterialTheme.colorScheme.primary,
        )
        task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(16.dp))
            Text(
                notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        dateView.dateKey?.let {
            FieldRow(
                "Дата",
                dateView.label,
                if (dateView.overdue) MaterialTheme.colorScheme.error else null,
            )
        }
        dateView.deadlineLabel?.let { FieldRow("Дедлайн", it) }
        dateView.reminderLabel?.let { FieldRow("Напоминание", it) }
        task.projectId?.let { pid -> board.project(pid)?.let { FieldRow("Проект", it.title) } }
        if (task.isToday && dateView.dateKey == null) FieldRow("Эта неделя", "да")
        priorityLabel(task.priority)?.let { FieldRow("Приоритет", it) }
        task.recurrence?.let { FieldRow("Повтор", recurrenceLabel(it)) }
        if (task.isEvening) FieldRow("Вечер", "да")
        if (task.isSomeday) FieldRow("Потом", "да")
        task.significance?.let { FieldRow("Значимость", "$it/10") }
        task.fuelCost?.let { FieldRow("Мыслетопливо", "${it.toInt()}") }
        if (task.billable) task.price?.let { FieldRow("Оплата", "${it.toInt()}") }
        dateView.completedLabel?.let { FieldRow("Завершена", it) }
        dateView.createdLabel?.let { FieldRow("Создана", it) }

        val tags = task.tagIds.mapNotNull { board.tag(it) }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tag in tags) TagChip(tag.title, tag.color)
            }
        }
        if (task.checklist.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            val doneCount = task.checklist.count { it.isCompleted }
            FieldRow(
                "Чеклист",
                "$doneCount/${task.checklist.size} выполнено",
            )
        }
    }
}

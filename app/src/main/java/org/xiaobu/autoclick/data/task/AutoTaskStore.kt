package org.xiaobu.autoclick.data.task

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class AutoTaskStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()

    fun getDraft(): AutoTaskConfig {
        val rawDraft = preferences.getString(DRAFT_KEY, null).orEmpty()
        return rawDraft.fromJson<AutoTaskConfig>()?.sanitizeDraft()
            ?: AutoTaskConfig(updatedAt = System.currentTimeMillis())
    }

    fun saveDraft(config: AutoTaskConfig) {
        preferences.edit {
            putString(DRAFT_KEY, config.sanitizeDraft().toJson())
        }
    }

    fun getTasks(): List<AutoTaskConfig> {
        val rawTasks = preferences.getString(LIST_KEY, null).orEmpty()
        if (rawTasks.isBlank()) return emptyList()
        val tasks = rawTasks.fromJson<List<AutoTaskConfig>>().orEmpty()
            .map { it.sanitizeSaved() }
            .take(MAX_TASKS)
        preferences.edit {
            putString(LIST_KEY, tasks.toJson())
        }
        return tasks
    }

    fun saveTask(config: AutoTaskConfig) {
        val savedConfig = config.sanitizeSaved().copy(updatedAt = System.currentTimeMillis())
        val mergedTasks = buildList {
            add(savedConfig)
            addAll(getTasks().filterNot { it.id == savedConfig.id })
        }.take(MAX_TASKS)
        preferences.edit {
            putString(LIST_KEY, mergedTasks.toJson())
        }
    }

    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return
        preferences.edit {
            putString(LIST_KEY, getTasks().filterNot { it.id == taskId }.toJson())
        }
    }

    fun encodeTask(task: AutoTaskConfig): String {
        return AutoTaskExportPayload(task = task.sanitizeSaved()).toJson()
    }

    fun decodeTask(rawJson: String): AutoTaskConfig? {
        val payload = rawJson.fromJson<AutoTaskExportPayload>()
        if (payload != null && payload.type == EXPORT_TYPE) {
            return payload.task.sanitizeSaved()
        }
        return rawJson.fromJson<AutoTaskConfig>()?.sanitizeSaved()
    }

    private fun AutoTaskConfig.sanitizeDraft(): AutoTaskConfig {
        return copy(
            id = id.ifBlank { buildTaskId() },
            name = name.trim(),
            updatedAt = updatedAt.coerceAtLeast(0L),
            steps = steps
                .map { it.sanitize() }
                .distinctBy { it.id }
                .take(MAX_STEPS)
        )
    }

    private fun AutoTaskConfig.sanitizeSaved(): AutoTaskConfig {
        val safeTime = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        return sanitizeDraft().copy(
            name = name.trim().ifBlank { buildDefaultTaskName(safeTime) },
            updatedAt = safeTime
        )
    }

    private fun AutoTaskStep.sanitize(): AutoTaskStep {
        return copy(
            id = id.ifBlank { buildStepId() },
            title = title.trim(),
            durationMs = durationMs.coerceAtLeast(20L),
            delayAfterMs = delayAfterMs.coerceAtLeast(0L),
            target = target?.sanitize(),
            secondaryTarget = secondaryTarget?.sanitize()
        )
    }

    private fun AutoTaskTarget.sanitize(): AutoTaskTarget {
        return copy(
            x = x.coerceAtLeast(0),
            y = y.coerceAtLeast(0),
            text = text.trim(),
            index = index.coerceAtLeast(1),
            imageUri = imageUri.trim()
        )
    }

    private fun Any.toJson(): String {
        return gson.toJson(this)
    }

    private inline fun <reified T> String.fromJson(): T? {
        return try {
            gson.fromJson<T>(this, object : TypeToken<T>() {}.type)
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun buildTaskId(): String {
        return "auto_task_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    private fun buildStepId(): String {
        return "auto_task_step_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    private fun buildDefaultTaskName(timeMillis: Long): String {
        val timeText = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
        return "自动点击器 $timeText"
    }

    private data class AutoTaskExportPayload(
        val type: String = EXPORT_TYPE,
        val version: Int = EXPORT_VERSION,
        val task: AutoTaskConfig = AutoTaskConfig()
    )

    private companion object {
        private const val PREF_NAME = "auto_task"
        private const val DRAFT_KEY = "auto_task_draft"
        private const val LIST_KEY = "auto_task_list"
        private const val EXPORT_TYPE = "auto_task_config"
        private const val EXPORT_VERSION = 1
        private const val MAX_TASKS = 20
        private const val MAX_STEPS = 100
    }
}

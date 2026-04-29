package org.xiaobu.autoclick.data.trigger

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget

class AutoTriggerStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()

    fun getDraft(): AutoTriggerConfig {
        val rawDraft = preferences.getString(DRAFT_KEY, null).orEmpty()
        return rawDraft.fromJson<AutoTriggerConfig>()?.sanitizeDraft()
            ?: AutoTriggerConfig(updatedAt = System.currentTimeMillis())
    }

    fun saveDraft(config: AutoTriggerConfig) {
        preferences.edit {
            putString(DRAFT_KEY, config.sanitizeDraft().toJson())
        }
    }

    fun getTriggers(): List<AutoTriggerConfig> {
        val rawTriggers = preferences.getString(LIST_KEY, null).orEmpty()
        if (rawTriggers.isBlank()) return emptyList()
        val triggers = rawTriggers.fromJson<List<AutoTriggerConfig>>().orEmpty()
            .map { it.sanitizeSaved() }
            .take(MAX_TRIGGERS)
        preferences.edit {
            putString(LIST_KEY, triggers.toJson())
        }
        return triggers
    }

    fun saveTrigger(config: AutoTriggerConfig) {
        val savedConfig = config.sanitizeSaved().copy(updatedAt = System.currentTimeMillis())
        val merged = buildList {
            add(savedConfig)
            addAll(getTriggers().filterNot { it.id == savedConfig.id })
        }.take(MAX_TRIGGERS)
        preferences.edit {
            putString(LIST_KEY, merged.toJson())
        }
    }

    fun deleteTrigger(triggerId: String) {
        if (triggerId.isBlank()) return
        preferences.edit {
            putString(LIST_KEY, getTriggers().filterNot { it.id == triggerId }.toJson())
        }
    }

    fun encodeTrigger(trigger: AutoTriggerConfig): String {
        return AutoTriggerExportPayload(trigger = trigger.sanitizeSaved()).toJson()
    }

    fun decodeTrigger(rawJson: String): AutoTriggerConfig? {
        val payload = rawJson.fromJson<AutoTriggerExportPayload>()
        if (payload != null && payload.type == EXPORT_TYPE) {
            return payload.trigger.sanitizeSaved()
        }
        return rawJson.fromJson<AutoTriggerConfig>()?.sanitizeSaved()
    }

    private fun AutoTriggerConfig.sanitizeDraft(): AutoTriggerConfig {
        val targetApps = normalizeTargetApps()
        return copy(
            name = name.trim(),
            packageName = targetApps.firstOrNull()?.packageName.orEmpty(),
            appLabel = targetApps.firstOrNull()?.appLabel.orEmpty(),
            targetApps = targetApps,
            eventTypes = effectiveEventTypes,
            pageKeyword = "",
            keywordExact = false,
            cooldownMs = cooldownMs.coerceAtLeast(0L),
            updatedAt = updatedAt.coerceAtLeast(0L),
            steps = steps.map { it.sanitize() }
        )
    }

    private fun AutoTriggerConfig.sanitizeSaved(): AutoTriggerConfig {
        val safeTime = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        return sanitizeDraft().copy(
            name = name.trim().ifBlank { buildDefaultTriggerName(safeTime) },
            updatedAt = safeTime
        )
    }

    private fun AutoTriggerConfig.normalizeTargetApps(): List<AutoTriggerApp> {
        val currentApps = targetApps
            .map {
                it.copy(
                    packageName = it.packageName.trim(),
                    appLabel = it.appLabel.trim()
                )
            }
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName.lowercase() }
        if (currentApps.isNotEmpty()) return currentApps
        return if (packageName.isNotBlank()) {
            listOf(
                AutoTriggerApp(
                    packageName = packageName.trim(),
                    appLabel = appLabel.trim()
                )
            )
        } else {
            emptyList()
        }
    }

    private fun AutoTaskStep.sanitize(): AutoTaskStep {
        return copy(
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

    private fun buildDefaultTriggerName(timeMillis: Long): String {
        val timeText = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
        return "触发器 $timeText"
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

    private data class AutoTriggerExportPayload(
        val type: String = EXPORT_TYPE,
        val version: Int = EXPORT_VERSION,
        val trigger: AutoTriggerConfig = AutoTriggerConfig()
    )

    private companion object {
        private const val PREF_NAME = "auto_trigger"
        private const val DRAFT_KEY = "auto_trigger_draft"
        private const val LIST_KEY = "auto_trigger_list"
        private const val MAX_TRIGGERS = 20
        private const val EXPORT_TYPE = "auto_trigger_config"
        private const val EXPORT_VERSION = 1
    }
}

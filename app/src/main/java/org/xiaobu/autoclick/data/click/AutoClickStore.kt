package org.xiaobu.autoclick.data.click

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

class AutoClickStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()

    fun getConfig(): AutoClickConfig {
        val rawConfig = preferences.getString(CONFIG_KEY, null).orEmpty()
        if (rawConfig.isBlank()) return AutoClickConfig()
        return rawConfig.fromJson<AutoClickConfig>()?.sanitize() ?: AutoClickConfig()
    }

    fun saveConfig(config: AutoClickConfig) {
        preferences.edit {
            putString(CONFIG_KEY, config.sanitize().toJson())
        }
    }

    fun addPoint(x: Int, y: Int): AutoClickPointConfig? {
        val config = getConfig()
        if (config.points.size >= MAX_POINTS) return null
        val point = AutoClickPointConfig(
            id = buildPointId(),
            x = x.coerceAtLeast(0),
            y = y.coerceAtLeast(0)
        )
        saveConfig(config.copy(points = config.points + point))
        return point
    }

    fun updatePoint(pointId: String, x: Int, y: Int) {
        if (pointId.isBlank()) return
        val config = getConfig()
        saveConfig(
            config.copy(
                points = config.points.map { point ->
                    if (point.id == pointId) {
                        point.copy(x = x.coerceAtLeast(0), y = y.coerceAtLeast(0))
                    } else {
                        point
                    }
                }
            )
        )
    }

    fun removePoint(pointId: String) {
        if (pointId.isBlank()) return
        val config = getConfig()
        saveConfig(config.copy(points = config.points.filterNot { it.id == pointId }))
    }

    fun movePoint(pointId: String, targetIndex: Int) {
        if (pointId.isBlank()) return
        val config = getConfig()
        val points = config.points.toMutableList()
        val currentIndex = points.indexOfFirst { it.id == pointId }
        if (currentIndex == -1) return
        val safeTargetIndex = targetIndex.coerceIn(0, points.lastIndex)
        if (currentIndex == safeTargetIndex) return
        val point = points.removeAt(currentIndex)
        points.add(safeTargetIndex, point)
        saveConfig(config.copy(points = points))
    }

    fun getPresets(): List<AutoClickPresetConfig> {
        val rawPresets = preferences.getString(PRESET_KEY, null).orEmpty()
        if (rawPresets.isBlank()) return emptyList()

        val presets = rawPresets.fromJson<List<AutoClickPresetConfig>>().orEmpty()
            .map(::sanitizePreset)
            .take(MAX_PRESETS)

        preferences.edit {
            putString(PRESET_KEY, presets.toJson())
        }
        return presets
    }

    fun savePreset(config: AutoClickConfig, name: String) {
        val sanitizedConfig = config.sanitize()
        val preset = AutoClickPresetConfig(
            id = buildPresetId(),
            name = name.trim().ifBlank { buildDefaultPresetName() },
            savedAt = System.currentTimeMillis(),
            config = sanitizedConfig
        )
        val configPoints = sanitizedConfig.points.map { it.x to it.y }
        val mergedPresets = buildList {
            add(preset)
            getPresets().forEach { item ->
                val itemPoints = item.config.points.map { it.x to it.y }
                val sameConfig = item.config.intervalMillis == sanitizedConfig.intervalMillis &&
                    item.config.durationSeconds == sanitizedConfig.durationSeconds &&
                    item.config.maxClickCount == sanitizedConfig.maxClickCount &&
                    itemPoints == configPoints
                if (!sameConfig) add(item)
            }
        }.take(MAX_PRESETS)

        preferences.edit {
            putString(PRESET_KEY, mergedPresets.toJson())
        }
    }

    fun deletePreset(presetId: String) {
        if (presetId.isBlank()) return
        preferences.edit {
            putString(PRESET_KEY, getPresets().filterNot { it.id == presetId }.toJson())
        }
    }

    fun encodePreset(preset: AutoClickPresetConfig): String {
        return AutoClickPresetExportPayload(
            exportedAtMillis = System.currentTimeMillis(),
            preset = sanitizePreset(preset)
        ).toJson()
    }

    fun decodePreset(rawJson: String): AutoClickPresetConfig? {
        val payload = rawJson.fromJson<AutoClickPresetExportPayload>() ?: return null
        if (payload.format != EXPORT_FORMAT || payload.version != EXPORT_VERSION) return null
        return sanitizePreset(payload.preset)
    }

    private fun sanitizePreset(preset: AutoClickPresetConfig): AutoClickPresetConfig {
        return preset.copy(
            id = preset.id.ifBlank { buildPresetId() },
            name = preset.name.ifBlank { buildDefaultPresetName(preset.savedAt) },
            savedAt = preset.savedAt.coerceAtLeast(0L),
            config = preset.config.sanitize()
        )
    }

    private fun AutoClickConfig.sanitize(): AutoClickConfig {
        return copy(
            intervalMillis = intervalMillis.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS),
            durationSeconds = durationSeconds.coerceIn(0, MAX_DURATION_SECONDS),
            maxClickCount = maxClickCount.coerceIn(0, MAX_CLICK_COUNT),
            points = points
                .map { point ->
                    point.copy(
                        id = point.id.ifBlank { buildPointId() },
                        x = point.x.coerceAtLeast(0),
                        y = point.y.coerceAtLeast(0)
                    )
                }
                .distinctBy { it.id }
                .take(MAX_POINTS)
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

    private fun buildPointId(): String {
        return "auto_click_point_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    private fun buildPresetId(): String {
        return "auto_click_preset_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
    }

    private fun buildDefaultPresetName(timeMillis: Long = System.currentTimeMillis()): String {
        val safeTimeMillis = timeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val timeText = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(safeTimeMillis))
        return "配置 $timeText"
    }

    private data class AutoClickPresetExportPayload(
        val format: String = EXPORT_FORMAT,
        val version: Int = EXPORT_VERSION,
        val exportedAtMillis: Long = 0L,
        val preset: AutoClickPresetConfig = AutoClickPresetConfig()
    )

    private companion object {
        private const val PREF_NAME = "auto_click"
        private const val CONFIG_KEY = "auto_click_config"
        private const val PRESET_KEY = "auto_click_presets"
        private const val EXPORT_FORMAT = "org.xiaobu.autoclick.auto_click_preset"
        private const val EXPORT_VERSION = 1
        private const val MAX_POINTS = 10
        private const val MAX_PRESETS = 12
        private const val MIN_INTERVAL_MS = 50
        private const val MAX_INTERVAL_MS = 60_000
        private const val MAX_DURATION_SECONDS = 24 * 60 * 60
        private const val MAX_CLICK_COUNT = 1_000_000
    }
}

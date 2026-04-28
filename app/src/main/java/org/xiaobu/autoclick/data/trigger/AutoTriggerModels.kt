package org.xiaobu.autoclick.data.trigger

import android.view.accessibility.AccessibilityEvent
import java.util.UUID
import org.xiaobu.autoclick.data.task.AutoTaskStep

enum class AutoTriggerEventType(
    val title: String,
    val description: String,
    val accessibilityEventType: Int,
    val defaultCooldownMs: Long
) {
    PAGE_NAVIGATED(
        title = "页面跳转",
        description = "窗口切换、新页面进入时触发",
        accessibilityEventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        defaultCooldownMs = 800L
    ),
    PAGE_RENDER_COMPLETED(
        title = "页面渲染完成",
        description = "页面内容刷新稳定后触发",
        accessibilityEventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        defaultCooldownMs = 1200L
    ),
    PAGE_SCROLLED(
        title = "页面滚动",
        description = "列表或容器滚动时触发",
        accessibilityEventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
        defaultCooldownMs = 600L
    ),
    VIEW_CLICKED(
        title = "控件点击",
        description = "目标应用内控件点击时触发",
        accessibilityEventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
        defaultCooldownMs = 400L
    ),
    TEXT_CHANGED(
        title = "文字变化",
        description = "输入框或文本内容变化时触发",
        accessibilityEventType = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        defaultCooldownMs = 500L
    );

    companion object {
        fun fromAccessibilityEvent(eventType: Int): AutoTriggerEventType? {
            return entries.firstOrNull { it.accessibilityEventType == eventType }
        }
    }
}

data class AutoTriggerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val packageName: String = "",
    val appLabel: String = "",
    val targetApps: List<AutoTriggerApp> = emptyList(),
    val eventType: AutoTriggerEventType = AutoTriggerEventType.PAGE_NAVIGATED,
    val eventTypes: List<AutoTriggerEventType> = listOf(eventType),
    val pageKeyword: String = "",
    val keywordExact: Boolean = false,
    val cooldownMs: Long = eventType.defaultCooldownMs,
    val steps: List<AutoTaskStep> = emptyList(),
    val updatedAt: Long = 0L
) {
    val effectiveEventTypes: List<AutoTriggerEventType>
        get() = eventTypes.ifEmpty { listOf(eventType) }.distinct()
}

data class AutoTriggerApp(
    val packageName: String = "",
    val appLabel: String = ""
)

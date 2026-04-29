package org.xiaobu.autoclick.data.task

import java.util.UUID

enum class AutoTaskActionType(
    val title: String,
    val requiresTarget: Boolean = false,
    val requiresSecondaryTarget: Boolean = false
) {
    WAIT("等待"),
    TAP("单击", requiresTarget = true),
    DOUBLE_TAP("双击", requiresTarget = true),
    LONG_PRESS("长按", requiresTarget = true),
    SWIPE("滑动", requiresTarget = true, requiresSecondaryTarget = true),
    BACK("返回"),
    HOME("主页"),
    RECENTS("最近任务"),
    NOTIFICATIONS("通知栏"),
    QUICK_SETTINGS("快捷设置"),
    LOCK_SCREEN("锁屏")
}

enum class AutoTaskTargetType(
    val title: String
) {
    COORDINATE("坐标"),
    NODE_TEXT("文字查找"),
    OCR_TEXT("文字识别"),
    IMAGE("图片识别")
}

data class AutoTaskTarget(
    val type: AutoTaskTargetType = AutoTaskTargetType.COORDINATE,
    val x: Int = 0,
    val y: Int = 0,
    val text: String = "",
    val index: Int = 1,
    val exact: Boolean = false,
    val imageUri: String = ""
)

data class AutoTaskStep(
    val id: String = UUID.randomUUID().toString(),
    val actionType: AutoTaskActionType = AutoTaskActionType.TAP,
    val title: String = "",
    val durationMs: Long = 300L,
    val delayAfterMs: Long = 300L,
    val target: AutoTaskTarget? = null,
    val secondaryTarget: AutoTaskTarget? = null
)

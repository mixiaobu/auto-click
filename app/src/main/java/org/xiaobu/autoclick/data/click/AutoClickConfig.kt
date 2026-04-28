package org.xiaobu.autoclick.data.click

data class AutoClickPointConfig(
    val id: String = "",
    val x: Int = 0,
    val y: Int = 0
)

data class AutoClickConfig(
    val intervalMillis: Int = 100,
    val durationSeconds: Int = 0,
    val points: List<AutoClickPointConfig> = emptyList()
)

data class AutoClickPresetConfig(
    val id: String = "",
    val name: String = "",
    val savedAt: Long = 0L,
    val config: AutoClickConfig = AutoClickConfig()
)

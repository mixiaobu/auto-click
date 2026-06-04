# 自动点击

自动点击是一款面向 Android 的辅助点击工具，主要提供连点器、触发器和自动点击器能力。项目基于无障碍服务执行点击、滑动和系统动作，通过悬浮窗提供运行控制，适合用于重复点击、简单自动化流程和指定应用场景下的触发执行。

> 请在合法、合规、明确授权的场景中使用本工具。无障碍服务仅用于执行用户配置的辅助操作。

## 功能概览

### 连点器

- 支持单点或多点顺序点击。
- 支持点击间隔、持续时长、点击次数限制。
- 支持悬浮指针定位和悬浮控制面板。
- 支持长按拖动调整指针顺序。
- 支持配置保存、加载、导入和导出。

### 触发器

- 支持选择一个或多个目标应用。
- 支持按页面跳转、页面渲染完成、页面滚动、控件点击、文字变化等无障碍事件触发。
- 支持配置冷却时间，避免同一规则频繁重复执行。
- 支持配置执行步骤，包括等待、点击、双击、长按、滑动、返回、主页、最近任务、通知栏、快捷设置、锁屏等动作。
- 支持坐标目标、节点文字查找、OCR 文字识别和图片识别。
- 支持触发器规则导入和导出。包含图片识别的规则导入后需要重新选择图片。

### 自动点击器

- 支持按步骤编排自动点击流程。
- 支持一次执行或重复执行。
- 支持步骤拖动排序。
- 支持坐标选择悬浮窗，方便从屏幕上直接选取目标坐标。
- 支持文字查找、OCR 文字识别、图片识别和系统动作。
- 支持配置保存、加载、导入和导出。
- 当前版本暂不包含录制功能。

### 设置

- 支持切换主题色。
- 内置赤、橙、黄、绿、青、蓝、紫主题，默认使用紫色主题。

## 权限说明

应用依赖以下核心权限：

- 悬浮窗权限：用于显示连点器、自动点击器和坐标选择等悬浮控制界面。
- 无障碍权限：用于执行点击、滑动、系统返回等辅助操作，并监听触发器事件。
- 通知权限和前台服务权限：用于部分悬浮窗服务和后台运行场景。
- 查询应用列表权限：用于选择触发器的目标应用。
- 网络权限：用于加载图片识别步骤中可能配置的网络图片地址。

首页会展示悬浮窗和无障碍权限状态。权限未开启时，功能页面不会进入，避免配置后无法执行。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Android AccessibilityService
- Android 悬浮窗服务
- Kotlin Coroutines
- Gson
- ML Kit Chinese Text Recognition
- R8 混淆和资源压缩

## 项目结构

```text
app/src/main/java/org/xiaobu/autoclick
├── MainActivity.kt                  # Compose 路由入口
├── AutoClickApp.kt                  # Application 和全局 Store
├── data
│   ├── click                        # 连点器配置和存储
│   ├── task                         # 自动点击器步骤模型和存储
│   ├── trigger                      # 触发器规则模型和存储
│   ├── app                          # 应用选择工具
│   └── settings                     # 应用设置
├── service
│   ├── AutoClickAccessibilityService.kt
│   ├── AutoClickOverlayService.kt
│   ├── AutoTaskOverlayService.kt
│   └── AutoTaskCoordinatePickerService.kt
└── ui
    ├── screen                       # 首页、连点器、触发器、自动点击器、设置页
    ├── component                    # 悬浮面板、步骤编辑器等组件
    └── theme                        # 主题色和 Material 主题
```

## 构建方式

在项目根目录执行：

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell 下可以执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

Release 构建：

```powershell
.\gradlew.bat :app:assembleRelease
```

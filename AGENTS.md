# AGENTS.md

## Scope

This file applies to the whole repository. The project is an Android app named `auto-click` / `自动点击`, implemented as a single `:app` module.

## Project Summary

`auto-click` is an Android accessibility automation tool. It provides:

- 连点器: sequential single/multi-point tapping from movable overlay pointers.
- 触发器: accessibility-event based rules for selected target apps.
- 自动点击器: ordered action steps with optional repeat execution.
- 设置: app theme selection.

The app performs gestures through `AutoClickAccessibilityService` and controls runtime UX through floating overlay services. Treat accessibility behavior and overlay permissions as core product boundaries, not incidental implementation details.

## Tech Stack

- Kotlin, official style.
- Android Gradle Plugin 9.2.1, Kotlin 2.4.0.
- Jetpack Compose, Material 3, Navigation Compose.
- Kotlin Coroutines.
- Gson for local JSON persistence/import/export.
- ML Kit Chinese Text Recognition for OCR targets.
- Android `AccessibilityService`, screenshots, gestures, and floating windows.
- Java 11 source/target compatibility.

Important Android config:

- `namespace` / `applicationId`: `org.xiaobu.autoclick`
- `minSdk`: 24
- `targetSdk`: 36
- `compileSdk`: Android 36.1
- Release enables R8 minification and resource shrinking.
- Native ABI filter currently includes `arm64-v8a`.

## Repository Layout

- `settings.gradle.kts`: root project name and `:app` inclusion.
- `build.gradle.kts`: root plugin aliases.
- `gradle/libs.versions.toml`: dependency and plugin versions.
- `app/build.gradle.kts`: Android module configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: permissions, services, and launcher activity.
- `app/src/main/java/org/xiaobu/autoclick/AutoClickApp.kt`: `Application`, global app context, toast helper, and Store singletons.
- `app/src/main/java/org/xiaobu/autoclick/MainActivity.kt`: Compose entry point, theme state, and navigation routes.
- `app/src/main/java/org/xiaobu/autoclick/data/`: local models, persistence, import/export, and installed-app utilities.
- `app/src/main/java/org/xiaobu/autoclick/service/`: accessibility execution and overlay services.
- `app/src/main/java/org/xiaobu/autoclick/ui/screen/`: feature screens.
- `app/src/main/java/org/xiaobu/autoclick/ui/component/`: overlay panels, coordinate picker, and action-step editor.
- `app/src/main/java/org/xiaobu/autoclick/ui/theme/`: Material theme options.

## Build And Verification

Run commands from the repository root.

PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

Instrumentation tests require a connected Android device or emulator:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

If a change touches only documentation, Gradle verification is usually unnecessary. For Kotlin, Gradle, manifest, resource, permission, or service changes, at minimum run `:app:assembleDebug`.

## Architecture Notes

### Application And Stores

`AutoClickApp` owns the app-wide Stores:

- `AutoClickStore`
- `AutoTriggerStore`
- `AutoTaskStore`
- `AppSettingsStore`

Stores use `SharedPreferences` and Gson. They also sanitize loaded/imported data, clamp numeric limits, repair missing IDs, trim text fields, and limit saved list sizes. Preserve this pattern when changing persistence. Do validation inside the relevant Store instead of relying only on UI inputs.

Import/export behavior is part of the product surface:

- `AutoClickStore` exports/imports preset payloads.
- `AutoTaskStore` exports/imports task payloads and clears image target URIs during export/import.
- `AutoTriggerStore` exports/imports trigger payloads and clears image target URIs during export/import.

### Compose Navigation

`MainActivity` defines simple string routes:

- `main`
- `autoClick`
- `autoTask`
- `trigger`
- `settings`

Feature screens receive callbacks from the route layer. Keep route ownership in `MainActivity` unless adding a larger navigation abstraction is clearly needed.

### Execution Services

`AutoClickAccessibilityService` is the execution engine. It owns:

- accessibility service enabled-state checks;
- gesture dispatch for tap, double-tap, long-press, and swipe;
- global actions: back, home, recents, notifications, quick settings, lock screen;
- node text lookup;
- OCR lookup from screenshots on Android 11+;
- image template matching from screenshots on Android 11+;
- trigger event filtering, cooldowns, and serialized trigger-step execution.

`AutoClickOverlayService` owns the 连点器 overlay: pointer windows, Compose control panel, quick control bubble, and the click loop.

`AutoTaskOverlayService` owns the 自动点击器 runtime overlay and executes the current draft task step-by-step by calling `AutoClickAccessibilityService.executeTaskStep`.

`AutoTaskCoordinatePickerService` owns the coordinate picker overlay and runs as a foreground service with special-use foreground-service type.

Do not duplicate gesture or target-resolution logic in screens or overlays. Route new action execution through `AutoClickAccessibilityService` where possible.

## Common Change Checklists

When adding or changing an `AutoTaskActionType`:

- Update `AutoTaskModels.kt`.
- Update `ActionStepEditorDialog.kt` validation, defaults, and editor controls.
- Update `AutoClickAccessibilityService.executeStep`.
- Update any overlay/screen text that displays step titles or execution status.
- Confirm Store sanitize/import/export behavior still handles old saved data.

When adding or changing an `AutoTaskTargetType`:

- Update `AutoTaskModels.kt`.
- Update `ActionStepEditorDialog.kt`.
- Update `AutoClickAccessibilityService.resolveTargetCenter`.
- Update task and trigger Store import/export behavior if the target contains local-only or sensitive data.

When changing trigger behavior:

- Keep package filtering, enabled-state checks, event type checks, cooldown checks, and "already executing" checks intact unless the requested feature explicitly changes them.
- Triggers currently ignore this app's own package events to avoid self-trigger loops.
- Trigger step execution is serialized with `triggerExecutionMutex`; preserve that unless concurrency has been designed deliberately.

When changing overlay behavior:

- Check `Settings.canDrawOverlays` before showing overlays.
- Detach views in service teardown paths.
- Keep Compose overlay views wired to lifecycle and saved-state owners.
- Avoid leaving overlay windows touchable while automated execution is running unless that behavior is intentional.

When changing permissions:

- Update `AndroidManifest.xml`.
- Update `README.md` permission notes if user-facing behavior changes.
- Update screen gating and permission launch actions in `PermissionActions.kt` / feature screens.

## UI Guidelines

- Use existing Compose + Material 3 style and theme helpers.
- Keep feature screens under `ui/screen` and reusable UI under `ui/component`.
- Prefer state hoisted at the screen level and persistence through Stores.
- Keep visible copy in Chinese unless there is an explicit reason to add another language.
- For iconography, use existing Compose Material icon dependencies where practical.

## Safety And Product Constraints

- This app depends on user-granted accessibility and overlay permissions. Do not hide permission prompts or silently bypass permission checks.
- Keep automation tied to user-configured points, steps, target apps, and triggers.
- Be careful with screenshot, OCR, image matching, and network image loading paths. Avoid logging sensitive screen contents or full image data.
- Do not edit, delete, regenerate, or move `xiaobu.jks` unless explicitly requested.
- Do not commit or depend on machine-local paths from `local.properties`.
- `.idea/`, `.gradle/`, `.kotlin/`, build outputs, and release APK output folders are local/generated and should not be edited for product changes.

## Testing Notes

Existing tests are placeholder unit/instrumentation examples. For behavior-heavy changes, prefer focused tests around Store sanitization/import/export where possible. Accessibility gestures, overlays, OCR, screenshots, and image matching usually need manual device/emulator verification because they depend on Android system services and permissions.

Manual verification for runtime features should cover:

- first-launch permission state on the main screen;
- enabling accessibility and overlay permissions;
- showing/hiding each overlay;
- starting/stopping 连点器;
- executing an 自动点击器 task once and in repeat mode;
- trigger execution in a selected target app;
- coordinate, node-text, OCR, and image targets if those paths changed.


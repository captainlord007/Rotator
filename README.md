# Rotator

**Screen-orientation controls for Android phones.** Rotator lets you choose an orientation for the whole phone or set individual rules for installed apps.

## Features

- **Whole-phone control:** Auto-rotate, Portrait, Reverse portrait, Landscape, and Reverse landscape.
- **Per-app rules:** Choose an orientation for each app with a launcher icon. Apps without a rule keep Android's normal rotation behavior.
- **Blocked orientations:** In Auto-rotate, block any of the four directions for the whole phone or a specific app.
- **Theme choices:** Light, dark, or follow the device theme.
- **Explicit Apply:** Selections remain drafts until Apply is pressed. Applying app rules turns off whole-phone rotation so only apps with saved rules receive a forced orientation.
- **Foreground service:** Keeps the selected rotation active and provides a notification action to stop it.

## Permissions

Rotator requests these settings from Android when they are needed:

- **Display over other apps** lets Rotator create a transparent, non-touchable orientation-control overlay.
- **Usage access** is needed only for per-app rules, so Rotator can identify which app is in the foreground.
- **Notifications** show that the rotation service is active and provide a stop action.

On Android 13 and later, a device may require **Allow restricted settings** before granting special access to an app installed from an APK. If Android shows that warning, open Rotator's App info, choose **⋮ > Allow restricted settings**, then return to Rotator and continue granting the requested access.

Rotator does not declare the Android `INTERNET` permission. Foreground-app detection uses Android's Usage Access setting on the device.

## Install

`Rotator.apk` is included in the repository for convenience. It is a **debug build** for testing, not a Play Store release build. When installing the APK directly, Android may ask you to allow installation from the browser or file manager you used to download it.

## Build from source

### Requirements

- Android Studio
- JDK 17
- Android SDK Platform 37

### Windows

```powershell
.\gradlew.bat assembleDebug
```

### macOS or Linux

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project details

- App version: **1.9** (`versionCode` 10)
- Minimum Android version: **Android 6.0 (API 23)**
- Target Android version: **Android 15 (API 35)**
- Compile SDK: **Android API 37**
- Gradle: **9.6.0**
- Android Gradle Plugin: **9.4.1**

## Android behavior and limitations

Rotator requests display orientation through Android's window system. Some apps, launchers, lock screens, or protected system surfaces may apply their own orientation policy, and behavior can vary by Android version and device manufacturer. Rotator cannot override every system restriction.

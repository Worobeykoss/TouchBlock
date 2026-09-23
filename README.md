# Touch Block

**English** | [Русский](README.ru.md)

An Android app that disables the touchscreen in selected rectangular areas of the screen.
Useful when a damaged screen registers phantom touches, or to protect part of the screen from accidental taps.

- Add as many zones as you need. Draw them with your finger right on top of the current screen, then move and resize them.
- Works on top of everything: in any app and in Settings, over the status bar, navigation buttons, keyboard and lock screen.
- A zone is tied to a physical area of the screen and stays in place when you rotate the phone.
- Zones can be highlighted in red or made invisible.
- A notification with Pause, Zones and Turn off buttons.
- English and Russian UI.

Requires Android 8.0 or newer.

## Installation

1. Download `TouchBlock.apk` from the [latest release](../../releases/latest) and install it.
2. Open the app, tap **Open Accessibility settings** and turn on the **Touch Block** service.
3. **Android 13+:** for apps installed from an APK rather than a store, the toggle is greyed out at first
   ("Restricted setting"). Try to turn the service on once, then open **App info** → ⋮ →
   allow restricted settings, and turn the service on again.
4. Tap **Set up zones**, draw your zones and turn blocking on.

## How it works

Blocking is handled by an accessibility service (`BlockerService`). It places transparent windows
the size of each zone using the `TYPE_ACCESSIBILITY_OVERLAY` window type. A touch that starts inside a zone
goes to that window and is not passed on. Touches outside the zones work as usual.

Why an accessibility service instead of the "Display over other apps" permission: since Android 12,
regular overlays (`TYPE_APPLICATION_OVERLAY`) are hidden by the system on many Settings screens,
in permission dialogs, in the package installer and in any app that asks for it (`setHideOverlayWindows`).
Accessibility service windows are not affected. They are also drawn above the status bar,
navigation bar, keyboard and lock screen.

The service does not subscribe to any accessibility events, does not read screen content and does not use the network.

Zones are stored as fractions of the screen in the device's natural orientation, so they stay on the same physical area of the glass when the screen rotates.

## If a zone covers something you need

- Tap **Pause** or **Turn off** in the notification.
- Set up the service shortcut in advance: Accessibility → Touch Block → volume keys shortcut.
  Then you can turn the service off by holding both volume keys for 3 seconds.
- After a restart the service doesn't run until the first unlock, so zones won't get in the way of entering your PIN.

## Limitations

- With gesture navigation, the system handles the swipe up from the bottom (Home) before any window.
  The Back gesture from the screen edge is suppressed inside a zone, but the system only allows this for up to 200dp of height on each edge.
  If that's a problem, switch to 3-button navigation: zones cover it completely.
- A touch that starts outside a zone keeps working even if the finger moves into the zone.

## Building

You need JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). Set the SDK path in `local.properties`:

```
sdk.dir=C:/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

If the build fails on Windows with `Unable to establish loopback connection`, the temp folder path is too long for the JDK.
Set a short `TEMP`/`TMP`, for example `C:\tmp`.

## Project structure

| File | Purpose |
|---|---|
| `BlockerService.kt` | Accessibility service: blocking windows, notification, screen rotation handling |
| `ZoneEditorView.kt` | Full-screen zone editor: drawing, moving, resizing |
| `ScreenGeometry.kt` | Converts zones between screen coordinates and the natural orientation |
| `MainActivity.kt` | Settings screen, enabling the service |
| `NotificationActionReceiver.kt` | Pause and Turn off buttons in the notification |
| `EditZonesActivity.kt` | Invisible activity behind the Zones notification button (collapses the shade) |

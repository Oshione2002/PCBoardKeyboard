# PCBoard Keyboard

Android IME prototype with a familiar mobile layout plus **Ctrl** and **Tab**.

## Included
- QWERTY and symbols layouts
- Ctrl shortcuts through Android key events
- Dedicated Tab key
- Shift and double-tap-style Shift state
- Three-word prediction strip
- Basic local autocorrection
- Backspace, comma, full stop, space and Enter
- Setup and testing activity
- No internet permission and no typed-text collection

## Build locally
1. Install Android Studio with Android SDK 36.
2. Open this folder.
3. Sync Gradle.
4. Build > Build APK(s).

## Build with GitHub Actions
Push the project to GitHub, open Actions, run **Build APK**, then download `PCBoard-debug-apk`.

## Limitations
This is an original keyboard, not Gboard and not a copy of Google's proprietary prediction, voice, translation, GIF or cloud services. Prediction and autocorrection in this version are deliberately small and local.

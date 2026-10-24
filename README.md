# VidShrink

VidShrink is an Android app that makes a smaller MP4 for sharing. Choose a video, select a maximum output size, and save or share the compressed result.

## What it does

- Targets a maximum final size from 5 MB through 100 MB.
- Offers 10 MB, 20 MB, and 50 MB presets.
- Uses H.264 by default. H.265 is available when the device has a hardware encoder.
- Limits output resolution to Auto, 1080p, 720p, or 480p without upscaling the video.
- Encodes AAC audio at 128 kbps.
- Checks the output size and retries with a lower bitrate when required.
- Saves default size, codec, and resolution settings.
- Accepts videos from Android's share sheet, then opens the result in the share chooser.

VidShrink processes the selected video on the device. The app does not request network access.

## Requirements

- Android 8.0 or later, API level 26.
- Android Studio with a compatible Android SDK, or a configured Gradle environment.

## Build

To build a debug APK on Windows, run:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Test

Run the unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

To run the Android UI tests, connect an Android device or start an emulator, then run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Repository notes

Debug APKs and local Android Studio state are intentionally ignored. Publish user-facing APKs through GitHub Releases instead of committing them to the repository.

## License

No license has been selected yet. Until a license is added, do not assume permission to reuse or distribute this code.

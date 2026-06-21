# PulseDeck Windows App Plan

## Goal

Ship PulseDeck as a first-class Windows desktop app while keeping the Android app stable. The Windows app lives in `windowsApp` as its own standalone Gradle project and uses Compose Multiplatform for desktop packaging.

## Current checkpoint

- `windowsApp` is a separate Gradle project, not included in the Android root build.
- The first desktop shell can scan a local music folder, list audio files, select a track, and open it with the Windows default player.
- Windows installers are configured through Compose Desktop native distributions.

## Build commands

```powershell
.\gradlew.bat -p windowsApp run
.\gradlew.bat -p windowsApp packageMsi
.\gradlew.bat -p windowsApp packageExe
```

Expected installer outputs:

```text
windowsApp\build\compose\binaries\main\msi\
windowsApp\build\compose\binaries\main\exe\
```

## Porting phases

1. Extract shared core models
   Move platform-neutral models, playback planning, queue rules, recommendation scoring, and settings value objects from `app` into a new shared Kotlin module.

2. Replace Android library access
   Keep Android on `MediaStore`; add Windows scanning through Java NIO plus a metadata reader for title, artist, album, duration, and embedded artwork.

3. Add a real Windows audio engine
   Android playback currently depends on Media3 and native Android audio paths. Windows needs its own engine abstraction, then a desktop implementation for playback, seeking, volume, queue transitions, and output diagnostics.

4. Port the main PulseDeck surfaces
   Bring over the library, albums, queue, player, audio console, settings, PulseRadio, and PremiumDeck surfaces after shared state and playback are platform-neutral.

5. Package and release
   Add icons, installer metadata, Windows signing, smoke tests, and release notes for `.msi` and `.exe` builds.

## Boundary decisions

- Do not make the Android module depend on desktop APIs.
- Do not include the Windows app in the Android root Gradle project until there is a deliberate shared-core module.
- Do not copy large Android-only Compose files into Windows until the platform dependencies are separated.
- Prefer shared Kotlin modules for logic, and platform modules for storage, scanning, playback, permissions, and packaging.

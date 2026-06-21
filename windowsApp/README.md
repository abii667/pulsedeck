# PulseDeck Windows

Standalone Windows desktop project for PulseDeck.

The Android application stays in the root `app` module. This project has its own Gradle settings and build file, so it can evolve like a native Windows desktop app without pulling Android-only dependencies into the desktop build.

Run from the repository root:

```powershell
.\gradlew.bat -p windowsApp run
```

Build installers from the repository root:

```powershell
.\gradlew.bat -p windowsApp packageMsi
.\gradlew.bat -p windowsApp packageExe
```

Installer outputs:

```text
windowsApp\build\compose\binaries\main\msi\PulseDeck-0.1.0.msi
windowsApp\build\compose\binaries\main\exe\PulseDeck-0.1.0.exe
```

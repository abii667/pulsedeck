# PulseDeck Public Export

This folder is a curated public-safe export of the PulseDeck workspace. It keeps the Android app source, the Gradle build files, and selected tooling, while leaving out private project wiring, generated artifacts, and internal operational material.

## Overview

PulseDeck is currently published as an Android-focused music app project with:

- an Android app in `app/`
- shared Gradle build configuration at the repository root
- selected tooling in `tools/`

This public repository is intentionally scoped for open collaboration. Some private or project-linked runtime integrations are deliberately omitted or disabled in this export.

## What This Export Keeps

- Android app source in `app/`
- Gradle wrapper and shared build configuration
- Selected developer tooling in `tools/`
- CircleCI config in `.circleci/`

## What This Export Leaves Out

- Firebase project config such as `app/google-services.json`
- Cloud Functions/backend deployment files
- local machine config such as `local.properties`
- generated builds, temporary folders, screenshots, memory dumps, and QA artifacts
- internal rollout/admin documentation

## Public Export Notes

Some network-backed integrations are intentionally disconnected in this export so it does not point at the original live project or reuse high-risk endpoints by default. The copied source keeps those code paths visible for contributors, but the hosted chart snapshot URLs, resolver endpoint lists, and lyrics-provider base URLs are blanked in the export copy.

Firebase dependencies remain in the Android build, but the project-specific config file is intentionally absent. The app is expected to compile without it, with Firebase-backed beta/runtime behavior staying unavailable until a fresh public or private Firebase project is configured.

## Quick Start

### Prerequisites

- JDK 21
- Android SDK
- Android SDK platform for `compileSdk = 36`
- Android NDK `27.0.12077973`
- CMake `3.22.1`

Set `ANDROID_HOME` and `ANDROID_SDK_ROOT`, or create an untracked `local.properties`, before building the Android app.

## License

This export is released under `GPL-3.0-or-later`.

That is the conservative choice for the current codebase because the Android app still depends on `NewPipe Extractor`, which the upstream project publishes under GPL terms. If you want to move this repo to a permissive license later, first remove or replace the GPL-governed dependency path and then re-evaluate the dependency graph.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Repository Layout

- `app/` Android application source
- `gradle/` shared Gradle catalog and wrapper support files
- `tools/` selected helper scripts and TinyRec training utilities
- `docs/` export-scope, licensing, and publish notes

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup notes, testing expectations, and public-export safety rules before opening an issue or pull request.

## Security

See [SECURITY.md](SECURITY.md) for vulnerability reporting guidance. Please do not post sensitive security details in a public issue.

## Before Publishing

1. Review [docs/EXPORT_SCOPE.md](docs/EXPORT_SCOPE.md).
2. Review [docs/PUBLISH_CHECKLIST.md](docs/PUBLISH_CHECKLIST.md).
3. Review [docs/LICENSE_DECISION.md](docs/LICENSE_DECISION.md).

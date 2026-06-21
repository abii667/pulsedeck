# Contributing to PulseDeck

Thanks for contributing to PulseDeck.

This repository is a curated public export of the project. It is meant to be easy to build on its own and safe to collaborate on publicly, while leaving out private deployment wiring and machine-specific files.

## Before You Start

Please read these first:

- [README.md](README.md)
- [docs/EXPORT_SCOPE.md](docs/EXPORT_SCOPE.md)
- [docs/LICENSE_DECISION.md](docs/LICENSE_DECISION.md)
- [docs/PUBLISH_CHECKLIST.md](docs/PUBLISH_CHECKLIST.md)

For larger changes, feature proposals, or architecture shifts, open an issue before spending a lot of time on implementation.

## Development Setup

### Prerequisites

- JDK 21
- Android SDK installed locally
- Android SDK platform for `compileSdk = 36`
- Android NDK `27.0.12077973`
- CMake `3.22.1`

Set `ANDROID_HOME` and `ANDROID_SDK_ROOT`, or create an untracked `local.properties` file for local Android builds.

### Important Public Export Notes

- `app/google-services.json` is intentionally not included in this repo.
- Firebase-related code is still present, but project-specific runtime wiring is intentionally absent.
- Some network-backed endpoints are blanked in this export on purpose. Do not restore live project endpoints, tokens, or private service configuration in a public pull request.
- The current repo license is `GPL-3.0-or-later`. If a contribution changes dependency licensing, call that out clearly in the PR.

## Build And Test

From the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat -p windowsApp run
```

Optional Windows packaging commands:

```powershell
.\gradlew.bat -p windowsApp packageMsi
.\gradlew.bat -p windowsApp packageExe
```

Instrumentation tests live under `app/src/androidTest` and may require a configured emulator or physical device.

## Contribution Guidelines

- Keep pull requests focused. Small, reviewable changes are much easier to merge.
- Add or update tests when behavior changes.
- Update docs when setup, build steps, exported scope, or contributor expectations change.
- Include screenshots or short recordings for visible UI changes when possible.
- Call out any dependency, license, or third-party service changes in the PR description.
- Never commit secrets, service credentials, signing keys, local machine config, or generated build output.

## Good Contribution Areas

- Bug fixes
- Android UI and playback improvements
- Windows desktop improvements
- Tests and reliability work
- Documentation and onboarding improvements
- Tooling and developer workflow cleanup

## Pull Request Checklist

Before opening a PR, please confirm:

- The change builds locally for the area you touched.
- Relevant tests were added or run when practical.
- No private config or generated artifacts were committed.
- The PR description explains what changed and how it was verified.

If you are unsure whether something belongs in the public export, open an issue first and we can sort it out together.

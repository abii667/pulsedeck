# Export Scope

## Included

- `app/`
- `windowsApp/`
- `gradle/`
- `.circleci/`
- `tools/audio_thread_safety_scan.*`
- `tools/gradle_audio_build_checks.*`
- `tools/tinyrec/`
- root Gradle wrapper and build files
- `THIRD_PARTY_NOTICES.md`

## Excluded

- `.git/`
- `.agents/`
- `.codex-remote-attachments/`
- `.gradle/` and `.kotlin/` from the original workspace
- `.venv-tinyrec/`
- `.youtube_resolver/`
- `functions/`
- `artifacts/`
- `build/`
- `app/build/`
- `windowsApp/build/`
- `tmp/`, `tmp_*`, and other scratch/export folders
- screenshots, XML captures, APKs, logs, heap dumps, and other generated evidence
- `local.properties`
- `.firebaserc`
- `firebase.json`
- `firestore.rules`
- `app/google-services.json`
- internal/private docs from the original `docs/` folder

## Export Patches Applied

- Removed the copied `app/google-services.json`.
- Reworded the Gradle Firebase message to match public-export behavior.
- Blank-disabled project-linked hosted chart URLs in `StreamingRecommendations.kt`.
- Blank-disabled external resolver endpoint lists in `StreamingRecommendations.kt` and `YouTubeNetworkRuntime.kt`.
- Blank-disabled the Innertube player endpoint in `YouTubeAudioResolver.kt`.
- Blank-disabled lyrics-provider base URLs in `LyricsRuntime.kt`.

## License Decision

The export now includes a root `LICENSE` file with the GNU GPL v3 text, and the repo-level choice for this export is `GPL-3.0-or-later`.

That choice is based on the current dependency graph, especially the active `NewPipe Extractor` dependency. If you want to pursue a permissive license later, remove or replace that GPL-governed path first and then re-audit the remaining dependencies and service terms.

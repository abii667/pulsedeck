# Publish Checklist

1. Confirm that `GPL-3.0-or-later` is the license you want for this public export.
2. Initialize a fresh Git repository inside `public-export`.
3. Run a build or test pass from the export folder.
4. Review `app/src/main/java/com/pulsedeck/app/settings/model/ThirdPartyLicenseInventory.kt` for the release/legal notes that still apply to contributors.
5. Decide whether you want to keep the Firebase beta code visible but unconfigured, or remove it in a second cleanup pass.
6. Replace any blanked integration endpoints only after you are comfortable publishing those integrations.
7. If you later want a permissive repo license, remove or replace the GPL-governed dependency path first.

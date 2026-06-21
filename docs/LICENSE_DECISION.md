# License Decision

## Chosen License

This public export is licensed as `GPL-3.0-or-later`.

## Why This Choice

The current Android app dependency graph still includes `NewPipe Extractor`, and upstream publishes that project under GPL terms. For this export, `GPL-3.0-or-later` is the conservative repo-level choice that best matches that dependency reality.

## What This Means

- Contributors can read, modify, and redistribute the source under GPL terms.
- If redistributed binaries are produced, the distributor should also satisfy the GPL's corresponding-source obligations.
- Some third-party services and SDKs used by the project still have their own separate terms; the repo license does not replace those third-party terms.

## Future Permissive Path

If you want a permissive license later, the clean route is:

1. Remove or replace the GPL-governed dependency path, especially `NewPipe Extractor`.
2. Re-audit the remaining dependencies and service-backed integrations.
3. Choose a new repo license only after that re-audit.

## Practical Note

This file is a project decision note, not legal advice. For a commercial release or broad binary redistribution plan, a lawyer should review the dependency and service-term mix.

# Security Policy

## Supported Branch

Security fixes are tracked against the current `main` branch of this public export.

Older snapshots and untagged local copies may not receive coordinated fixes.

## Reporting A Vulnerability

If you believe you found a security issue in this repository:

1. Use GitHub's private vulnerability reporting for this repository if the `Report a vulnerability` option is available.
2. If private reporting is not available, do not post exploit details, secrets, tokens, private config, or step-by-step abuse instructions in a public issue.
3. In that case, contact the repository owner directly through GitHub first and ask for a private reporting channel.

Please include:

- the affected area, file, or feature
- the conditions required to reproduce the problem
- the potential impact
- any proof of concept that can be shared safely
- logs or screenshots with secrets and personal data removed

## Public Repo Scope Notes

This repository is a curated public export. Some integrations are intentionally disconnected here, and private deployment files are intentionally omitted.

Please do not publish or request:

- private Firebase project files such as `app/google-services.json`
- signing keys, tokens, credentials, or machine-specific config
- private service endpoints that were deliberately blanked in the export
- sensitive user data or private testing artifacts

## Response Expectations

Security reports are handled on a best-effort basis.

- Initial triage target: within 7 days
- Follow-up timing depends on severity, reproducibility, and maintainer availability

## Responsible Disclosure

Please avoid public disclosure until the issue has been reviewed and a fix or mitigation path is understood.

If the report concerns infrastructure or private services that are not part of this public export, do not test or probe those systems further from the public repo context.

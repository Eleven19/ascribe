# Contributing

Notes and short articles for anyone working on this codebase (including future you). They explain design choices, integrations, and how to change or extend things.

## Articles

- **[Copilot coding agent](copilot-coding-agent.md)** — `copilot-setup-steps` workflow (JDK 21, Mill, submodules) and optional firewall allowlist notes for PR reviews.
- **[Maintaining the Changelog](changelog.md)** — How `CHANGELOG.md` is structured, how `[Unreleased]` is maintained, and what has to exist before a release can be published.
- **[Logging with Scribe](logging-with-scribe.md)** — How Kyo’s logging works and how we switched this app to use Scribe as the backend (implementing `Log.Unsafe`, wiring with `Log.let`).
- **[Releasing](releasing.md)** — How to publish to Maven Central (release and SNAPSHOT) and how the GitHub release workflow builds native archives for `mise`.

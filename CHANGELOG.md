# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

- Added Maven Central publishing via `SonatypeCentralPublishModule` with `publish-central.yml` and `publish-snapshot.yml` workflows.
- Added `PublishSupport` Mill trait for consistent POM metadata and Sonatype Central integration.
- Added `compute-publish-version.sh` script for branch-aware SNAPSHOT versioning.

### Changed

- Changed interceptor selection to use `mill-interceptor intercept <tool> ...`
  instead of `INTERCEPTED_BUILD_TOOL`.

### Fixed

### Documentation

- Documented the `intercept` subcommand and `mvn` alias behavior.

### CI

- Added `publish-central.yml` workflow for tag-driven and manual Maven Central releases.
- Added `publish-snapshot.yml` workflow for manual SNAPSHOT publishing.

## [0.1.0] - 2026-03-14

### Added

- Added Maven, Gradle, and sbt interceptor support with command parsing and task mapping.
- Added Scribe-backed logging and command-line entrypoint wiring for the interceptor app.

### Changed

- Added native release packaging support through Mill so platform archives can be published to GitHub Releases.

### Fixed

- Improved command translation coverage with focused unit tests across the Maven, Gradle, and sbt shims.

### Documentation

- Added contributor docs for native release packaging and release workflow behavior.

### CI

- Added GitHub CI for build and test validation.

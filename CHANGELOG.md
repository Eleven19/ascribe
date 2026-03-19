# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

- Added ASG (Abstract Semantic Graph) module with full type hierarchy (23 block types, 5 inline types), Schema.derived JSON codecs with discriminator, and private constructor + smart apply pattern.
- Added AST-to-ASG bridge converter for headings, paragraphs, lists, inline formatting, sections, listing/sidebar blocks, document headers, and attribute entries.
- Added AST and ASG Visitor traits with hierarchical defaults, stack-safe foldLeft/foldRight via trampolining, collect/collectPostOrder/count, and extension methods on Node/AstNode.
- Added AST and ASG construction DSLs for ergonomic node building in tests and general use.
- Added full AsciiDoc table support (Phases 1-3):
  - Basic PSV tables with `|===` delimiter and `|` cell separator.
  - Column specifications via `cols` attribute (widths, alignment, vertical alignment, multipliers).
  - Header/footer row detection (implicit via blank line, explicit via `%header`/`%footer`/`%noheader`).
  - Block title (`.Title`) and display attributes (`frame`, `grid`, `stripes`).
  - Column and cell content styles (`a`/`d`/`e`/`h`/`l`/`m`/`s` operators).
  - Cell spanning (`2+|`, `.3+|`, `2.3+|`) and cell duplication (`3*|`).
  - CSV (`,===`) and DSV (`:===`) data format tables.
  - Nested tables (`!===`) inside `a`-style cells with `CellContent` ADT (Inlines | Blocks).
- Added `@specStatus` annotation for tracking ASG type provenance (TCK, SpecDerived, Custom).
- Added opaque types for type safety: `ColSpan`, `RowSpan`, `DupCount`, `CellStyle`, `ColumnSpec`, and AST `CellSpecifier` types.
- Added 28 custom table TCK test cases covering all table features.
- Added `AstNode` sealed trait as common base for all AST types.
- Added `Location` case class (replacing type alias) with `Schema.transform` for TCK-compatible JSON array serialization.
- Added TCK runner wired to real parser pipeline (`Ascribe.parse → AstToAsg.convert → AsgCodecs.encode`).
- Added Maven Central publishing via `SonatypeCentralPublishModule` with `publish-central.yml` and `publish-snapshot.yml` workflows.
- Added `PublishSupport` Mill trait for consistent POM metadata and Sonatype Central integration.
- Added `compute-publish-version.sh` script for branch-aware SNAPSHOT versioning.

### Changed

- Migrated package namespace from `io.github.eleven19.ascribe` to `io.eleven19.ascribe` across all modules.
- Replaced 627-line manual ASG JSON codec with ~30 lines using `Schema.derived` + `JsonBinaryCodecDeriver`.
- Converted ASG sealed abstract classes to sealed traits with `derives Schema` and private constructors.
- Converted Mill build configs from `package.mill` (Scala) to `package.mill.yaml` (YAML) format.
- Changed interceptor selection to use `mill-interceptor intercept <tool> ...`
  instead of `INTERCEPTED_BUILD_TOOL`.

### Fixed

- Fixed end positions to be inclusive (last char, not past-end) matching TCK expectations.
- Fixed multi-line paragraphs to produce single Text node with `\n`-joined content.
- Fixed JSON serialization to omit None/default fields (`withTransientDefaultValue(true)`).

### Documentation

- Added GitHub Pages documentation site with Scala 3 scaladoc API docs and prose content.
- Added documentation guides for parsing, ASG model, and visitor pattern.
- Added contributing guides for development setup and TCK compliance.
- Documented the `intercept` subcommand and `mvn` alias behavior.

### CI

- Added `docs.yml` GitHub Actions workflow for deploying documentation to GitHub Pages.
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

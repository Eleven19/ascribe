# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

### Changed

### Fixed

### Documentation

- Updated README with current v0.2.1 versions, usage examples, module table, and link to documentation site.
- Added ascribe logo to repository.
- Fixed broken logo image link in README.
- Updated getting-started guide with correct artifact coordinates and versions.

### CI

## [0.2.1] - 2026-03-25

### Fixed

- Added `PublishSupport` to `ascribe-asg`, `ascribe-bridge`, and `ascribe-pipeline` modules so all library artifacts are published to Maven Central.

## [0.2.0] - 2026-03-25

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
- Added `@specStatusInfo` annotation for tracking ASG type provenance (TCK, SpecDerived, Custom).
- Added opaque types for type safety: `ColSpan`, `RowSpan`, `DupCount`, `CellStyle`, `ColumnSpec`, and AST `CellSpecifier` types.
- Added 28 custom table TCK test cases covering all table features.
- Added `AstNode` sealed trait as common base for all AST types.
- Added `Location` case class (replacing type alias) with `Schema.transform` for TCK-compatible JSON array serialization.
- Added TCK runner wired to real parser pipeline (`Ascribe.parse → AstToAsg.convert → AsgCodecs.encode`).
- Added Maven Central publishing via `SonatypeCentralPublishModule` with `publish-central.yml` and `publish-snapshot.yml` workflows.
- Added `PublishSupport` Mill trait for consistent POM metadata and Sonatype Central integration.
- Added `compute-publish-version.sh` script for branch-aware SNAPSHOT versioning.
- Added `DocumentPath` and `DocumentTree` types for multi-document processing.
- Added `ascribe-pipeline` module with `Renderer`, `RewriteRule`, `Source`, `Sink`, and `Pipeline` abstractions using Kyo effects.
- Added `FileSource` and `FileSink` for filesystem-based document I/O.
- Added `IncludeProcessor` for `include::` directive preprocessing.
- Added `AsciiDocRenderer` and `AsgJsonRenderer` for multi-target rendering.
- Added pipeline DSL for ergonomic pipeline construction.
- Added full delimited block support (listing, literal, sidebar, comment, passthrough, example, open) with variable-length fences and nesting.
- Added source block support with language and title attributes.

### Changed

- Migrated package namespace from `io.github.eleven19.ascribe` to `io.eleven19.ascribe` across all modules.
- Replaced 627-line manual ASG JSON codec with ~30 lines using `Schema.derived` + `JsonBinaryCodecDeriver`.
- Converted ASG sealed abstract classes to sealed traits with `derives Schema` and private constructors.
- Converted Mill build configs from `package.mill` (Scala) to `package.mill.yaml` (YAML) format.
- Centralized compiler settings into shared `CommonScalaModule` and `CommonScalaTestModule` meta-build traits, following the `mill-interceptor` pattern.
- Enabled `-Werror`, `-deprecation`, and `-feature` compiler flags across all modules.
- Replaced deprecated `parsley.combinator.{many, some}` with `Parsley.{many, some}`.
- Replaced deprecated `kyo.IO` with `kyo.Sync` across pipeline module.
- Dropped `Block` suffix from AST type names for conciseness.

### Fixed

- Fixed end positions to be inclusive (last char, not past-end) matching TCK expectations.
- Fixed multi-line paragraphs to produce single Text node with `\n`-joined content.
- Fixed JSON serialization to omit None/default fields (`withTransientDefaultValue(true)`).
- Fixed non-exhaustive pattern match in integration test steps.
- Fixed case-insensitive class name clash between `SpecStatus` enum and `specStatus` annotation.

### Documentation

- Added GitHub Pages documentation site with Scala 3 scaladoc API docs and prose content.
- Added documentation guides for parsing, ASG model, and visitor pattern.
- Added contributing guides for development setup and TCK compliance.

### CI

- Added `docs.yml` GitHub Actions workflow for deploying documentation to GitHub Pages.
- Added `publish-central.yml` workflow for tag-driven and manual Maven Central releases.
- Added `publish-snapshot.yml` workflow for manual SNAPSHOT publishing.
- Added TCK, ASG, and bridge tests to CI pipeline.

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

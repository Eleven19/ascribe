# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses tags like `v0.1.0` while changelog versions omit the
leading `v`.

## [Unreleased]

### Added

- Implemented full **`ascribe-pipeline-ox`** API parity with `ascribe-pipeline-kyo`: `Pipeline`, `Source`, `Sink`, `FileSource` / `FileSink`, `IncludeResolver`, `AsciiDocRenderer`, `AsgJsonRenderer`, `dsl`, and ZIO tests mirroring the Kyo suite. File and include stages run under Ox `supervised` scopes; results use `Either[PipelineError, *]` instead of Kyo effects.
- Expanded **`ascribe-pipeline-ox`** tests with edge-case suites: empty trees, parse/sink error propagation, `Source`/`Sink` units, file I/O failure modes (non-directory, empty scan, nested output paths), include depth and parse failures, `OxRuntime` / `PipelineOp`, `dsl` integration, and extra `AsgJsonRenderer` checks.

### Changed

### Fixed

### Documentation

### CI

## [0.3.0] - 2026-03-27

### Added

- Added Concrete Syntax Tree (CST) layer with full parser retargeting and pipeline integration.
- Added attribute references and substitution (`{attr-name}`) with built-in attributes (`{empty}`, `{sp}`, `{nbsp}`, `{zwsp}`), document header and body-level attribute entries, and unset operations.
- Added admonition paragraph blocks (NOTE, TIP, IMPORTANT, CAUTION, WARNING) with lowering to AST `Admonition` nodes.
- Added inline link support for 4 AsciiDoc link forms: bare autolinks (`https://example.com`), URL macros (`https://example.com[text]`), `link:` macros (`link:path[text]`), and `mailto:` macros (`mailto:addr[text]`).
- Added `Link` AST node with `LinkVariant`/`MacroKind` enum hierarchy, lazy `scheme` derivation, and `Link.Scheme` pattern match extractor.
- Added `CstLink` sealed trait grouping all CST link node types with shared `target` def.
- Added constrained italic (`_text_`) and monospace (`` `text` ``) inline formatting with spec-compliant word-boundary enforcement using Parsley Ref-based state tracking.
- Added `ConstrainedItalic` and `ConstrainedMono` AST node types, matching the existing `ConstrainedBold` pattern.
- Added generic inline macro attribute parsing (`CstMacroAttrList`) for bracket content with two-phase detection (comma/equals outside quotes triggers attribute mode).
- Added domain-typed link attributes: `ElementId`, `WindowTarget`, `CssRole` opaque types with extractors, `LinkOption` enum (`NoFollow`, `NoOpener`), and `LinkAttributes` case class with `OpensInNewWindow` extractor.
- Added `^` caret shorthand for `window=_blank` in link macros; `window=_blank` implicitly adds `NoOpener` (security best practice).
- Added `visitLink` grouping in both CST and AST visitor hierarchies.

### Changed

- Added `constrained: Boolean` field to `CstItalic` and `CstMono` CST nodes, matching the existing `CstBold` pattern.
- Retrofitted constrained bold (`*text*`) parser with word-boundary enforcement (previously had no boundary checking).
- Replaced `bracketedInlineContent` parser with `macroAttrList` two-phase parser for structured attribute extraction.
- CST link nodes (`CstUrlMacro`, `CstLinkMacro`, `CstMailtoMacro`) now carry `CstMacroAttrList` instead of `List[CstInline]`.
- AST `Link` node now carries `LinkAttributes` field (defaults to `LinkAttributes.empty`).

### Fixed

- Fixed `lowerHeader` to use `lowerInlines` for document title content instead of `toString` fallback, so links, bold, italic etc. in document titles are now lowered correctly.

### Documentation

- Updated README with current v0.2.1 versions, usage examples, module table, and link to documentation site.
- Added ascribe logo to repository.
- Fixed broken logo image link in README.
- Updated all docs to reflect current code: correct AST type names, pipeline module, delimited block types, Mill `mvnDeps`/`Seq` syntax.
- Fixed getting-started guide with correct artifact coordinates, versions, and pipeline examples.
- Fixed architecture doc to include pipeline module and all delimited block types.
- Fixed parsing guide to document all delimited block types and correct table parser location.
- Fixed visitor guide `TableBlock` references to `Table`.

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

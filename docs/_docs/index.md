---
title: Ascribe
---

# Ascribe

Ascribe is a Scala 3 AsciiDoc parser that produces a type-safe Abstract Semantic Graph (ASG). Built on [Parsley](https://github.com/j-mie6/parsley) parser combinators, it provides a complete pipeline from raw AsciiDoc source text to structured, JSON-serializable document models.

## Key Features

- **Parsley-based parser** -- Combinatorial parsing with clean error messages, position tracking, and atomic backtracking.
- **Full ASG type hierarchy** -- Sealed trait hierarchy covering blocks (Section, Paragraph, Listing, Sidebar, List, Table, ...) and inlines (Text, Span, Ref, CharRef, Raw).
- **Table support** -- Full AsciiDoc table parsing including PSV, CSV, and DSV formats, column specs, cell specifiers (style, spanning, duplication), header/footer rows, frame/grid/stripes attributes, and nested tables.
- **Schema-derived JSON codecs** -- Automatic JSON encoding/decoding via `zio-blocks-schema` with `DiscriminatorKind.Field("name")`, matching the official AsciiDoc TCK format.
- **Stack-safe visitor and fold** -- Trampolined tree traversals (`foldLeft`, `foldRight`, `collect`, `count`) on both AST and ASG, with hierarchical visitor traits.
- **TCK compliance** -- 78 test scenarios passing (22 official TCK + 28 custom table + 28 other).

## Quick Links

- [Getting Started](getting-started.md) -- Add the dependency, parse your first document, and encode to JSON.
- [Architecture](architecture.md) -- Pipeline overview, module structure, and design decisions.
- [Parser Guide](guides/parsing.md) -- How block and inline parsers work.
- [ASG Model Guide](guides/asg.md) -- The Abstract Semantic Graph type system.
- [Visitor & Fold Guide](guides/visitor.md) -- Tree traversal patterns.
- [Development Setup](contributing/development.md) -- Build, test, and format.
- [TCK Compliance](contributing/tck.md) -- How TCK tests work and how to add coverage.

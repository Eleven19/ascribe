---
title: Ascribe
layout: index
---

# Ascribe

Ascribe is a Scala 3 AsciiDoc parser that produces a type-safe Abstract Semantic Graph (ASG). Built on [Parsley](https://github.com/j-mie6/parsley) parser combinators, it provides a complete pipeline from raw AsciiDoc source text to structured, JSON-serializable document models.

## Key Features

- **Parsley-based parser** -- Combinatorial parsing with clean error messages, position tracking, and atomic backtracking.
- **Full ASG type hierarchy** -- Sealed trait hierarchy covering blocks (Section, Paragraph, Listing, Sidebar, List, ...) and inlines (Text, Span, Ref, CharRef, Raw).
- **Schema-derived JSON codecs** -- Automatic JSON encoding/decoding via `zio-blocks-schema` with `DiscriminatorKind.Field("name")`, matching the official AsciiDoc TCK format.
- **Stack-safe visitor and fold** -- Trampolined tree traversals (`foldLeft`, `foldRight`, `collect`, `count`) on both AST and ASG, with hierarchical visitor traits.
- **TCK compliance** -- All 13 AsciiDoc Technology Compatibility Kit test cases passing.

## Quick Links

- [Getting Started](getting-started.md) -- Add the dependency, parse your first document, and encode to JSON.
- [Architecture](architecture.md) -- Pipeline overview, module structure, and design decisions.
- [Parser Guide](guides/parsing.md) -- How block and inline parsers work.
- [ASG Model Guide](guides/asg.md) -- The Abstract Semantic Graph type system.
- [Visitor & Fold Guide](guides/visitor.md) -- Tree traversal patterns.
- [Development Setup](contributing/development.md) -- Build, test, and format.
- [TCK Compliance](contributing/tck.md) -- How TCK tests work and how to add coverage.

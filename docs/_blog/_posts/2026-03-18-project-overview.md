---
title: "Introducing Ascribe: A Scala 3 AsciiDoc Parser"
author: Ascribe Team
date: 2026-03-18
---

# Introducing Ascribe

We are pleased to announce Ascribe, a Scala 3 AsciiDoc parser that produces a type-safe Abstract Semantic Graph (ASG).

Ascribe is designed for applications that need to process AsciiDoc documents programmatically -- documentation toolchains, content management systems, static site generators, and language tooling.

## Highlights

- **Full TCK compliance** -- All 13 test cases from the official AsciiDoc Technology Compatibility Kit pass, ensuring compatibility with the AsciiDoc specification.

- **Parsley-based parser** -- Built on the Parsley parser combinator library, providing clean error messages with position tracking and atomic backtracking.

- **Schema-derived JSON codecs** -- ASG serialization is fully automatic via `zio-blocks-schema` with `DiscriminatorKind.Field("name")`. No hand-written JSON codecs needed.

- **Stack-safe visitors** -- Both AST and ASG modules include trampolined fold operations (`foldLeft`, `foldRight`, `collect`, `count`) that are safe on arbitrarily deep document trees.

- **Builder DSLs** -- Concise construction syntax for both AST and ASG nodes, making tests and programmatic document construction straightforward.

## Getting Started

```scala
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.bridge.AstToAsg
import io.eleven19.ascribe.asg.AsgCodecs

val doc = Ascribe.parse("= Hello\n\nWorld.\n").get
val asg = AstToAsg.convert(doc)
val json = AsgCodecs.encode(asg)
```

See the [Getting Started guide](/docs/getting-started) for full setup instructions.

## What's Next

We are working toward broader AsciiDoc coverage, including tables, description lists, admonitions, and more inline markup variants. Contributions are welcome.

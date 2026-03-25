---
title: Getting Started
---

# Getting Started

## Add the Dependency

In your `build.mill` (Mill build tool), add Ascribe as a dependency:

```scala
def ivyDeps = Agg(
  ivy"io.eleven19.ascribe::ascribe:0.2.1",
  ivy"io.eleven19.ascribe::ascribe-asg:0.2.1",
  ivy"io.eleven19.ascribe::ascribe-bridge:0.2.1",
  ivy"io.eleven19.ascribe::ascribe-pipeline:0.2.1"
)
```

## Parse an AsciiDoc Document

The main entry point is `Ascribe.parse`, which takes a raw AsciiDoc string and returns a Parsley `Result`:

```scala
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.Document

Ascribe.parse("= Title\n\nParagraph text.\n") match
  case parsley.Success(doc) => println(doc)
  case parsley.Failure(msg) => println(s"Parse error: $msg")
```

The returned `Document` is an AST (Abstract Syntax Tree) with full source position tracking.

## Convert to ASG

The bridge module converts the parser's AST into the ASG (Abstract Semantic Graph), which matches the official AsciiDoc TCK schema:

```scala
import io.eleven19.ascribe.bridge.AstToAsg

val astDoc = Ascribe.parse("= Title\n\nParagraph text.\n").get
val asgDoc = AstToAsg.convert(astDoc)
```

## Encode to JSON

Use `AsgCodecs` to serialize the ASG to JSON:

```scala
import io.eleven19.ascribe.asg.AsgCodecs

val json = AsgCodecs.encode(asgDoc)
println(json)
```

This produces JSON matching the AsciiDoc TCK expected format, with `"name"` as the type discriminator field.

## Using the AST DSL

The `ast.dsl` module provides a concise builder syntax for constructing AST nodes without position boilerplate:

```scala
import io.eleven19.ascribe.ast.dsl.{*, given}

val doc = document(
  heading(1, text("My Document")),
  paragraph("Hello ", bold("world"), "!"),
  unorderedList(
    listItem("First item"),
    listItem("Second item")
  )
)
```

String values are implicitly converted to `Text` nodes.

## Using the ASG DSL

The `asg.dsl` module provides a similar builder for ASG nodes with implicit location threading:

```scala
import io.eleven19.ascribe.asg.dsl.{*, given}

val doc = document(
  paragraph(text("Hello"), span("strong", "constrained", text("world")))
)
```

Override the default location by providing your own `given Location`:

```scala
given Location = loc(1, 1, 3, 9)
val located = document(paragraph(text("Hello")))
```

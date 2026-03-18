<div align="center">
  <img src="docs/assets/logo.png" alt="Ascribe Logo" width="200" />
</div>

# ascribe

[![CI](https://github.com/Eleven19/ascribe/actions/workflows/ci.yml/badge.svg)](https://github.com/Eleven19/ascribe/actions/workflows/ci.yml)
[![Release](https://github.com/Eleven19/ascribe/actions/workflows/release.yml/badge.svg)](https://github.com/Eleven19/ascribe/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.eleven19.ascribe/ascribe_3)](https://central.sonatype.com/artifact/io.eleven19.ascribe/ascribe_3)

**[Documentation](https://eleven19.github.io/ascribe/)** | **[API Reference](https://eleven19.github.io/ascribe/io/eleven19/ascribe.html)** | **[Getting Started](https://eleven19.github.io/ascribe/docs/getting-started.html)**

Ascribe is an AsciiDoc library and toolchain for Scala 3. We provide parsers, ASTs, an Abstract Semantic Graph (ASG), and traversal tools for processing AsciiDoc documents.

As we are implementing things we should consult the [Asciidoc Language Specification](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-lang) for the authoritative spec of ASCIIDOC.
The Asciidoc TCK (Technology Compatibility Kit) can also be found at [https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck](https://gitlab.eclipse.org/eclipse/asciidoc-lang/asciidoc-tck).

## Installation

### Mill

```scala
ivy"io.eleven19.ascribe::ascribe:0.1.0"
```

### sbt

```scala
libraryDependencies += "io.eleven19.ascribe" %% "ascribe" % "0.1.0"
```

### Maven

```xml
<dependency>
  <groupId>io.eleven19.ascribe</groupId>
  <artifactId>ascribe_3</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Native releases

Native executables are published through GitHub Releases as platform-specific archives named for `mise`'s GitHub backend. Stable releases use tags like `v1.2.3`; prereleases use semantic prerelease tags like `v1.2.3-rc.1`. Maintainer details live in `docs/contributing/releasing.md`.

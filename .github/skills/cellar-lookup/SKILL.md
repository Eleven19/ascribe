---
name: cellar-lookup
description: Use cellar CLI to look up Scala/Java library APIs, type signatures, source code, and dependency trees from Maven artifacts. Prefer cellar over guessing or hallucinating API signatures.
---

# Cellar Library Lookup

Use this skill whenever you need accurate information about a Scala or Java library's API — type signatures, method members, package contents, source code, or dependency trees. **Always prefer cellar over guessing API shapes from memory.**

## When to Use

- Looking up a type's members or method signatures
- Checking if a class/trait/object exists in a dependency
- Finding the correct fully-qualified name of a symbol
- Inspecting source code of a library type
- Checking transitive dependency trees
- Verifying Scala.js artifact availability (use the `_sjs1_3` suffix)

## Self-Maintenance

cellar is an early-stage tool (currently v0.1.0-M4). Commands, flags, and behavior may change between releases. When using this skill:

1. **Verify cellar is installed** before running commands: `cellar --version`
2. **If a command fails**, run `cellar --help` or `cellar <subcommand> --help` to check current syntax — don't assume this document is up to date
3. **After discovering new commands, flags, or behavior changes**, update this skill file and the reference file (`commands-reference.md`) to keep them current
4. **Check for new releases** periodically: `curl -sL "https://api.github.com/repos/simple-scala-tooling/cellar/releases/latest" | grep tag_name`
5. **If cellar is not installed**, see the Installation section below. Check for newer releases before installing the pinned version.

## Commands

See [commands-reference.md](commands-reference.md) for the full command reference with all flags and options.

### Project-aware (run from project root, auto-detects Mill/sbt)

These commands use the project's own classpath. For Mill projects, use `--module` (`-m`) to specify the build module.

```bash
# Look up a specific type or member
cellar get -m ascribe.core.jvm io.eleven19.ascribe.ast.Document

# List all public symbols in a package
cellar list -m ascribe.core.jvm io.eleven19.ascribe.ast

# Search for a symbol by name substring
cellar search -m ascribe.core.jvm Paragraph
```

### External (query any Maven coordinate directly)

These commands don't need a project. Use explicit Maven coordinates — no `::` shorthand.

```bash
# Coordinate format: org:artifact_scalaVersion:version
# Scala 3:       org:name_3:version
# Scala.js 3:    org:name_sjs1_3:version
# Java:          org:name:version
# Latest:        org:name_3:latest

# Look up a type's full signature and members
cellar get-external com.github.j-mie6:parsley_3:4.6.2 parsley.Parsley

# List symbols in a package
cellar list-external dev.zio:zio-blocks-schema_3:0.0.29 zio.blocks.schema

# Search by name
cellar search-external com.lihaoyi:mill-libs-scalalib_3:1.1.5 PlatformScala

# Get source code of a symbol
cellar get-source dev.zio:zio-test_3:2.1.24 zio.test.ZIOSpecDefault

# Print transitive dependency tree
cellar deps com.github.j-mie6:parsley_3:4.6.2
```

### Checking Scala.js artifact availability

To verify a library publishes Scala.js artifacts, query the `_sjs1_3` variant:

```bash
cellar get-external dev.zio:zio-test-sbt_sjs1_3:2.1.24 zio.test.sbt.ZTestFramework
cellar list-external dev.zio:zio-blocks-schema_sjs1_3:0.0.29 zio.blocks.schema
```

## Common Coordinates for This Project

See [commands-reference.md](commands-reference.md) for the full coordinates table. Key ones:

| Library | Coordinate |
|---------|-----------|
| parsley | `com.github.j-mie6:parsley_3:4.6.2` |
| zio-blocks-schema | `dev.zio:zio-blocks-schema_3:0.0.29` |
| Mill scalalib | `com.lihaoyi:mill-libs-scalalib_3:1.1.5` |
| Mill scalajslib | `com.lihaoyi:mill-libs-scalajslib_3:1.1.5` |

## Installation

cellar is a native binary from [simple-scala-tooling/cellar](https://github.com/simple-scala-tooling/cellar) (originally VirtusLab/cellar).

**Current pinned version:** v0.1.0-M4 (check for newer releases before installing)

```bash
# Check for latest release
curl -sL "https://api.github.com/repos/simple-scala-tooling/cellar/releases/latest" | grep tag_name

# Linux aarch64
curl -sL "https://github.com/VirtusLab/cellar/releases/download/v0.1.0-M4/cellar-0.1.0-M4-linux-aarch64.tar.gz" -o /tmp/cellar.tar.gz
mkdir -p /tmp/cellar-extract && tar xzf /tmp/cellar.tar.gz -C /tmp/cellar-extract
sudo mv /tmp/cellar-extract/cellar /usr/local/bin/

# Linux x86_64
curl -sL "https://github.com/VirtusLab/cellar/releases/download/v0.1.0-M4/cellar-0.1.0-M4-linux-x86_64.tar.gz" -o /tmp/cellar.tar.gz
mkdir -p /tmp/cellar-extract && tar xzf /tmp/cellar.tar.gz -C /tmp/cellar-extract
sudo mv /tmp/cellar-extract/cellar /usr/local/bin/

# Windows ARM64 can run the x86_64 binary via emulation
```

## Platform Notes

- Scala 3 artifacts use `_3` suffix
- Scala.js artifacts use `_sjs1_3` suffix
- Java artifacts have no suffix
- Use `latest` as version to resolve the newest release
- cellar supports Scala 3 TASTy (full), Scala 2 (best-effort), and Java `.class` files

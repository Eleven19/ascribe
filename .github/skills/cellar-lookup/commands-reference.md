# Cellar Commands Reference

> **Last verified:** v0.1.0-M4 (2026-04-04)
> **Source:** https://github.com/simple-scala-tooling/cellar
>
> If commands fail or behave differently, run `cellar --help` or `cellar <subcommand> --help`
> and update this file with the current syntax.

## Project-Aware Commands

These commands use the current project's build tool (Mill, sbt, or scala-cli) to resolve the classpath.

### `cellar get`

Fetch symbol info (type signature, members, docs) from the current project.

```
cellar get [--module <name>] [--java-home <path>] [--no-cache] <fully-qualified-symbol>
```

| Flag | Description |
|------|-------------|
| `--module <name>`, `-m` | Build module name (required for Mill/sbt) |
| `--java-home <path>` | Use a specific JDK for JRE classpath |
| `--no-cache` | Skip classpath cache (re-extract from build tool) |

```bash
cellar get -m ascribe.core.jvm io.eleven19.ascribe.ast.Document
cellar get -m ascribe.asg.jvm io.eleven19.ascribe.asg.Node
```

### `cellar list`

List all public symbols in a package or class from the current project.

```
cellar list [--module <name>] [--limit <N>] [--java-home <path>] [--no-cache] <fully-qualified-symbol>
```

| Flag | Description |
|------|-------------|
| `--module <name>`, `-m` | Build module name (required for Mill/sbt) |
| `--limit <N>`, `-l` | Maximum number of results (default: 50) |

```bash
cellar list -m ascribe.core.jvm io.eleven19.ascribe.ast
cellar list -m ascribe.core.jvm io.eleven19.ascribe.parser.BlockParser
```

### `cellar search`

Substring search for symbol names in the current project.

```
cellar search [--module <name>] [--limit <N>] [--java-home <path>] [--no-cache] <query>
```

```bash
cellar search -m ascribe.core.jvm Paragraph
cellar search -m ascribe.asg.jvm BlockMetadata
```

## External Commands

These commands query any Maven coordinate directly, without needing a project.

### Coordinate Format

```
org:artifact_scalaVersion:version

# Examples:
com.github.j-mie6:parsley_3:4.6.2          # Scala 3
dev.zio:zio-test_sjs1_3:2.1.24              # Scala.js 3
org.apache.commons:commons-lang3:3.14.0     # Java
com.github.j-mie6:parsley_3:latest          # Latest release
```

### `cellar get-external`

Fetch symbol info from a Maven coordinate.

```
cellar get-external [--java-home <path>] [--repository <url>]... <coordinate> <fully-qualified-symbol>
```

| Flag | Description |
|------|-------------|
| `--java-home <path>` | Use a specific JDK for JRE classpath |
| `--repository <url>`, `-r` | Extra Maven repository URL (repeatable) |

```bash
cellar get-external com.github.j-mie6:parsley_3:4.6.2 parsley.Parsley
cellar get-external com.lihaoyi:mill-libs-scalalib_3:1.1.5 mill.scalalib.PlatformScalaModule
cellar get-external dev.zio:zio-test_3:2.1.24 zio.test.ZIOSpecDefault
```

### `cellar list-external`

List symbols in a package from a Maven coordinate.

```
cellar list-external [--limit <N>] [--java-home <path>] [--repository <url>]... <coordinate> <fully-qualified-symbol>
```

```bash
cellar list-external dev.zio:zio-blocks-schema_3:0.0.29 zio.blocks.schema
cellar list-external com.lihaoyi:mill-libs-scalajslib_3:1.1.5 mill.scalajslib
```

### `cellar search-external`

Substring search for symbol names in a Maven coordinate.

```
cellar search-external [--limit <N>] [--java-home <path>] [--repository <url>]... <coordinate> <query>
```

```bash
cellar search-external com.lihaoyi:mill-libs-scalalib_3:1.1.5 PlatformScala
cellar search-external dev.zio:zio-test_3:2.1.24 TestFramework
```

### `cellar get-source`

Fetch the source code of a named symbol (requires `-sources.jar` on Maven Central).

```
cellar get-source [--java-home <path>] [--repository <url>]... <coordinate> <fully-qualified-symbol>
```

```bash
cellar get-source dev.zio:zio-test_3:2.1.24 zio.test.ZIOSpecDefault
cellar get-source com.github.j-mie6:parsley_3:4.6.2 parsley.Parsley
```

### `cellar deps`

Print the transitive dependency tree of a Maven coordinate.

```
cellar deps [--repository <url>]... <coordinate>
```

```bash
cellar deps com.github.j-mie6:parsley_3:4.6.2
cellar deps dev.zio:zio-blocks-schema_3:0.0.29
cellar deps dev.zio:zio-test_sjs1_3:2.1.24
```

## Common Coordinates for This Project

| Library | JVM Coordinate | JS Coordinate |
|---------|---------------|---------------|
| parsley | `com.github.j-mie6:parsley_3:4.6.2` | `com.github.j-mie6:parsley_sjs1_3:4.6.2` |
| zio-blocks-schema | `dev.zio:zio-blocks-schema_3:0.0.29` | `dev.zio:zio-blocks-schema_sjs1_3:0.0.29` |
| zio-test | `dev.zio:zio-test_3:2.1.24` | `dev.zio:zio-test_sjs1_3:2.1.24` |
| zio-test-sbt | `dev.zio:zio-test-sbt_3:2.1.24` | `dev.zio:zio-test-sbt_sjs1_3:2.1.24` |
| zio-json | `dev.zio:zio-json_3:0.7.3` | `dev.zio:zio-json_sjs1_3:0.7.3` |
| scalatags | `com.lihaoyi:scalatags_3:0.13.1` | `com.lihaoyi:scalatags_sjs1_3:0.13.1` |
| munit | `org.scalameta:munit_3:1.0.4` | `org.scalameta:munit_sjs1_3:1.0.4` |
| os-lib | `com.lihaoyi:os-lib_3:0.11.8` | (JVM-only) |
| ox | `com.softwaremill.ox:core_3:0.3.0` | (JVM-only) |
| kyo-core | `io.getkyo:kyo-core_3:1.0-RC1` | (JVM-only) |
| Mill scalalib | `com.lihaoyi:mill-libs-scalalib_3:1.1.5` | n/a |
| Mill scalajslib | `com.lihaoyi:mill-libs-scalajslib_3:1.1.5` | n/a |
| Mill javalib | `com.lihaoyi:mill-libs-javalib_3:1.1.5` | n/a |

## Troubleshooting

- **"Symbol not found"**: Check the fully-qualified name. Use `search-external` to find the correct name.
- **"Cannot resolve coordinate"**: Verify the artifact exists on Maven Central. Check `_3` vs `_2.13` suffix.
- **Nested types**: cellar may not resolve `Outer.Inner` syntax. Try `search-external` with the inner name.
- **Build tool detection fails**: Use `--no-cache` to force re-detection, or fall back to `*-external` commands.

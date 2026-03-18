---
title: Development Setup
---

# Development Setup

## Prerequisites

- **JVM 21** (Temurin recommended)
- **Mill 1.1.3** (included as `./mill` wrapper in the repository)

## Clone and Build

```bash
git clone https://github.com/Eleven19/ascribe.git
cd ascribe
./mill __.compile
```

The first build will download Mill and all dependencies automatically.

## Project Modules

| Module | Mill path | Description |
|--------|-----------|-------------|
| Core parser + AST | `ascribe` | Parsley-based parser, AST types, lexer |
| ASG model | `ascribe.asg` | ASG node types, JSON codecs, visitor |
| Bridge | `ascribe.bridge` | AST-to-ASG converter |
| TCK runner | `ascribe.tck-runner` | Cucumber-based TCK test harness |
| Integration tests | `ascribe.itest` | Cucumber integration tests |

## Running Tests

Run all tests:

```bash
./mill __.test
```

Run tests for individual modules:

```bash
./mill ascribe.test              # Parser and AST tests
./mill ascribe.asg.test          # ASG codec and visitor tests
./mill ascribe.bridge.test       # Bridge converter tests
```

## Running TCK Tests

The TCK runner uses test data from the AsciiDoc TCK submodule. First refresh the test data, then run:

```bash
./mill ascribe.tck-runner.tckRefresh
./mill ascribe.tck-runner.test
```

## Code Formatting

Check formatting:

```bash
./mill __.checkFormat
```

Apply formatting:

```bash
./mill ascribe.reformat
```

## Generating Documentation

Generate Scaladoc:

```bash
./mill ascribe.docs.docJar
```

The output will be in `out/ascribe/docs/docJar.dest/javadoc`.

## Useful Mill Commands

```bash
./mill resolve __            # List all modules
./mill __.compile            # Compile everything
./mill __.test               # Run all tests
./mill show ascribe.ivyDeps  # Show dependencies for a module
```

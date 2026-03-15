# Changing the logging implementation in Kyo: Scribe integration

This note explains how Kyo’s logging works and how we switched this app from the default console logger to [Scribe](https://github.com/outr/scribe), so you can do the same or plug in another backend.

## How Kyo’s Log works

Kyo’s logging is **effectful** and **context-based**:

- You call `Log.trace`, `Log.debug`, `Log.info`, `Log.warn`, or `Log.error` (with optional `Throwable`).
- Messages use Kyo’s `Text` type; call sites get **source position** (file:line) from the effect `Frame`.
- The actual output is **not** global. It comes from a **logger instance** stored in Kyo’s `Local` context. By default that’s a simple console logger.

So “which logger” is chosen per scope, typically at the app entrypoint, via `Log.let(log)(...)`.

### Default: console logger

Out of the box, `Log.live` is a `Log(Unsafe.ConsoleLogger("kyo.logs", Level.debug))`: it prints lines like `LEVEL name -- [position] message` to stdout. Fine for demos; less so if you want structured logging, log files, or integration with existing tooling.

### Swapping the backend

To use another library (Scribe, SLF4J, java.util.logging, etc.) you:

1. Implement **`Log.Unsafe`**: one class that handles `level` plus the five levels × (message only / message + throwable).
2. Wrap it in **`Log(yourUnsafe)`** and run your program (or the part that should use it) inside **`Log.let(yourLog)(...)`**.

No need to change call sites: they keep using `Log.info(...)`, `Log.error(...)`, etc.

---

## What we did in this repo: Scribe

We wanted Scribe as the real backend for all `Log.*` calls in this app (formatting, performance, programmatic configuration). Kyo doesn’t ship a Scribe module, so we added a small adapter in the app.

### 1. Dependencies

Scribe is already a dependency in this project (see `build.mill.yaml`):

```yaml
mvnDeps:
  - com.outr::scribe::3.18.0
  # ... kyo-core, etc.
```

No extra module is required.

### 2. Implementing `Log.Unsafe` with Scribe

We introduced **`ScribeLog`** in `src/io/github/eleven19/mill/interceptor/ScribeLog.scala`.

Conceptually:

- **`Log.Unsafe`** is an abstract class with:
  - `def level: Log.Level`
  - For each of trace/debug/info/warn/error: one method with `(msg: => Text)` and one with `(msg: => Text, t: => Throwable)`.
  - All methods take `(using frame: Frame, allow: AllowUnsafe)`.

- Our **`Unsafe.Scribe`** class holds a **`scribe.Logger`** and a **`Log.Level`**, and implements each method by:
  - Building a single string: **`[${frame.position.show}] $msg`** (so we keep Kyo’s source position in the message).
  - Calling the corresponding Scribe method, e.g. `logger.trace(...)`, `logger.error(..., t)`.

Scribe’s API matches what we need: `trace(msg)`, `trace(msg, t)`, and similarly for debug, info, warn, error. So the adapter is a thin wrapper.

Important details:

- **`level`**: we expose the `Log.Level` we were given (e.g. `Log.Level.debug`). Kyo uses this to decide whether to call into our implementation at all (e.g. if level is `warn`, `trace`/`debug` are no-ops). So the “minimum level” is controlled by this value.
- **`AllowUnsafe`**: implementing `Log.Unsafe` is a low-level integration; the Kyo API requires this capability. We don’t need to do anything special beyond `using allow: AllowUnsafe`.
- **Message string**: we use string interpolation `s"[${frame.position.show}] $msg"` so that `Text` is rendered and the call site position is included, similar to Kyo’s own `ConsoleLogger`.

We then expose a safe API:

- **`ScribeLog(name, level)`** – creates a Scribe `Logger(name)` and wraps it in `Log` with that level.
- **`ScribeLog(logger, level)`** – wraps an existing Scribe `Logger` and level (no default on `level` to avoid overload issues).

So the “logging implementation” in this codebase is **`ScribeLog`**; the “backend” is Scribe.

### 3. Wiring it at the entrypoint

We want **every** `Log.*` call in the app to go to Scribe. The entrypoint is **`Main`** (a `KyoApp`), so we set the logger once and run the whole app under it.

In `Main.scala`:

```scala
object Main extends KyoApp:
    private val scribeLog = ScribeLog("io.github.eleven19.mill.interceptor")

    run {
        Log.let(scribeLog) {
            direct {
                val interceptedBuildToolFromEnv =
                    System.env[String]("INTERCEPTED_BUILD_TOOL").now

                process(args, interceptedBuildToolFromEnv).now
            }
        }
    }
```

- **`scribeLog`** is our `Log` instance (Scribe-backed).
- **`Log.let(scribeLog) { ... }`** runs the block with that logger as the current context. All code that runs inside that block (including `process(...)` and any `Log.error` / `Log.info` calls) uses Scribe.

We did **not** change any call sites: they still use `Log.error("...")`, `Log.info(...)`, etc. Only the implementation behind those calls changed.

### 4. Verifying

Running the app and triggering a log line (e.g. set `INTERCEPTED_BUILD_TOOL=unknown` so we hit the “Unsupported build tool” path) shows Scribe’s format in the output (timestamp, thread, level, source, message). That confirms Kyo’s `Log` is backed by Scribe for this app.

---

## Summary

| Goal | How |
|------|-----|
| Use a different logging backend in Kyo | Implement `Log.Unsafe`, wrap in `Log(...)`, run your code inside `Log.let(yourLog)(...)`. |
| In this app | We implemented `ScribeLog` (Scribe-backed `Log.Unsafe`) and call `Log.let(scribeLog) { ... }` in `Main.run`. |
| Call sites | Unchanged: keep using `Log.trace/debug/info/warn/error`. |

If you later want a different level or logger name, you can change the arguments to `ScribeLog("...")` or use `ScribeLog(logger, Log.Level.info)` (or another level). For other backends (e.g. SLF4J), Kyo’s own modules like `kyo-logging-slf4j` follow the same pattern: they implement `Log.Unsafe` and you use `Log.let(slf4jLog)(...)` at the entrypoint.

package io.github.eleven19.mill.interceptor

import kyo.*
import kyo.test.KyoSpecDefault
import zio.test.*

/** Unit tests for [[ScribeLog]] using kyo-zio-test (KyoSpecDefault). */
object ScribeLogSpec extends KyoSpecDefault:

  def spec: Spec[Any, Any] = suite("ScribeLog")(
    suite("apply")(
      test("by name uses default debug level") {
        val log = ScribeLog("test.logger")
        Sync.defer(assertTrue(log.level == Log.Level.debug))
      },
      test("by name and level preserves level") {
        val log = ScribeLog("test.logger", Log.Level.warn)
        Sync.defer(assertTrue(log.level == Log.Level.warn))
      },
      test("by logger and level preserves level") {
        val scribeLogger = scribe.Logger("custom")
        val log          = ScribeLog(scribeLogger, Log.Level.trace)
        Sync.defer(assertTrue(log.level == Log.Level.trace))
      }
    ),
    suite("Log.let and delegation")(
      test("Log.let(ScribeLog)(Log.info) runs without error") {
        val scribeLog = ScribeLog("scribe.log.spec.info")
        Log.let(scribeLog)(Log.info("info message")).map(_ => assertCompletes)
      },
      test("Log.let(ScribeLog)(Log.debug) runs without error") {
        val scribeLog = ScribeLog("scribe.log.spec.debug")
        Log.let(scribeLog)(Log.debug("debug message")).map(_ => assertCompletes)
      },
      test("Log.let(ScribeLog)(Log.warn) runs without error") {
        val scribeLog = ScribeLog("scribe.log.spec.warn")
        Log.let(scribeLog)(Log.warn("warn message")).map(_ => assertCompletes)
      },
      test("Log.let(ScribeLog)(Log.error) runs without error") {
        val scribeLog = ScribeLog("scribe.log.spec.error")
        Log.let(scribeLog)(Log.error("error message")).map(_ => assertCompletes)
      },
      test("Log.let(ScribeLog)(Log.error with throwable) runs without error") {
        val scribeLog = ScribeLog("scribe.log.spec.error.ex")
        val ex        = new RuntimeException("test exception")
        Log.let(scribeLog)(Log.error("error with ex", ex)).map(_ => assertCompletes)
      }
    )
  )

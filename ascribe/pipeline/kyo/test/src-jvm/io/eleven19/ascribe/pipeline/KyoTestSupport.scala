package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.{<, Abort, Async, Duration, KyoApp, Result, Sync, Timeout}
import kyo.AllowUnsafe.embrace.danger

/** Test-only helpers to run Kyo stacks with `Abort[PipelineError]`. */
object KyoTestSupport:

    def runSyncAbort[A](v: A < (Sync & Abort[PipelineError])): Result[PipelineError, A] =
        Sync.Unsafe.evalOrThrow(Abort.run[PipelineError](v))

    def runSyncAbortResult[A](v: Result[PipelineError, A] < Sync): Result[PipelineError, A] =
        Sync.Unsafe.evalOrThrow(v)

    def runAsyncAbort[A](v: A < (Async & Abort[PipelineError])): Result[PipelineError, A] =
        Sync.Unsafe.evalOrThrow(
            Abort.recover[Timeout](timeout => throw timeout)(
                KyoApp.runAndBlock(Duration.Infinity)(Abort.run[PipelineError](v))
            )
        )

    def runSync[A](v: A < Sync): A =
        Sync.Unsafe.evalOrThrow(v)

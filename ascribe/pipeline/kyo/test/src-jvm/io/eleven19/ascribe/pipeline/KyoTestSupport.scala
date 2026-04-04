package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.{<, Abort, Result, Sync}
import kyo.AllowUnsafe.embrace.danger

/** Test-only helpers to run `Sync & Abort[PipelineError]` stacks (see kyo `AbortCombinatorsTest` for `Sync` + `Abort` nesting). */
object KyoTestSupport:

    def runSyncAbort[A](v: A < (Sync & Abort[PipelineError])): Result[PipelineError, A] =
        Sync.Unsafe.evalOrThrow(Abort.run[PipelineError](v))

    def runSyncAbortResult[A](v: Result[PipelineError, A] < Sync): Result[PipelineError, A] =
        Sync.Unsafe.evalOrThrow(v)

    def runSync[A](v: A < Sync): A =
        Sync.Unsafe.evalOrThrow(v)

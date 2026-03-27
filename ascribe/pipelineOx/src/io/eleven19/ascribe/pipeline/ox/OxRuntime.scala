package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.pipeline.core.{PipelineError, PipelineOp, PipelineOpVisitor}

/** Placeholder Ox-backed visitor; extend with file I/O and include resolution. */
object OxRuntime:

    def interpret[A](op: PipelineOp)(using v: PipelineOpVisitor[A]): A =
        PipelineOp.fold(op)(v)

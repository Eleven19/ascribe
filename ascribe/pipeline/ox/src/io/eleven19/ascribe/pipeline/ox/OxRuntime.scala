package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.pipeline.core.{PipelineOp, PipelineOpVisitor}

/** Interprets the minimal [[PipelineOp]] algebra (for experiments and future codegen).
  *
  * For full document processing, prefer [[Pipeline]], [[Source]], and [[Sink]] in this package.
  */
object OxRuntime:

    def interpret[A](op: PipelineOp)(using v: PipelineOpVisitor[A]): A =
        PipelineOp.fold(op)(v)

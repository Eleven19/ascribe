package io.eleven19.ascribe.pipeline.core

import io.eleven19.ascribe.ast.DocumentPath

/** Minimal pipeline algebra; extend carefully as new stages are added. */
enum PipelineOp:
    case ReadString(path: DocumentPath, content: String)
    case RenderAsciiDoc(path: DocumentPath)

/** Fold / visitor over [[PipelineOp]]. */
trait PipelineOpVisitor[A]:
    def readString(path: DocumentPath, content: String): A
    def renderAsciiDoc(path: DocumentPath): A

object PipelineOp:

    def fold[A](op: PipelineOp)(v: PipelineOpVisitor[A]): A =
        op match
            case ReadString(path, content) => v.readString(path, content)
            case RenderAsciiDoc(path)      => v.renderAsciiDoc(path)

package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import io.eleven19.ascribe.pipeline.core.{PipelineOp, PipelineOpVisitor}
import zio.test.*

object OxRuntimeSpec extends ZIOSpecDefault:

    def spec = suite("OxRuntime")(
        test("interpret ReadString delegates to visitor") {
            given PipelineOpVisitor[String] with
                def readString(path: DocumentPath, content: String): String =
                    s"READ ${path.render} <<$content>>"
                def renderAsciiDoc(path: DocumentPath): String =
                    s"RENDER ${path.render}"

            val op  = PipelineOp.ReadString(DocumentPath.fromString("ch.adoc"), "body")
            val out = OxRuntime.interpret(op)
            assertTrue(out == "READ ch.adoc <<body>>")
        },
        test("interpret RenderAsciiDoc delegates to visitor") {
            given PipelineOpVisitor[String] with
                def readString(path: DocumentPath, content: String): String = "unexpected"
                def renderAsciiDoc(path: DocumentPath): String         = s"R ${path.render}"

            val op  = PipelineOp.RenderAsciiDoc(DocumentPath.fromString("out.adoc"))
            val out = OxRuntime.interpret(op)
            assertTrue(out == "R out.adoc")
        }
    )

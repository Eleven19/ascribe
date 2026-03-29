package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.dsl.{*, given}
import zio.test.*
import scala.language.implicitConversions

object AsgJsonRendererSpec extends ZIOSpecDefault:

    def spec = suite("AsgJsonRenderer (Ox)")(
        test("encodes a minimal document with document and paragraph kinds") {
            val json = AsgJsonRenderer.render(document(paragraph("hi")))
            assertTrue(
                json.contains("\"name\""),
                json.contains("document"),
                json.contains("paragraph"),
                json.contains("hi")
            )
        },
        test("Pipeline renderWith AsgJsonRenderer matches direct render") {
            val doc = document(paragraph("x"))
            val a   = AsgJsonRenderer.render(doc)
            Pipeline.fromDocument(doc).renderWith(AsgJsonRenderer).runToString match
                case Right(s) => assertTrue(s == a)
                case Left(_)  => assertTrue(false)
        }
    )

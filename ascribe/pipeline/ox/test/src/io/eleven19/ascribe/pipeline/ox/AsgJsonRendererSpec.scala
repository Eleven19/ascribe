package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.dsl.{*, given}
import kyo.test.*
import scala.language.implicitConversions

class AsgJsonRendererSpec extends Test[Any]:

    "AsgJsonRenderer (Ox)" - {
        "encodes a minimal document with document and paragraph kinds" in {
            val json = AsgJsonRenderer.render(document(paragraph("hi")))
            assert(json.contains("\"name\""))
            assert(json.contains("document"))
            assert(json.contains("paragraph"))
            assert(json.contains("hi"))
        }
        "Pipeline renderWith AsgJsonRenderer matches direct render" in {
            val doc = document(paragraph("x"))
            val a   = AsgJsonRenderer.render(doc)
            Pipeline.fromDocument(doc).renderWith(AsgJsonRenderer).runToString match
                case Right(s) => assert(s == a)
                case Left(_)  => assert(false)
        }
    }

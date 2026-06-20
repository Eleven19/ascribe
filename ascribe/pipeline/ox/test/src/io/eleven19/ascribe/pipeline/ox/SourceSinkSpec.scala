package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.PipelineError
import kyo.test.*
import scala.language.implicitConversions

class SourceSinkSpec extends Test[Any]:

    "Source & Sink (Ox) units" - {
        "Source.fromStrings fails on first bad document" in {
            val r = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "[bad"),
                    (DocumentPath("b.adoc"), "OK.\n")
                )
                .read
            assert(r.isLeft)
        }
        "Source.fromStrings fails on second bad document" in {
            val r = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "OK.\n"),
                    (DocumentPath("b.adoc"), "[bad")
                )
                .read
            assert(r.isLeft)
        }
        "Source.fromDocument uses default path document.adoc" in {
            Source.fromDocument(document(paragraph("x"))).read match
                case Right(t) => assert(t.allDocuments.head._1 == DocumentPath("document.adoc"))
                case Left(_)  => assert(false)
        }
        "Source.fromDocument with explicit path" in {
            val p = DocumentPath("custom/path.adoc")
            Source.fromDocument(document(paragraph("x")), p).read match
                case Right(t) => assert(t.allDocuments.head._1 == p)
                case Left(_)  => assert(false)
        }
        "MapSink second write replaces entire map" in {
            val s = Sink.toMap()
            assert(s.write(Map(DocumentPath("a.adoc") -> "1\n")).isRight)
            assert(s.write(Map(DocumentPath("b.adoc") -> "2\n")).isRight)
            assert(s.output.size == 1)
            assert(s.output.contains(DocumentPath("b.adoc")))
        }
        "StringSink second write replaces result" in {
            val s = Sink.toStringResult()
            assert(s.write(Map(DocumentPath("a.adoc") -> "first")).isRight)
            assert(s.write(Map(DocumentPath("b.adoc") -> "second")).isRight)
            assert(s.output == "second")
        }
        "StringSink empty map yields empty string" in {
            val s = Sink.toStringResult()
            assert(s.write(Map.empty).isRight)
            assert(s.output == "")
        }
    }

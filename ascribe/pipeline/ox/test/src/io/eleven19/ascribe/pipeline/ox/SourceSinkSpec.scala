package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.PipelineError
import zio.test.*
import scala.language.implicitConversions

object SourceSinkSpec extends ZIOSpecDefault:

    def spec = suite("Source & Sink (Ox) units")(
        test("Source.fromStrings fails on first bad document") {
            val r = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "[bad"),
                    (DocumentPath("b.adoc"), "OK.\n")
                )
                .read
            assertTrue(r.isLeft)
        },
        test("Source.fromStrings fails on second bad document") {
            val r = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "OK.\n"),
                    (DocumentPath("b.adoc"), "[bad")
                )
                .read
            assertTrue(r.isLeft)
        },
        test("Source.fromDocument uses default path document.adoc") {
            Source.fromDocument(document(paragraph("x"))).read match
                case Right(t) => assertTrue(t.allDocuments.head._1 == DocumentPath("document.adoc"))
                case Left(_)  => assertTrue(false)
        },
        test("Source.fromDocument with explicit path") {
            val p = DocumentPath("custom/path.adoc")
            Source.fromDocument(document(paragraph("x")), p).read match
                case Right(t) => assertTrue(t.allDocuments.head._1 == p)
                case Left(_)  => assertTrue(false)
        },
        test("MapSink second write replaces entire map") {
            val s = Sink.toMap()
            assertTrue(
                s.write(Map(DocumentPath("a.adoc") -> "1\n")).isRight,
                s.write(Map(DocumentPath("b.adoc") -> "2\n")).isRight,
                s.output.size == 1,
                s.output.contains(DocumentPath("b.adoc"))
            )
        },
        test("StringSink second write replaces result") {
            val s = Sink.toStringResult()
            assertTrue(
                s.write(Map(DocumentPath("a.adoc") -> "first")).isRight,
                s.write(Map(DocumentPath("b.adoc") -> "second")).isRight,
                s.output == "second"
            )
        },
        test("StringSink empty map yields empty string") {
            val s = Sink.toStringResult()
            assertTrue(s.write(Map.empty).isRight, s.output == "")
        }
    )

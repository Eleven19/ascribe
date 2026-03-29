package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import zio.test.*
import scala.language.implicitConversions

object PipelineSpec extends ZIOSpecDefault:

    def spec = suite("Pipeline (Ox)")(
        test("roundtrip: parse and render produces original") {
            val input  = "Hello world.\n"
            val result = Pipeline.fromString(input).runToString
            assertTrue(result == Right("Hello world.\n"))
        },
        test("pipeline with rewrite rule modifies output") {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val result = Pipeline.fromString("hello.\n").rewrite(rule).runToString
            assertTrue(result == Right("HELLO.\n"))
        },
        test("pipeline with remove rule eliminates blocks") {
            val doc = Document(
                None,
                scala.List(paragraph("keep"), Comment("////", "drop")(Span.unknown))
            )(Span.unknown)
            val rule = RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }
            Pipeline.fromDocument(doc).rewrite(rule).run match
                case Right(tree) => assertTrue(tree.allDocuments.head._2.blocks.size == 1)
                case Left(_)     => assertTrue(false)
        },
        test("pipeline with composed rules applies in order") {
            val rule1 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("world.")(Span.unknown))
            }
            val rule2 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("ignored.")(Span.unknown))
            }
            val result = Pipeline.fromString("hello.\n").rewrite(rule1).rewrite(rule2).runToString
            assertTrue(result == Right("world.\n"))
        },
        test("runToStrings returns map of all rendered documents") {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val result = Pipeline.fromTree(tree).runToStrings
            assertTrue(
                result == Right(
                    Map(
                        DocumentPath("a.adoc") -> "one\n",
                        DocumentPath("b.adoc") -> "two\n"
                    )
                )
            )
        },
        test("runTo writes to a MapSink") {
            val sink = Sink.toMap()
            val ok   = Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink)
            assertTrue(ok.isRight, sink.output.size == 1)
        },
        test("runTo writes to a StringSink") {
            val sink = Sink.toStringResult()
            val ok   = Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink)
            assertTrue(ok.isRight, sink.output == "hello\n")
        },
        test("renderWith switches renderer") {
            val result = Pipeline
                .fromDocument(document(paragraph("hello")))
                .renderWith(AsgJsonRenderer)
                .runToString
            assertTrue(result.exists(_.contains("\"name\":\"document\"")))
        },
        test("Source.fromStrings creates multi-document tree") {
            val result = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "Hello.\n"),
                    (DocumentPath("b.adoc"), "World.\n")
                )
                .read
            assertTrue(result.map(_.size) == Right(2))
        },
        test("Source.fromString fails on invalid input") {
            val result = Source.fromString("[invalid\n").read
            assertTrue(result.isLeft)
        },
        test("runToTargets renders through multiple renderers in a single run") {
            Pipeline
                .fromDocument(document(paragraph("hello")))
                .runToTargets(
                    "adoc" -> AsciiDocRenderer,
                    "json" -> AsgJsonRenderer
                ) match
                case Right(results) =>
                    assertTrue(
                        results.size == 2,
                        results("adoc").values.head == "hello\n",
                        results("json").values.head.contains("\"name\":\"document\"")
                    )
                case Left(_) => assertTrue(false)
        },
        test("renderAll returns a list of results per renderer") {
            Pipeline
                .fromDocument(document(paragraph("hello")))
                .renderAll(AsciiDocRenderer, AsgJsonRenderer) match
                case Right(results) =>
                    assertTrue(
                        results.size == 2,
                        results.head.values.head == "hello\n",
                        results(1).values.head.contains("\"name\":\"document\"")
                    )
                case Left(_) => assertTrue(false)
        },
        test("runToTargets works with multi-document tree") {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            Pipeline.fromTree(tree).runToTargets("adoc" -> AsciiDocRenderer) match
                case Right(results) =>
                    assertTrue(
                        results("adoc").size == 2,
                        results("adoc")(DocumentPath("a.adoc")) == "one\n",
                        results("adoc")(DocumentPath("b.adoc")) == "two\n"
                    )
                case Left(_) => assertTrue(false)
        }
    )

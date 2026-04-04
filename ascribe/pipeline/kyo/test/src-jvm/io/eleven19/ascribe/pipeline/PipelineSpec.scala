package io.eleven19.ascribe.pipeline

import zio.test.*
import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import scala.language.implicitConversions
import kyo.{<, Abort, Result}

object PipelineSpec extends ZIOSpecDefault:

    /** Evaluate a pure Kyo value (no pending effects). */
    private def runPure[A](v: A < Any): A = v.asInstanceOf[A]

    /** Run an Abort[PipelineError] effect and extract the result. */
    private def runAbort[A](v: A < Abort[PipelineError]): Result[PipelineError, A] =
        runPure(Abort.run[PipelineError](v))

    def spec = suite("Pipeline")(
        test("roundtrip: parse and render produces original") {
            val input  = "Hello world.\n"
            val result = runAbort(Pipeline.fromString(input).runToString)
            assertTrue(result == Result.Success("Hello world.\n"))
        },
        test("pipeline with rewrite rule modifies output") {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val result = runAbort(Pipeline.fromString("hello.\n").rewrite(rule).runToString)
            assertTrue(result == Result.Success("HELLO.\n"))
        },
        test("pipeline with remove rule eliminates blocks") {
            val doc = Document(
                None,
                scala.List(paragraph("keep"), Comment("////", "drop")(Span.unknown))
            )(Span.unknown)
            val rule = RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }
            val tree = runPure(Pipeline.fromDocument(doc).rewrite(rule).run)
            assertTrue(tree.allDocuments.head._2.blocks.size == 1)
        },
        test("pipeline with composed rules applies in order") {
            val rule1 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("world.")(Span.unknown))
            }
            val rule2 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("ignored.")(Span.unknown))
            }
            val result = runAbort(Pipeline.fromString("hello.\n").rewrite(rule1).rewrite(rule2).runToString)
            assertTrue(result == Result.Success("world.\n"))
        },
        test("runToStrings returns map of all rendered documents") {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val result = runPure(Pipeline.fromTree(tree).runToStrings)
            assertTrue(
                result.size == 2,
                result(DocumentPath("a.adoc")) == "one\n",
                result(DocumentPath("b.adoc")) == "two\n"
            )
        },
        test("runTo writes to a MapSink") {
            val sink = Sink.toMap()
            runPure(Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink))
            assertTrue(sink.output.size == 1)
        },
        test("runTo writes to a StringSink") {
            val sink = Sink.toStringResult()
            runPure(Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink))
            assertTrue(sink.output == "hello\n")
        },
        test("renderWith switches renderer") {
            val result = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .renderWith(AsgJsonRenderer.asInstanceOf[Renderer[Any]])
                    .runToString
            )
            assertTrue(result.contains("\"name\":\"document\""))
        },
        test("Source.fromStrings creates multi-document tree") {
            val result = runAbort(
                Source
                    .fromStrings(
                        (DocumentPath("a.adoc"), "Hello.\n"),
                        (DocumentPath("b.adoc"), "World.\n")
                    )
                    .read
            )
            assertTrue(result.map(_.size) == Result.Success(2))
        },
        test("Source.fromString fails on invalid input") {
            val result = runAbort(Source.fromString("[invalid\n").read)
            assertTrue(!result.isSuccess)
        },
        test("runToTargets renders through multiple renderers in a single run") {
            val results = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .runToTargets(
                        "adoc" -> AsciiDocRenderer,
                        "json" -> AsgJsonRenderer
                    )
            )
            assertTrue(
                results.size == 2,
                results("adoc").values.head == "hello\n",
                results("json").values.head.contains("\"name\":\"document\"")
            )
        },
        test("renderAll returns a list of results per renderer") {
            val results = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .renderAll(AsciiDocRenderer, AsgJsonRenderer)
            )
            assertTrue(
                results.size == 2,
                results.head.values.head == "hello\n",
                results(1).values.head.contains("\"name\":\"document\"")
            )
        },
        test("runToTargets works with multi-document tree") {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val results = runPure(
                Pipeline.fromTree(tree).runToTargets("adoc" -> AsciiDocRenderer)
            )
            assertTrue(
                results("adoc").size == 2,
                results("adoc")(DocumentPath("a.adoc")) == "one\n",
                results("adoc")(DocumentPath("b.adoc")) == "two\n"
            )
        }
    )

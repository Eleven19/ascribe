package io.eleven19.ascribe.pipeline

import kyo.test.*
import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import scala.language.implicitConversions
import kyo.{<, Abort, Result, Sync}

class PipelineSpec extends Test[Any]:

    /** Evaluate a pure Kyo value (no pending effects). */
    private def runPure[A](v: A < Any): A =
        KyoTestSupport.runSync(v.asInstanceOf[A < Sync])

    /** Run an Abort[PipelineError] effect and extract the result. */
    private def runAbort[A](v: A < Abort[PipelineError]): Result[PipelineError, A] =
        KyoTestSupport.runSyncAbort(v.asInstanceOf[A < (Sync & Abort[PipelineError])])

    "Pipeline" - {
        "roundtrip: parse and render produces original" in {
            val input  = "Hello world.\n"
            val result = runAbort(Pipeline.fromString(input).runToString)
            assert(result == Result.Success("Hello world.\n"))
        }
        "pipeline with rewrite rule modifies output" in {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val result = runAbort(Pipeline.fromString("hello.\n").rewrite(rule).runToString)
            assert(result == Result.Success("HELLO.\n"))
        }
        "pipeline with remove rule eliminates blocks" in {
            val doc = Document(
                None,
                scala.List(paragraph("keep"), Comment("////", "drop")(Span.unknown))
            )(Span.unknown)
            val rule = RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }
            val tree = runPure(Pipeline.fromDocument(doc).rewrite(rule).run)
            assert(tree.allDocuments.head._2.blocks.size == 1)
        }
        "pipeline with composed rules applies in order" in {
            val rule1 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("world.")(Span.unknown))
            }
            val rule2 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("ignored.")(Span.unknown))
            }
            val result = runAbort(Pipeline.fromString("hello.\n").rewrite(rule1).rewrite(rule2).runToString)
            assert(result == Result.Success("world.\n"))
        }
        "runToStrings returns map of all rendered documents" in {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val result = runPure(Pipeline.fromTree(tree).runToStrings)
            assert(result.size == 2)
            assert(result(DocumentPath("a.adoc")) == "one\n")
            assert(result(DocumentPath("b.adoc")) == "two\n")
        }
        "runTo writes to a MapSink" in {
            val sink = Sink.toMap()
            runPure(Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink))
            assert(sink.output.size == 1)
        }
        "runTo writes to a StringSink" in {
            val sink = Sink.toStringResult()
            runPure(Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink))
            assert(sink.output == "hello\n")
        }
        "renderWith switches renderer" in {
            val result = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .renderWith(AsgJsonRenderer.asInstanceOf[Renderer[Any]])
                    .runToString
            )
            assert(result.contains("\"name\":\"document\""))
        }
        "Source.fromStrings creates multi-document tree" in {
            val result = runAbort(
                Source
                    .fromStrings(
                        (DocumentPath("a.adoc"), "Hello.\n"),
                        (DocumentPath("b.adoc"), "World.\n")
                    )
                    .read
            )
            assert(result.map(_.size) == Result.Success(2))
        }
        "Source.fromString fails on invalid input" in {
            val result = runAbort(Source.fromString("[invalid\n").read)
            assert(!result.isSuccess)
        }
        "runToTargets renders through multiple renderers in a single run" in {
            val results = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .runToTargets(
                        "adoc" -> AsciiDocRenderer,
                        "json" -> AsgJsonRenderer
                    )
            )
            assert(results.size == 2)
            assert(results("adoc").values.head == "hello\n")
            assert(results("json").values.head.contains("\"name\":\"document\""))
        }
        "renderAll returns a list of results per renderer" in {
            val results = runPure(
                Pipeline
                    .fromDocument(document(paragraph("hello")))
                    .renderAll(AsciiDocRenderer, AsgJsonRenderer)
            )
            assert(results.size == 2)
            assert(results.head.values.head == "hello\n")
            assert(results(1).values.head.contains("\"name\":\"document\""))
        }
        "runToTargets works with multi-document tree" in {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val results = runPure(
                Pipeline.fromTree(tree).runToTargets("adoc" -> AsciiDocRenderer)
            )
            assert(results("adoc").size == 2)
            assert(results("adoc")(DocumentPath("a.adoc")) == "one\n")
            assert(results("adoc")(DocumentPath("b.adoc")) == "two\n")
        }
    }

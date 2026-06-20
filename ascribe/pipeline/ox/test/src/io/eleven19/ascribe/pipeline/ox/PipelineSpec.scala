package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import kyo.test.*
import scala.language.implicitConversions

class PipelineSpec extends Test[Any]:

    "Pipeline (Ox)" - {
        "roundtrip: parse and render produces original" in {
            val input  = "Hello world.\n"
            val result = Pipeline.fromString(input).runToString
            assert(result == Right("Hello world.\n"))
        }
        "pipeline with rewrite rule modifies output" in {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val result = Pipeline.fromString("hello.\n").rewrite(rule).runToString
            assert(result == Right("HELLO.\n"))
        }
        "pipeline with remove rule eliminates blocks" in {
            val doc = Document(
                None,
                scala.List(paragraph("keep"), Comment("////", "drop")(Span.unknown))
            )(Span.unknown)
            val rule = RewriteRule.forBlocks { case _: Comment => RewriteAction.Remove }
            Pipeline.fromDocument(doc).rewrite(rule).run match
                case Right(tree) => assert(tree.allDocuments.head._2.blocks.size == 1)
                case Left(_)     => assert(false)
        }
        "pipeline with composed rules applies in order" in {
            val rule1 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("world.")(Span.unknown))
            }
            val rule2 = RewriteRule.forInlines { case Text("hello.") =>
                RewriteAction.Replace(Text("ignored.")(Span.unknown))
            }
            val result = Pipeline.fromString("hello.\n").rewrite(rule1).rewrite(rule2).runToString
            assert(result == Right("world.\n"))
        }
        "runToStrings returns map of all rendered documents" in {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            val result = Pipeline.fromTree(tree).runToStrings
            assert(
                result == Right(
                    Map(
                        DocumentPath("a.adoc") -> "one\n",
                        DocumentPath("b.adoc") -> "two\n"
                    )
                )
            )
        }
        "runTo writes to a MapSink" in {
            val sink = Sink.toMap()
            val ok   = Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink)
            assert(ok.isRight)
            assert(sink.output.size == 1)
        }
        "runTo writes to a StringSink" in {
            val sink = Sink.toStringResult()
            val ok   = Pipeline.fromDocument(document(paragraph("hello"))).runTo(sink)
            assert(ok.isRight)
            assert(sink.output == "hello\n")
        }
        "renderWith switches renderer" in {
            val result = Pipeline
                .fromDocument(document(paragraph("hello")))
                .renderWith(AsgJsonRenderer)
                .runToString
            assert(result.exists(_.contains("\"name\":\"document\"")))
        }
        "Source.fromStrings creates multi-document tree" in {
            val result = Source
                .fromStrings(
                    (DocumentPath("a.adoc"), "Hello.\n"),
                    (DocumentPath("b.adoc"), "World.\n")
                )
                .read
            assert(result.map(_.size) == Right(2))
        }
        "Source.fromString fails on invalid input" in {
            val result = Source.fromString("[invalid\n").read
            assert(result.isLeft)
        }
        "runToTargets renders through multiple renderers in a single run" in {
            Pipeline
                .fromDocument(document(paragraph("hello")))
                .runToTargets(
                    "adoc" -> AsciiDocRenderer,
                    "json" -> AsgJsonRenderer
                ) match
                case Right(results) =>
                    assert(results.size == 2)
                    assert(results("adoc").values.head == "hello\n")
                    assert(results("json").values.head.contains("\"name\":\"document\""))
                case Left(_) => assert(false)
        }
        "renderAll returns a list of results per renderer" in {
            Pipeline
                .fromDocument(document(paragraph("hello")))
                .renderAll(AsciiDocRenderer, AsgJsonRenderer) match
                case Right(results) =>
                    assert(results.size == 2)
                    assert(results.head.values.head == "hello\n")
                    assert(results(1).values.head.contains("\"name\":\"document\""))
                case Left(_) => assert(false)
        }
        "runToTargets works with multi-document tree" in {
            val tree = DocumentTree.fromDocuments(
                scala.List(
                    (DocumentPath("a.adoc"), document(paragraph("one"))),
                    (DocumentPath("b.adoc"), document(paragraph("two")))
                )
            )
            Pipeline.fromTree(tree).runToTargets("adoc" -> AsciiDocRenderer) match
                case Right(results) =>
                    assert(results("adoc").size == 2)
                    assert(results("adoc")(DocumentPath("a.adoc")) == "one\n")
                    assert(results("adoc")(DocumentPath("b.adoc")) == "two\n")
                case Left(_) => assert(false)
        }
    }

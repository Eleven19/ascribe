package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.{PipelineError, RewriteAction, RewriteRule}
import zio.test.*
import scala.language.implicitConversions

/** Error propagation, empty trees, [[TreeBranch]] rewriting, and sink failures. */
object PipelineEdgeCasesSpec extends ZIOSpecDefault:

    def spec = suite("Pipeline (Ox) edge cases")(
        test("runToString on empty document tree returns empty string") {
            val result = Pipeline.fromTree(DocumentTree.empty).runToString
            assertTrue(result == Right(""))
        },
        test("runToStrings on empty tree yields empty map") {
            val result = Pipeline.fromTree(DocumentTree.empty).runToStrings
            assertTrue(result == Right(Map.empty))
        },
        test("parse failure propagates through run and runToString") {
            val bad = Pipeline.fromString("[not asciiDoc\n").run
            assertTrue(
                bad.isLeft,
                Pipeline.fromString("[bad\n").runToString.isLeft,
                Pipeline.fromString("[bad\n").runToStrings.isLeft
            )
        },
        test("parse failure carries DocumentPath when using fromString with path") {
            val path = DocumentPath("chapter/ch1.adoc")
            Source.fromString("[invalid", path).read match
                case Left(PipelineError.ParseError(_, Some(p))) => assertTrue(p == path)
                case _                                          => assertTrue(false)
        },
        test("runTo propagates sink Left") {
            val failing = new Sink:
                def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                    Left(PipelineError.RenderError("simulated sink failure"))
            val result = Pipeline.fromString("ok.\n").runTo(failing)
            assertTrue(result == Left(PipelineError.RenderError("simulated sink failure")))
        },
        test("rewrite applies to every DocLeaf under a TreeBranch root") {
            val tree = DocumentTree(
                TreeNode.TreeBranch(
                    DocumentPath.root,
                    List(
                        TreeNode.DocLeaf(DocumentPath("a.adoc"), document(paragraph("one"))),
                        TreeNode.DocLeaf(DocumentPath("b.adoc"), document(paragraph("two")))
                    )
                )
            )
            val rule = RewriteRule.forInlines { case Text(t) =>
                RewriteAction.Replace(Text(t.toUpperCase)(Span.unknown))
            }
            Pipeline.fromTree(tree).rewrite(rule).runToStrings match
                case Right(m) =>
                    assertTrue(m.size == 2, m(DocumentPath("a.adoc")) == "ONE\n", m(DocumentPath("b.adoc")) == "TWO\n")
                case Left(_) => assertTrue(false)
        },
        test("runToTargets with zero renderers yields empty outer map") {
            Pipeline.fromString("hi.\n").runToTargets() match
                case Right(m) => assertTrue(m.isEmpty)
                case Left(_)  => assertTrue(false)
        },
        test("renderAll with zero renderers yields empty list") {
            Pipeline.fromString("hi.\n").renderAll() match
                case Right(xs) => assertTrue(xs.isEmpty)
                case Left(_)   => assertTrue(false)
        }
    )

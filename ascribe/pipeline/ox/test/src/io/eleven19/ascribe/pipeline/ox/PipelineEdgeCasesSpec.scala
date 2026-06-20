package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.{PipelineError, RewriteAction, RewriteRule}
import kyo.test.*
import scala.language.implicitConversions

/** Error propagation, empty trees, [[TreeBranch]] rewriting, and sink failures. */
class PipelineEdgeCasesSpec extends Test[Any]:

    "Pipeline (Ox) edge cases" - {
        "runToString on empty document tree returns empty string" in {
            val result = Pipeline.fromTree(DocumentTree.empty).runToString
            assert(result == Right(""))
        }
        "runToStrings on empty tree yields empty map" in {
            val result = Pipeline.fromTree(DocumentTree.empty).runToStrings
            assert(result == Right(Map.empty))
        }
        "parse failure propagates through run and runToString" in {
            val bad = Pipeline.fromString("[not asciiDoc\n").run
            assert(bad.isLeft)
            assert(Pipeline.fromString("[bad\n").runToString.isLeft)
            assert(Pipeline.fromString("[bad\n").runToStrings.isLeft)
        }
        "parse failure carries DocumentPath when using fromString with path" in {
            val path = DocumentPath("chapter/ch1.adoc")
            Source.fromString("[invalid", path).read match
                case Left(PipelineError.ParseError(_, Some(p))) => assert(p == path)
                case _                                          => assert(false)
        }
        "runTo propagates sink Left" in {
            val failing = new Sink:
                def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
                    Left(PipelineError.RenderError("simulated sink failure"))
            val result = Pipeline.fromString("ok.\n").runTo(failing)
            assert(result == Left(PipelineError.RenderError("simulated sink failure")))
        }
        "rewrite applies to every DocLeaf under a TreeBranch root" in {
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
                    assert(m.size == 2)
                    assert(m(DocumentPath("a.adoc")) == "ONE\n")
                    assert(m(DocumentPath("b.adoc")) == "TWO\n")
                case Left(_) => assert(false)
        }
        "runToTargets with zero renderers yields empty outer map" in {
            Pipeline.fromString("hi.\n").runToTargets() match
                case Right(m) => assert(m.isEmpty)
                case Left(_)  => assert(false)
        }
        "renderAll with zero renderers yields empty list" in {
            Pipeline.fromString("hi.\n").renderAll() match
                case Right(xs) => assert(xs.isEmpty)
                case Left(_)   => assert(false)
        }
    }

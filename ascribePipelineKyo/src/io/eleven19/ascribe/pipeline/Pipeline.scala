package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import io.eleven19.ascribe.pipeline.core.{PipelineError, RewriteRule as CoreRewriteRule}
import kyo.{<, Abort}

/** A composable document processing pipeline.
  *
  * Pipelines are built by chaining stages: source → rewrite → render → sink. All stages produce effectful values using
  * Kyo.
  */
case class Pipeline[S] private (
    source: Source[S],
    rules: List[CoreRewriteRule],
    renderer: Renderer[S]
):

    /** Add a rewrite rule to the pipeline. */
    def rewrite(rule: CoreRewriteRule): Pipeline[S] =
        copy(rules = rules :+ rule)

    /** Set a custom renderer. */
    def renderWith(r: Renderer[S]): Pipeline[S] =
        copy(renderer = r)

    /** Execute the pipeline: read from source, apply all rewrites, return the transformed DocumentTree. */
    def run: DocumentTree < S =
        source.read.map { tree =>
            val composedRule = CoreRewriteRule.compose(rules*)
            DocumentTree(rewriteTreeNode(tree.root, composedRule))
        }

    /** Execute the pipeline and render all documents to strings. */
    def runToStrings: Map[DocumentPath, String] < S =
        run.map { tree =>
            renderDocList(tree.allDocuments, Map.empty)
        }

    /** Execute the pipeline and render the first document to a string. */
    def runToString: String < S =
        run.map { tree =>
            tree.allDocuments.headOption match
                case None         => ""
                case Some((_, d)) => renderer.render(d)
        }

    def runToTargets(
        targets: (String, Renderer[Any])*
    ): Map[String, Map[DocumentPath, String]] < S =
        run.map { tree =>
            val docs = tree.allDocuments
            targets.map { (label, r) =>
                val rendered = docs.map { (p, d) =>
                    (p, r.render(d).asInstanceOf[String])
                }.toMap
                (label, rendered)
            }.toMap
        }

    def renderAll(
        renderers: Renderer[Any]*
    ): List[Map[DocumentPath, String]] < S =
        run.map { tree =>
            val docs = tree.allDocuments
            renderers.toList.map { r =>
                docs.map((p, d) => (p, r.render(d).asInstanceOf[String])).toMap
            }
        }

    def runTo[S2](sink: Sink[S2]): Unit < (S & S2) =
        runToStrings.map { rendered =>
            sink.write(rendered)
        }

    private def renderDocList(
        docs: List[(DocumentPath, Document)],
        acc: Map[DocumentPath, String]
    ): Map[DocumentPath, String] < S =
        docs match
            case Nil => acc
            case (p, d) :: rest =>
                renderer.render(d).map { s =>
                    renderDocList(rest, acc + (p -> s))
                }

    private def rewriteTreeNode(node: TreeNode, rule: CoreRewriteRule): TreeNode =
        node match
            case TreeNode.DocLeaf(p, d) =>
                TreeNode.DocLeaf(p, CoreRewriteRule.rewrite(d, rule))
            case TreeNode.TreeBranch(p, kids) =>
                TreeNode.TreeBranch(p, kids.map(rewriteTreeNode(_, rule)))

object Pipeline:

    def from[S](source: Source[S]): Pipeline[S] =
        Pipeline(source, Nil, AsciiDocRenderer.asInstanceOf[Renderer[S]])

    def fromString(content: String): Pipeline[Abort[PipelineError]] =
        from(Source.fromString(content))

    def fromString(content: String, path: DocumentPath): Pipeline[Abort[PipelineError]] =
        from(Source.fromString(content, path))

    def fromTree(tree: DocumentTree): Pipeline[Any] =
        from(Source.fromTree(tree))

    def fromDocument(document: Document): Pipeline[Any] =
        from(Source.fromDocument(document))

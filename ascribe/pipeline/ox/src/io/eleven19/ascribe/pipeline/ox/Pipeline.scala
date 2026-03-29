package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import io.eleven19.ascribe.pipeline.core.{PipelineError, RewriteRule as CoreRewriteRule}

case class Pipeline private (
    source: Source,
    rules: List[CoreRewriteRule],
    renderer: Renderer
):

    def rewrite(rule: CoreRewriteRule): Pipeline =
        copy(rules = rules :+ rule)

    def renderWith(r: Renderer): Pipeline =
        copy(renderer = r)

    def run: Either[PipelineError, DocumentTree] =
        source.read.map { tree =>
            val composedRule = CoreRewriteRule.compose(rules*)
            DocumentTree(rewriteTreeNode(tree.root, composedRule))
        }

    def runToStrings: Either[PipelineError, Map[DocumentPath, String]] =
        run.map { tree =>
            renderDocList(tree.allDocuments, Map.empty)
        }

    def runToString: Either[PipelineError, String] =
        run.map { tree =>
            tree.allDocuments.headOption match
                case None         => ""
                case Some((_, d)) => renderer.render(d)
        }

    def runToTargets(
        targets: (String, Renderer)*
    ): Either[PipelineError, Map[String, Map[DocumentPath, String]]] =
        run.map { tree =>
            val docs = tree.allDocuments
            targets.map { (label, r) =>
                val rendered = docs.map { (p, d) =>
                    (p, r.render(d))
                }.toMap
                (label, rendered)
            }.toMap
        }

    def renderAll(
        renderers: Renderer*
    ): Either[PipelineError, List[Map[DocumentPath, String]]] =
        run.map { tree =>
            val docs = tree.allDocuments
            renderers.toList.map { r =>
                docs.map((p, d) => (p, r.render(d))).toMap
            }
        }

    def runTo(sink: Sink): Either[PipelineError, Unit] =
        runToStrings.flatMap(sink.write)

    private def renderDocList(
        docs: List[(DocumentPath, Document)],
        acc: Map[DocumentPath, String]
    ): Map[DocumentPath, String] =
        docs match
            case Nil => acc
            case (p, d) :: rest =>
                renderDocList(rest, acc + (p -> renderer.render(d)))

    private def rewriteTreeNode(node: TreeNode, rule: CoreRewriteRule): TreeNode =
        node match
            case TreeNode.DocLeaf(p, d) =>
                TreeNode.DocLeaf(p, CoreRewriteRule.rewrite(d, rule))
            case TreeNode.TreeBranch(p, kids) =>
                TreeNode.TreeBranch(p, kids.map(rewriteTreeNode(_, rule)))

object Pipeline:

    def from(source: Source): Pipeline =
        Pipeline(source, Nil, AsciiDocRenderer)

    def fromString(content: String): Pipeline =
        from(Source.fromString(content))

    def fromString(content: String, path: DocumentPath): Pipeline =
        from(Source.fromString(content, path))

    def fromTree(tree: DocumentTree): Pipeline =
        from(Source.fromTree(tree))

    def fromDocument(document: Document): Pipeline =
        from(Source.fromDocument(document))

package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree, TreeNode}
import kyo.{<, Abort}

/** A composable document processing pipeline.
  *
  * Pipelines are built by chaining stages: source → rewrite → render → sink. All stages produce effectful values using
  * Kyo.
  *
  * {{{
  * val output = Pipeline
  *     .fromString("= Title\n\nHello.\n")
  *     .rewrite(removeComments)
  *     .run
  * }}}
  */
case class Pipeline[S] private (
    source: Source[S],
    rules: List[RewriteRule[S]],
    renderer: Renderer[S]
):

    /** Add a rewrite rule to the pipeline. Pure rules (`RewriteRule[Any]`) are accepted for any pipeline. */
    def rewrite(rule: RewriteRule[Any]): Pipeline[S] =
        // RewriteRule[Any] is safe to widen to RewriteRule[S]: it carries no effects and S only appears
        // in covariant position in the rule's return type. A RewriteRule variance declaration would remove this cast.
        copy(rules = rules :+ rule.asInstanceOf[RewriteRule[S]])

    /** Set a custom renderer. */
    def renderWith(r: Renderer[S]): Pipeline[S] =
        copy(renderer = r)

    /** Execute the pipeline: read from source, apply all rewrites, return the transformed DocumentTree. */
    def run: DocumentTree < S =
        source.read.map { tree =>
            val composedRule = RewriteRule.compose(rules*)
            rewriteTreeNode(tree.root, composedRule).map(DocumentTree(_))
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

    /** Execute the pipeline and render all documents through multiple renderers.
      *
      * Runs the source and rewrite stages once, then renders each document through every provided renderer. Returns a
      * map from (renderer label, document path) to rendered string.
      *
      * {{{
      * val results = pipeline.runToTargets(
      *     "adoc" -> AsciiDocRenderer,
      *     "json" -> AsgJsonRenderer
      * )
      * // results("adoc")(DocumentPath("doc.adoc")) == "= Title\n..."
      * // results("json")(DocumentPath("doc.adoc")) == "{\"name\":\"document\"..."
      * }}}
      */
    def runToTargets(
        targets: (String, Renderer[Any])*
    ): Map[String, Map[DocumentPath, String]] < S =
        run.map { tree =>
            val docs = tree.allDocuments
            targets.map { (label, r) =>
                // r is Renderer[Any] (effectless): String < Any == String at runtime in Kyo.
                val rendered = docs.map { (p, d) =>
                    (p, r.render(d).asInstanceOf[String])
                }.toMap
                (label, rendered)
            }.toMap
        }

    /** Execute the pipeline and render all documents through multiple renderers.
      *
      * Like `runToTargets` but returns a list of results (one per renderer) without labels.
      *
      * {{{
      * val List(adocResults, jsonResults) = pipeline.renderAll(AsciiDocRenderer, AsgJsonRenderer)
      * }}}
      */
    def renderAll(
        renderers: Renderer[Any]*
    ): List[Map[DocumentPath, String]] < S =
        run.map { tree =>
            val docs = tree.allDocuments
            // Renderer[Any] is effectless: String < Any == String at runtime in Kyo.
            renderers.toList.map { r =>
                docs.map((p, d) => (p, r.render(d).asInstanceOf[String])).toMap
            }
        }

    /** Execute the pipeline and write output to a sink. The sink's effect type is combined with the pipeline's. */
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

    private def rewriteTreeNode(node: TreeNode, rule: RewriteRule[S]): TreeNode < S =
        node match
            case TreeNode.DocLeaf(p, d) =>
                RewriteRule.rewrite(d, rule).map(TreeNode.DocLeaf(p, _))
            case TreeNode.TreeBranch(p, kids) =>
                rewriteNodeList(kids, rule).map(TreeNode.TreeBranch(p, _))

    private def rewriteNodeList(nodes: List[TreeNode], rule: RewriteRule[S]): List[TreeNode] < S =
        nodes match
            case Nil => Nil
            case head :: tail =>
                rewriteTreeNode(head, rule).map { h =>
                    rewriteNodeList(tail, rule).map(h :: _)
                }

object Pipeline:

    /** Create a pipeline from a source with default AsciiDoc rendering. */
    def from[S](source: Source[S]): Pipeline[S] =
        // AsciiDocRenderer is Renderer[Any] (no effects). Widening to Renderer[S] is safe because the render method
        // only uses S in its return type, and Any is compatible with any S at runtime. Declaring Renderer covariant
        // would remove this cast.
        Pipeline(source, Nil, AsciiDocRenderer.asInstanceOf[Renderer[S]])

    /** Create a pipeline from a single AsciiDoc string. */
    def fromString(content: String): Pipeline[Abort[PipelineError]] =
        from(Source.fromString(content))

    /** Create a pipeline from a single AsciiDoc string with a path. */
    def fromString(content: String, path: DocumentPath): Pipeline[Abort[PipelineError]] =
        from(Source.fromString(content, path))

    /** Create a pipeline from an existing DocumentTree. */
    def fromTree(tree: DocumentTree): Pipeline[Any] =
        from(Source.fromTree(tree))

    /** Create a pipeline from an already-parsed Document. */
    def fromDocument(document: Document): Pipeline[Any] =
        from(Source.fromDocument(document))

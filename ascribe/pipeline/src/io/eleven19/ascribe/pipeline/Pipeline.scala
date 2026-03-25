package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
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
        copy(rules = rules :+ rule.asInstanceOf[RewriteRule[S]])

    /** Set a custom renderer. */
    def renderWith(r: Renderer[S]): Pipeline[S] =
        copy(renderer = r)

    /** Execute the pipeline: read from source, apply all rewrites, return the transformed DocumentTree. */
    def run: DocumentTree < S =
        source.read.map { tree =>
            val composedRule = RewriteRule.compose(rules*)
            tree.allDocuments match
                case Nil => tree
                case docs =>
                    val rewritten = docs.map { (path, doc) =>
                        RewriteRule.rewrite(doc, composedRule).map(d => (path, d))
                    }
                    rewriteAll(rewritten)
        }

    /** Execute the pipeline and render all documents to strings. */
    def runToStrings: Map[DocumentPath, String] < S =
        run.map { tree =>
            tree.allDocuments.map { (p, d) =>
                (p, renderer.render(d))
            }.toMap.asInstanceOf[Map[DocumentPath, String]]
        }

    /** Execute the pipeline and render the first document to a string. */
    def runToString: String < S =
        run.map { tree =>
            tree.allDocuments.headOption
                .map((_, d) => renderer.render(d))
                .getOrElse("")
                .asInstanceOf[String]
        }

    /** Execute the pipeline and write output to a sink. */
    def runTo(sink: Sink[Any]): Unit < S =
        run.map { tree =>
            sink.write(tree, d => renderer.render(d).asInstanceOf[String])
        }

    private def rewriteAll(
        docs: List[(DocumentPath, Document) < S]
    ): DocumentTree < S =
        docs match
            case Nil => DocumentTree.empty
            case head :: tail =>
                head.map { h =>
                    rewriteAll(tail).map { rest =>
                        DocumentTree.fromDocuments(h :: rest.allDocuments)
                    }
                }

object Pipeline:

    /** Create a pipeline from a source with default AsciiDoc rendering. */
    def from[S](source: Source[S]): Pipeline[S] =
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

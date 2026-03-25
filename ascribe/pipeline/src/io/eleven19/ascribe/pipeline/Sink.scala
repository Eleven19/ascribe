package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.{Document, DocumentPath, DocumentTree}
import kyo.*

/** A destination for rendered output.
  *
  * Sinks receive a DocumentTree and a rendering function, writing output to some backing store.
  */
trait Sink[S]:
    /** Write rendered documents to this sink. */
    def write(tree: DocumentTree, render: Document => String): Unit < S

object Sink:

    /** A sink that collects all output into a Map (for testing). */
    def toMap(): MapSink = new MapSink

    /** A sink that collects output from a single document into a String (for testing). */
    def toStringResult(): StringSink = new StringSink

/** A sink that collects rendered output into a Map keyed by DocumentPath. */
class MapSink extends Sink[Any]:
    private var results: Map[DocumentPath, String] = Map.empty

    def write(tree: DocumentTree, render: Document => String): Unit < Any =
        results = tree.allDocuments.map((p, d) => (p, render(d))).toMap

    /** Retrieve the collected output. */
    def output: Map[DocumentPath, String] = results

/** A sink that collects the first document's rendered output into a String. */
class StringSink extends Sink[Any]:
    private var result: String = ""

    def write(tree: DocumentTree, render: Document => String): Unit < Any =
        result = tree.allDocuments.headOption.map((_, d) => render(d)).getOrElse("")

    /** Retrieve the collected output. */
    def output: String = result

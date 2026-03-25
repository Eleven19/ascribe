package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.{DocumentPath, DocumentTree}
import kyo.*

/** A destination for rendered output.
  *
  * Sinks receive pre-rendered output as a map of document paths to strings, and write it to some backing store. The
  * rendering step (which may carry effects) is completed by the pipeline before calling the sink.
  */
trait Sink[S]:
    /** Write pre-rendered documents to this sink. */
    def write(rendered: Map[DocumentPath, String]): Unit < S

object Sink:

    /** A sink that collects all output into a Map (for testing). */
    def toMap(): MapSink = new MapSink

    /** A sink that collects output from a single document into a String (for testing). */
    def toStringResult(): StringSink = new StringSink

/** A sink that collects rendered output into a Map keyed by DocumentPath.
  *
  * Note: calling `write` multiple times replaces the previous results.
  */
class MapSink extends Sink[Any]:
    private var results: Map[DocumentPath, String] = Map.empty

    def write(rendered: Map[DocumentPath, String]): Unit < Any =
        results = rendered

    /** Retrieve the collected output. */
    def output: Map[DocumentPath, String] = results

/** A sink that collects the first document's rendered output into a String.
  *
  * Note: calling `write` multiple times replaces the previous result.
  */
class StringSink extends Sink[Any]:
    private var result: String = ""

    def write(rendered: Map[DocumentPath, String]): Unit < Any =
        result = rendered.headOption.map(_._2).getOrElse("")

    /** Retrieve the collected output. */
    def output: String = result

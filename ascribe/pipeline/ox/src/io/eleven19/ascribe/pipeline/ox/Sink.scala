package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.DocumentPath
import io.eleven19.ascribe.pipeline.core.PipelineError

trait Sink:
    def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit]

object Sink:

    def toMap(): MapSink = new MapSink

    def toStringResult(): StringSink = new StringSink

class MapSink extends Sink:
    private var results: Map[DocumentPath, String] = Map.empty

    def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
        results = rendered
        Right(())

    def output: Map[DocumentPath, String] = results

class StringSink extends Sink:
    private var result: String = ""

    def write(rendered: Map[DocumentPath, String]): Either[PipelineError, Unit] =
        result = rendered.headOption.map(_._2).getOrElse("")
        Right(())

    def output: String = result

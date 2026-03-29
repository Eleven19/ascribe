package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.{Comment, Document, Span}
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.ox.dsl.{removeComments, stripFormatting}
import zio.test.*
import scala.language.implicitConversions

/** End-to-end checks for exported [[dsl]] rules. */
object PipelineDslSpec extends ZIOSpecDefault:

    def spec = suite("pipeline ox dsl integration")(
        test("removeComments strips Comment blocks from AST-backed pipeline") {
            val doc = Document(
                None,
                List(
                    Comment("////", "dropped\n")(Span.unknown),
                    paragraph("kept.")
                )
            )(Span.unknown)
            Pipeline.fromDocument(doc).rewrite(removeComments).runToString match
                case Right(s) =>
                    assertTrue(!s.contains("dropped"), s.contains("kept"))
                case Left(_) => assertTrue(false)
        },
        test("stripFormatting flattens bold to plain text in output") {
            Pipeline.fromString("Hello *world*.\n").rewrite(stripFormatting).runToString match
                case Right(s) =>
                    assertTrue(s.contains("Hello world."), !s.contains("*world*"))
                case Left(_) => assertTrue(false)
        }
    )

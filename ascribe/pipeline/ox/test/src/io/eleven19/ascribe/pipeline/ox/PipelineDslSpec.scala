package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.{Comment, Document, Span}
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.ox.dsl.{removeComments, stripFormatting}
import kyo.test.*
import scala.language.implicitConversions

/** End-to-end checks for exported [[dsl]] rules. */
class PipelineDslSpec extends Test[Any]:

    "pipeline ox dsl integration" - {
        "removeComments strips Comment blocks from AST-backed pipeline" in {
            val doc = Document(
                None,
                List(
                    Comment("////", "dropped\n")(Span.unknown),
                    paragraph("kept.")
                )
            )(Span.unknown)
            Pipeline.fromDocument(doc).rewrite(removeComments).runToString match
                case Right(s) =>
                    assert(!s.contains("dropped"))
                    assert(s.contains("kept"))
                case Left(_) => assert(false)
        }
        "stripFormatting flattens bold to plain text in output" in {
            Pipeline.fromString("Hello *world*.\n").rewrite(stripFormatting).runToString match
                case Right(s) =>
                    assert(s.contains("Hello world."))
                    assert(!s.contains("*world*"))
                case Left(_) => assert(false)
        }
    }

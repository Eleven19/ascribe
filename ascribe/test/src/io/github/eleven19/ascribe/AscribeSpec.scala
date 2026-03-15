package io.github.eleven19.ascribe

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.ast.{Block, Document, Inline}

object AscribeSpec extends ZIOSpecDefault:

    def spec = suite("Ascribe public API")(
        test("parses a simple heading document") {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assertTrue(
                        doc == Document(
                            List(Block.Heading(1, List(Inline.Text("Hello World"))))
                        )
                    )
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("parses a paragraph") {
            Ascribe.parse("Hello world.\n") match
                case Success(doc) =>
                    assertTrue(
                        doc == Document(List(Block.Paragraph(List(Inline.Text("Hello world.")))))
                    )
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("empty input produces an empty document") {
            Ascribe.parse("") match
                case Success(doc) => assertTrue(doc == Document(List()))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        }
    )

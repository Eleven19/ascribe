package io.github.eleven19.ascribe

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.TestHelpers.*

object AscribeSpec extends ZIOSpecDefault:

    def spec = suite("Ascribe public API")(
        test("parses a simple heading document") {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assertTrue(doc == document(heading(1, text("Hello World"))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("parses a paragraph") {
            Ascribe.parse("Hello world.\n") match
                case Success(doc) =>
                    assertTrue(doc == document(paragraph(text("Hello world."))))
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        },
        test("empty input produces an empty document") {
            Ascribe.parse("") match
                case Success(doc) => assertTrue(doc == document())
                case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
        }
    )

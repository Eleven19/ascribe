package io.eleven19.ascribe

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.ast.dsl.{*, given}

object AscribeSpec extends ZIOSpecDefault:

    def spec = suite("Ascribe public API")(
        test("parses a simple heading document") {
            Ascribe.parse("= Hello World\n") match
                case Success(doc) =>
                    assertTrue(doc == document(documentHeader(text("Hello World"))))
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

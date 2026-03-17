package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.TestHelpers.*
import io.eleven19.ascribe.ast.Inline
import io.eleven19.ascribe.parser.InlineParser.lineContent

object InlineParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = lineContent.parse(input)

    def spec = suite("InlineParser")(
        suite("plain text")(
            test("parses a simple word") {
                parse("hello") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("hello")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a sentence with spaces") {
                parse("hello world") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("hello world")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("stops at newline") {
                parse("line one\nline two") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("line one")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bold spans")(
            test("parses **bold** text") {
                parse("**bold**") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(bold(text("bold"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bold embedded in text") {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(text("hello "), bold(text("world")), text(" end"))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("treats lone * as plain text") {
                parse("a*b") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(text("a"), text("*"), text("b")))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("italic spans")(
            test("parses __italic__ text") {
                parse("__italic__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(italic(text("italic"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("monospace spans")(
            test("parses ``mono`` text") {
                parse("``mono``") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(mono(text("mono"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mixed inline")(
            test("parses bold and italic together") {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(bold(text("b")), text(" and "), italic(text("i")))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

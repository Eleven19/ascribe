package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.ast.Span
import io.eleven19.ascribe.cst.{CstBold, CstItalic, CstMono, CstText}
import io.eleven19.ascribe.parser.InlineParser.lineContent

object InlineParserSpec extends ZIOSpecDefault:
    private val u = Span.unknown
    private def parse(input: String) = lineContent.parse(input)

    def spec = suite("InlineParser")(
        suite("plain text")(
            test("parses a simple word") {
                parse("hello") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("hello")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a sentence with spaces") {
                parse("hello world") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("hello world")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("stops at newline") {
                parse("line one\nline two") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("line one")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bold spans")(
            test("parses **bold** text") {
                parse("**bold**") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstBold(List(CstText("bold")(u)), false)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bold embedded in text") {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstText("hello ")(u),
                            CstBold(List(CstText("world")(u)), false)(u),
                            CstText(" end")(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("treats lone * as plain text") {
                parse("a*b") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstText("a")(u), CstText("*")(u), CstText("b")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("italic spans")(
            test("parses __italic__ text") {
                parse("__italic__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstItalic(List(CstText("italic")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("monospace spans")(
            test("parses ``mono`` text") {
                parse("``mono``") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMono(List(CstText("mono")(u)))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mixed inline")(
            test("parses bold and italic together") {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(
                            CstBold(List(CstText("b")(u)), false)(u),
                            CstText(" and ")(u),
                            CstItalic(List(CstText("i")(u)))(u)
                        ))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

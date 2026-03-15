package io.github.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.ast.{Inline, InlineContent}
import io.github.eleven19.ascribe.parser.InlineParser.lineContent

object InlineParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = lineContent.parse(input)

    def spec = suite("InlineParser")(
        suite("plain text")(
            test("parses a simple word") {
                parse("hello") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Text("hello")))
                    case Failure(_) => assertTrue(false)
            },
            test("parses a sentence with spaces") {
                parse("hello world") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Text("hello world")))
                    case Failure(_) => assertTrue(false)
            },
            test("stops at newline") {
                parse("line one\nline two") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Text("line one")))
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("bold spans")(
            test("parses **bold** text") {
                parse("**bold**") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Bold(List(Inline.Text("bold")))))
                    case Failure(_) => assertTrue(false)
            },
            test("parses bold embedded in text") {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                Inline.Text("hello "),
                                Inline.Bold(List(Inline.Text("world"))),
                                Inline.Text(" end")
                            )
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("treats lone * as plain text") {
                parse("a*b") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Text("a"), Inline.Text("*"), Inline.Text("b")))
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("italic spans")(
            test("parses __italic__ text") {
                parse("__italic__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Italic(List(Inline.Text("italic")))))
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("monospace spans")(
            test("parses ``mono`` text") {
                parse("``mono``") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(Inline.Mono(List(Inline.Text("mono")))))
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("mixed inline")(
            test("parses bold and italic together") {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                Inline.Bold(List(Inline.Text("b"))),
                                Inline.Text(" and "),
                                Inline.Italic(List(Inline.Text("i")))
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        )
    )

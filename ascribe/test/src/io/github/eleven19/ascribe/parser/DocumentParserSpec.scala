package io.github.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.github.eleven19.ascribe.ast.{Block, Document, Inline, ListItem}
import io.github.eleven19.ascribe.parser.DocumentParser.document

object DocumentParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = document.parse(input)

    def spec = suite("DocumentParser")(
        suite("headings")(
            test("parses a level-1 heading") {
                parse("= Title\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(List(Block.Heading(1, List(Inline.Text("Title")))))
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("parses a level-3 heading") {
                parse("=== Section\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(List(Block.Heading(3, List(Inline.Text("Section")))))
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("parses a heading with inline bold") {
                parse("== **Bold** Title\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.Heading(
                                        2,
                                        List(
                                            Inline.Bold(List(Inline.Text("Bold"))),
                                            Inline.Text(" Title")
                                        )
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("paragraphs")(
            test("parses a single-line paragraph") {
                parse("Hello world.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(List(Block.Paragraph(List(Inline.Text("Hello world.")))))
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("parses a paragraph with inline markup") {
                parse("Use **parsley** to parse.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.Paragraph(
                                        List(
                                            Inline.Text("Use "),
                                            Inline.Bold(List(Inline.Text("parsley"))),
                                            Inline.Text(" to parse.")
                                        )
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("unordered lists")(
            test("parses a single-item unordered list") {
                parse("* item one\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.UnorderedList(
                                        List(ListItem(List(Inline.Text("item one"))))
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("parses a multi-item unordered list") {
                parse("* alpha\n* beta\n* gamma\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.UnorderedList(
                                        List(
                                            ListItem(List(Inline.Text("alpha"))),
                                            ListItem(List(Inline.Text("beta"))),
                                            ListItem(List(Inline.Text("gamma")))
                                        )
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("ordered lists")(
            test("parses a multi-item ordered list") {
                parse(". first\n. second\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.OrderedList(
                                        List(
                                            ListItem(List(Inline.Text("first"))),
                                            ListItem(List(Inline.Text("second")))
                                        )
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        ),
        suite("multi-block documents")(
            test("parses heading followed by paragraph") {
                parse("= Title\n\nIntroduction text.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.Heading(1, List(Inline.Text("Title"))),
                                    Block.Paragraph(List(Inline.Text("Introduction text.")))
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            },
            test("parses heading, paragraph and list") {
                val input = "= Guide\n\nRead the steps:\n\n* step one\n* step two\n"
                parse(input) match
                    case Success(doc) =>
                        assertTrue(
                            doc == Document(
                                List(
                                    Block.Heading(1, List(Inline.Text("Guide"))),
                                    Block.Paragraph(List(Inline.Text("Read the steps:"))),
                                    Block.UnorderedList(
                                        List(
                                            ListItem(List(Inline.Text("step one"))),
                                            ListItem(List(Inline.Text("step two")))
                                        )
                                    )
                                )
                            )
                        )
                    case Failure(_) => assertTrue(false)
            }
        )
    )

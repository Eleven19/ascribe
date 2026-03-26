package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.dsl.{*, given}

object DocumentParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = Ascribe.parse(input)

    def spec = suite("DocumentParser")(
        suite("headings")(
            test("parses a level-1 heading as document header") {
                parse("= Title\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(documentHeader(text("Title"))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a level-3 heading as section") {
                parse("=== Section\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(section(2, List(text("Section")))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a heading with inline bold as section") {
                parse("== **Bold** Title\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                section(1, List(bold(text("Bold")), text(" Title")))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("paragraphs")(
            test("parses a single-line paragraph") {
                parse("Hello world.\n") match
                    case Success(doc) =>
                        assertTrue(doc == document(paragraph(text("Hello world."))))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a paragraph with inline markup") {
                parse("Use **parsley** to parse.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                paragraph(text("Use "), bold(text("parsley")), text(" to parse."))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("unordered lists")(
            test("parses a single-item unordered list") {
                parse("* item one\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(unorderedList(listItem(text("item one"))))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a multi-item unordered list") {
                parse("* alpha\n* beta\n* gamma\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                unorderedList(
                                    listItem(text("alpha")),
                                    listItem(text("beta")),
                                    listItem(text("gamma"))
                                )
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("ordered lists")(
            test("parses a multi-item ordered list") {
                parse(". first\n. second\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                orderedList(listItem(text("first")), listItem(text("second")))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("multi-block documents")(
            test("parses heading followed by paragraph") {
                parse("= Title\n\nIntroduction text.\n") match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                documentHeader(text("Title")),
                                paragraph(text("Introduction text."))
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses heading, paragraph and list") {
                val input = "= Guide\n\nRead the steps:\n\n* step one\n* step two\n"
                parse(input) match
                    case Success(doc) =>
                        assertTrue(
                            doc == document(
                                documentHeader(text("Guide")),
                                paragraph(text("Read the steps:")),
                                unorderedList(
                                    listItem(text("step one")),
                                    listItem(text("step two"))
                                )
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("header attribute entries")(
            test("parses :!name: unset entry in document header") {
                Ascribe.parseCst("= Doc\n:!my-attr:\n\nPara.\n") match
                    case Success(cst) =>
                        val unsetEntries = cst.header.toList.flatMap(_.attributes).filter(_.unset)
                        assertTrue(unsetEntries.nonEmpty) &&
                        assertTrue(unsetEntries.head.name == "my-attr")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test(":!name: in header does not appear in lowered attributes list") {
                Ascribe.parse("= Doc\n:!my-attr:\n\nPara.\n") match
                    case Success(doc) =>
                        val attrNames = doc.header.toList.flatMap(_.attributes).map(_._1)
                        assertTrue(!attrNames.contains("!my-attr")) &&
                        assertTrue(!attrNames.contains("my-attr"))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

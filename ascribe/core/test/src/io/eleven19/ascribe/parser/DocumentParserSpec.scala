package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import kyo.test.*

import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.dsl.{*, given}

class DocumentParserSpec extends Test[Any]:
    private def parse(input: String) = Ascribe.parse(input)

    "DocumentParser" - {
        "headings" - {
            "parses a level-1 heading as document header" in {
                parse("= Title\n") match
                    case Success(doc) =>
                        assert(doc == document(documentHeader(text("Title"))))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses a level-3 heading as section" in {
                parse("=== Section\n") match
                    case Success(doc) =>
                        assert(doc == document(section(2, List(text("Section")))))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses a heading with inline bold as section" in {
                parse("== **Bold** Title\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                section(1, List(bold(text("Bold")), text(" Title")))
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "paragraphs" - {
            "parses a single-line paragraph" in {
                parse("Hello world.\n") match
                    case Success(doc) =>
                        assert(doc == document(paragraph(text("Hello world."))))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses a paragraph with inline markup" in {
                parse("Use **parsley** to parse.\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                paragraph(text("Use "), bold(text("parsley")), text(" to parse."))
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "unordered lists" - {
            "parses a single-item unordered list" in {
                parse("* item one\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(unorderedList(listItem(text("item one"))))
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses a multi-item unordered list" in {
                parse("* alpha\n* beta\n* gamma\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                unorderedList(
                                    listItem(text("alpha")),
                                    listItem(text("beta")),
                                    listItem(text("gamma"))
                                )
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "ordered lists" - {
            "parses a multi-item ordered list" in {
                parse(". first\n. second\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                orderedList(listItem(text("first")), listItem(text("second")))
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "multi-block documents" - {
            "parses heading followed by paragraph" in {
                parse("= Title\n\nIntroduction text.\n") match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                documentHeader(text("Title")),
                                paragraph(text("Introduction text."))
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses heading, paragraph and list" in {
                val input = "= Guide\n\nRead the steps:\n\n* step one\n* step two\n"
                parse(input) match
                    case Success(doc) =>
                        assert(
                            doc == document(
                                documentHeader(text("Guide")),
                                paragraph(text("Read the steps:")),
                                unorderedList(
                                    listItem(text("step one")),
                                    listItem(text("step two"))
                                )
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "header attribute entries" - {
            "parses :!name: unset entry in document header" in {
                Ascribe.parseCst("= Doc\n:!my-attr:\n\nPara.\n") match
                    case Success(cst) =>
                        val unsetEntries = cst.header.toList.flatMap(_.attributes).filter(_.unset)
                        assert(unsetEntries.nonEmpty)
                        assert(unsetEntries.head.name == "my-attr")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            ":!name: in header does not appear in lowered attributes list" in {
                Ascribe.parse("= Doc\n:!my-attr:\n\nPara.\n") match
                    case Success(doc) =>
                        val attrNames = doc.header.toList.flatMap(_.attributes).map(_._1)
                        assert(!attrNames.contains("!my-attr"))
                        assert(!attrNames.contains("my-attr"))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
    }

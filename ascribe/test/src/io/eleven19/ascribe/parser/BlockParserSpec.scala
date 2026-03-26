package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.cst.{CstAdmonitionParagraph, CstAttributeEntry, CstBlock}
import io.eleven19.ascribe.parser.BlockParser.{admonitionParagraphBlock, attributeEntryBlock}

object BlockParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = attributeEntryBlock.parse(input)

    def spec = suite("BlockParser")(
        suite("admonitionParagraphBlock")(
            test("parses NOTE: paragraph") {
                admonitionParagraphBlock.parse("NOTE: Watch out.\n") match
                    case Success(CstAdmonitionParagraph("NOTE", content)) =>
                        assertTrue(content.nonEmpty)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses TIP: paragraph") {
                admonitionParagraphBlock.parse("TIP: Try this.\n") match
                    case Success(CstAdmonitionParagraph("TIP", _)) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("parses IMPORTANT: paragraph") {
                admonitionParagraphBlock.parse("IMPORTANT: Read this.\n") match
                    case Success(CstAdmonitionParagraph("IMPORTANT", _)) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("parses CAUTION: paragraph") {
                admonitionParagraphBlock.parse("CAUTION: Be careful.\n") match
                    case Success(CstAdmonitionParagraph("CAUTION", _)) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("parses WARNING: paragraph") {
                admonitionParagraphBlock.parse("WARNING: Danger.\n") match
                    case Success(CstAdmonitionParagraph("WARNING", _)) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("NOTE without colon+space is NOT an admonition") {
                admonitionParagraphBlock.parse("NOTE something\n") match
                    case Failure(_) => assertTrue(true)
                    case Success(r) => assertTrue(s"should have failed, got: $r" == "")
            }
        ),
        suite("attributeEntryBlock")(
            test("parses normal attribute entry") {
                parse(":my-attr: some value\n") match
                    case Success(CstAttributeEntry("my-attr", "some value", false)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses unset attribute entry :!name:") {
                parse(":!my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", true)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses empty-value attribute entry") {
                parse(":my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", false)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

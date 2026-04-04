package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.cst.{CstAdmonitionParagraph, CstAttributeEntry, CstBlock, CstDelimitedBlock, CstParagraph, CstText}
import io.eleven19.ascribe.parser.BlockParser.{admonitionParagraphBlock, attributeEntryBlock, attributedParagraph, attributeListLine, block}

object BlockParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = attributeEntryBlock.parse(input)

    private def titleText(p: CstParagraph): String =
        p.title.map(_.content.collect { case CstText(c) => c }.mkString).getOrElse("")

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
                    case other                                     => assertTrue(s"unexpected: $other" == "")
            },
            test("parses IMPORTANT: paragraph") {
                admonitionParagraphBlock.parse("IMPORTANT: Read this.\n") match
                    case Success(CstAdmonitionParagraph("IMPORTANT", _)) => assertTrue(true)
                    case other                                           => assertTrue(s"unexpected: $other" == "")
            },
            test("parses CAUTION: paragraph") {
                admonitionParagraphBlock.parse("CAUTION: Be careful.\n") match
                    case Success(CstAdmonitionParagraph("CAUTION", _)) => assertTrue(true)
                    case other                                         => assertTrue(s"unexpected: $other" == "")
            },
            test("parses WARNING: paragraph") {
                admonitionParagraphBlock.parse("WARNING: Danger.\n") match
                    case Success(CstAdmonitionParagraph("WARNING", _)) => assertTrue(true)
                    case other                                         => assertTrue(s"unexpected: $other" == "")
            },
            test("NOTE without colon+space is NOT an admonition") {
                admonitionParagraphBlock.parse("NOTE something\n") match
                    case Failure(_) => assertTrue(true)
                    case Success(r) => assertTrue(s"should have failed, got: $r" == "")
            }
        ),
        suite("attributedParagraph")(
            test("parses [.lead] followed by paragraph text") {
                attributedParagraph.parse("[.lead]\nThis is a lead paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        assertTrue(p.attributes.isDefined) &&
                        assertTrue(p.attributes.get.roles == List("lead")) &&
                        assertTrue(p.lines.nonEmpty)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("attributeListLine parses [.lead]") {
                attributeListLine.parse("[.lead]\n") match
                    case Success(al) =>
                        assertTrue(al.roles == List("lead"))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("block parses [.lead] + paragraph") {
                block.parse("[.lead]\nThis is a lead paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        assertTrue(p.attributes.isDefined) &&
                        assertTrue(p.attributes.get.roles == List("lead"))
                    case Success(other) => assertTrue(s"unexpected type: ${other.getClass.getSimpleName}" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("merges multiple attribute list lines into one") {
                attributedParagraph.parse("[.role1]\n[.role2]\nMulti-role paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        val roles = p.attributes.map(_.roles).getOrElse(Nil)
                        assertTrue(roles == List("role1", "role2")) &&
                        assertTrue(p.lines.nonEmpty)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses named attributes on paragraph") {
                attributedParagraph.parse("[key=value]\nParagraph with named attr.\n") match
                    case Success(p: CstParagraph) =>
                        assertTrue(p.attributes.isDefined) &&
                        assertTrue(p.attributes.get.named.get("key").contains("value"))
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses option attribute on paragraph") {
                attributedParagraph.parse("[%hardbreaks]\nParagraph with option.\n") match
                    case Success(p: CstParagraph) =>
                        assertTrue(p.attributes.isDefined) &&
                        assertTrue(p.attributes.get.options.contains("hardbreaks"))
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses block title with attribute list before paragraph") {
                attributedParagraph.parse(".My Title\n[.lead]\nParagraph text.\n") match
                    case Success(p: CstParagraph) =>
                        assertTrue(p.title.isDefined) &&
                        assertTrue(titleText(p) == "My Title") &&
                        assertTrue(p.attributes.isDefined) &&
                        assertTrue(p.attributes.get.roles == List("lead")) &&
                        assertTrue(p.lines.nonEmpty)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("fails on plain text with no attribute list") {
                attributedParagraph.parse("This is a plain paragraph.\n") match
                    case Failure(_) => assertTrue(true)
                    case Success(r) => assertTrue(s"should have failed, got: $r" == "")
            },
            test("fails on attribute list with no following paragraph lines") {
                attributedParagraph.parse("[.lead]\n") match
                    case Failure(_) => assertTrue(true)
                    case Success(r) => assertTrue(s"should have failed, got: $r" == "")
            },
            test("fails and backtracks when attribute list is followed by a delimited block") {
                attributedParagraph.parse("[source,ruby]\n----\ncode\n----\n") match
                    case Failure(_) => assertTrue(true)
                    case Success(r) => assertTrue(s"should have failed, got: $r" == "")
            }
        ),
        suite("block (backtracking)")(
            test("[source,ruby] + listing delimiter routes to delimited block, not paragraph") {
                block.parse("[source,ruby]\n----\nrequire 'sinatra'\n----\n") match
                    case Success(_: CstDelimitedBlock) => assertTrue(true)
                    case Success(_: CstParagraph)      => assertTrue("should be CstDelimitedBlock, got CstParagraph" == "")
                    case Success(other)                => assertTrue(s"unexpected: ${other.getClass.getSimpleName}" == "")
                    case Failure(msg)                  => assertTrue(s"Expected Success but got: $msg" == "")
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
                    case Success(other)                                  => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses empty-value attribute entry") {
                parse(":my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", false)) => assertTrue(true)
                    case Success(other)                                   => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import kyo.test.*

import io.eleven19.ascribe.cst.{
    CstAdmonitionParagraph,
    CstAttributeEntry,
    CstBlock,
    CstDelimitedBlock,
    CstParagraph,
    CstText
}
import io.eleven19.ascribe.parser.BlockParser.{
    admonitionParagraphBlock,
    attributeEntryBlock,
    attributedParagraph,
    attributeListLine,
    block
}

class BlockParserSpec extends Test[Any]:
    private def parse(input: String) = attributeEntryBlock.parse(input)

    private def titleText(p: CstParagraph): String =
        p.title.map(_.content.collect { case CstText(c) => c }.mkString).getOrElse("")

    "BlockParser" - {
        "admonitionParagraphBlock" - {
            "parses NOTE: paragraph" in {
                admonitionParagraphBlock.parse("NOTE: Watch out.\n") match
                    case Success(CstAdmonitionParagraph("NOTE", content)) =>
                        assert(content.nonEmpty)
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "parses TIP: paragraph" in {
                admonitionParagraphBlock.parse("TIP: Try this.\n") match
                    case Success(CstAdmonitionParagraph("TIP", _)) => assert(true)
                    case other                                     => assert(s"unexpected: $other" == "")
            }
            "parses IMPORTANT: paragraph" in {
                admonitionParagraphBlock.parse("IMPORTANT: Read this.\n") match
                    case Success(CstAdmonitionParagraph("IMPORTANT", _)) => assert(true)
                    case other                                           => assert(s"unexpected: $other" == "")
            }
            "parses CAUTION: paragraph" in {
                admonitionParagraphBlock.parse("CAUTION: Be careful.\n") match
                    case Success(CstAdmonitionParagraph("CAUTION", _)) => assert(true)
                    case other                                         => assert(s"unexpected: $other" == "")
            }
            "parses WARNING: paragraph" in {
                admonitionParagraphBlock.parse("WARNING: Danger.\n") match
                    case Success(CstAdmonitionParagraph("WARNING", _)) => assert(true)
                    case other                                         => assert(s"unexpected: $other" == "")
            }
            "NOTE without colon+space is NOT an admonition" in {
                admonitionParagraphBlock.parse("NOTE something\n") match
                    case Failure(_) => assert(true)
                    case Success(r) => assert(s"should have failed, got: $r" == "")
            }
        }
        "attributedParagraph" - {
            "parses [.lead] followed by paragraph text" in {
                attributedParagraph.parse("[.lead]\nThis is a lead paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        assert(p.attributes.isDefined)
                        assert(p.attributes.get.roles == List("lead"))
                        assert(p.lines.nonEmpty)
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "attributeListLine parses [.lead]" in {
                attributeListLine.parse("[.lead]\n") match
                    case Success(al) =>
                        assert(al.roles == List("lead"))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "block parses [.lead] + paragraph" in {
                block.parse("[.lead]\nThis is a lead paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        assert(p.attributes.isDefined)
                        assert(p.attributes.get.roles == List("lead"))
                    case Success(other) => assert(s"unexpected type: ${other.getClass.getSimpleName}" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "merges multiple attribute list lines into one" in {
                attributedParagraph.parse("[.role1]\n[.role2]\nMulti-role paragraph.\n") match
                    case Success(p: CstParagraph) =>
                        val roles = p.attributes.map(_.roles).getOrElse(Nil)
                        assert(roles == List("role1", "role2"))
                        assert(p.lines.nonEmpty)
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "parses named attributes on paragraph" in {
                attributedParagraph.parse("[key=value]\nParagraph with named attr.\n") match
                    case Success(p: CstParagraph) =>
                        assert(p.attributes.isDefined)
                        assert(p.attributes.get.named.get("key").contains("value"))
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "parses option attribute on paragraph" in {
                attributedParagraph.parse("[%hardbreaks]\nParagraph with option.\n") match
                    case Success(p: CstParagraph) =>
                        assert(p.attributes.isDefined)
                        assert(p.attributes.get.options.contains("hardbreaks"))
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "parses block title with attribute list before paragraph" in {
                attributedParagraph.parse(".My Title\n[.lead]\nParagraph text.\n") match
                    case Success(p: CstParagraph) =>
                        assert(p.title.isDefined)
                        assert(titleText(p) == "My Title")
                        assert(p.attributes.isDefined)
                        assert(p.attributes.get.roles == List("lead"))
                        assert(p.lines.nonEmpty)
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "fails on plain text with no attribute list" in {
                attributedParagraph.parse("This is a plain paragraph.\n") match
                    case Failure(_) => assert(true)
                    case Success(r) => assert(s"should have failed, got: $r" == "")
            }
            "fails on attribute list with no following paragraph lines" in {
                attributedParagraph.parse("[.lead]\n") match
                    case Failure(_) => assert(true)
                    case Success(r) => assert(s"should have failed, got: $r" == "")
            }
            "fails and backtracks when attribute list is followed by a delimited block" in {
                attributedParagraph.parse("[source,ruby]\n----\ncode\n----\n") match
                    case Failure(_) => assert(true)
                    case Success(r) => assert(s"should have failed, got: $r" == "")
            }
        }
        "block (backtracking)" - {
            "[source,ruby] + listing delimiter routes to delimited block, not paragraph" in {
                block.parse("[source,ruby]\n----\nrequire 'sinatra'\n----\n") match
                    case Success(_: CstDelimitedBlock) => assert(true)
                    case Success(_: CstParagraph)      => assert("should be CstDelimitedBlock, got CstParagraph" == "")
                    case Success(other)                => assert(s"unexpected: ${other.getClass.getSimpleName}" == "")
                    case Failure(msg)                  => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "attributeEntryBlock" - {
            "parses normal attribute entry" in {
                parse(":my-attr: some value\n") match
                    case Success(CstAttributeEntry("my-attr", "some value", false)) => assert(true)
                    case Success(other) => assert(s"unexpected: $other" == "")
                    case Failure(msg)   => assert(s"Expected Success but got: $msg" == "")
            }
            "parses unset attribute entry :!name:" in {
                parse(":!my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", true)) => assert(true)
                    case Success(other)                                  => assert(s"unexpected: $other" == "")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses empty-value attribute entry" in {
                parse(":my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", false)) => assert(true)
                    case Success(other)                                   => assert(s"unexpected: $other" == "")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
    }

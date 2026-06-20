package io.eleven19.ascribe.cst

import parsley.{Failure, Success}
import kyo.test.*

import io.eleven19.ascribe.Ascribe

class CstParserSpec extends Test[Any]:

    private def parseCst(input: String) = Ascribe.parseCst(input)

    "CstParser" - {
        "blank lines preserved" - {
            "blank line between paragraphs is a CstBlankLine node" in {
                parseCst("Para one.\n\nPara two.\n") match
                    case Success(doc) =>
                        val hasBlankLine = doc.content.exists(_.isInstanceOf[CstBlankLine])
                        assert(hasBlankLine)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "content order: para, blank, para" in {
                parseCst("A.\n\nB.\n") match
                    case Success(doc) =>
                        assert(doc.content.length == 3)
                        assert(doc.content(0).isInstanceOf[CstParagraph])
                        assert(doc.content(1).isInstanceOf[CstBlankLine])
                        assert(doc.content(2).isInstanceOf[CstParagraph])
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "line comments" - {
            "single-line comment becomes CstLineComment node" in {
                parseCst("// This is a comment\nPara.\n") match
                    case Success(doc) =>
                        val comment = doc.content.collectFirst { case c: CstLineComment => c }
                        assert(comment.isDefined)
                        assert(comment.get.content.contains("This is a comment"))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "include directives" - {
            "include directive becomes CstInclude node (not resolved at parse time)" in {
                parseCst("include::partial.adoc[]\n") match
                    case Success(doc) =>
                        val inc = doc.content.collectFirst { case i: CstInclude => i }
                        assert(inc.isDefined)
                        assert(inc.get.target == "partial.adoc")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "include with opts=optional attribute" in {
                parseCst("include::file.adoc[opts=optional]\n") match
                    case Success(doc) =>
                        val inc = doc.content.collectFirst { case i: CstInclude => i }
                        assert(inc.isDefined)
                        assert(inc.get.target == "file.adoc")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "paragraph lines" - {
            "multi-line paragraph preserves individual CstParagraphLine nodes" in {
                parseCst("Line one.\nLine two.\nLine three.\n") match
                    case Success(doc) =>
                        val para = doc.content.collectFirst { case p: CstParagraph => p }
                        assert(para.isDefined)
                        assert(para.get.lines.length == 3)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "attribute entries" - {
            "body attribute entry becomes CstAttributeEntry node" in {
                parseCst(":my-attr: some value\nPara.\n") match
                    case Success(doc) =>
                        val entry = doc.content.collectFirst { case e: CstAttributeEntry => e }
                        assert(entry.isDefined)
                        assert(entry.get.name == "my-attr")
                        assert(entry.get.value == "some value")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "document header" - {
            "document header captures title as CstHeading" in {
                parseCst("= My Document\n\nParagraph.\n") match
                    case Success(doc) =>
                        assert(doc.header.isDefined)
                        assert(doc.header.get.title.level == 1)
                        assert(doc.header.get.title.marker == "=")
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "document header captures attribute entries" in {
                parseCst("= Title\n:author: Jane\n:version: 1.0\n\nParagraph.\n") match
                    case Success(doc) =>
                        assert(doc.header.isDefined)
                        assert(doc.header.get.attributes.length == 2)
                        assert(doc.header.get.attributes.exists(_.name == "author"))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "headings" - {
            "heading preserves marker string" in {
                parseCst("== Section Title\n") match
                    case Success(doc) =>
                        val h = doc.content.collectFirst { case h: CstHeading => h }
                        assert(h.isDefined)
                        assert(h.get.marker == "==")
                        assert(h.get.level == 2)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
    }

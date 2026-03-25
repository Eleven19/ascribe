package io.eleven19.ascribe.cst

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.Ascribe

object CstParserSpec extends ZIOSpecDefault:

    private def parseCst(input: String) = Ascribe.parseCst(input)

    def spec = suite("CstParser")(
        suite("blank lines preserved")(
            test("blank line between paragraphs is a CstBlankLine node") {
                parseCst("Para one.\n\nPara two.\n") match
                    case Success(doc) =>
                        val hasBlankLine = doc.content.exists(_.isInstanceOf[CstBlankLine])
                        assertTrue(hasBlankLine)
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("content order: para, blank, para") {
                parseCst("A.\n\nB.\n") match
                    case Success(doc) =>
                        assertTrue(doc.content.length == 3) &&
                        assertTrue(doc.content(0).isInstanceOf[CstParagraph]) &&
                        assertTrue(doc.content(1).isInstanceOf[CstBlankLine]) &&
                        assertTrue(doc.content(2).isInstanceOf[CstParagraph])
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("line comments")(
            test("single-line comment becomes CstLineComment node") {
                parseCst("// This is a comment\nPara.\n") match
                    case Success(doc) =>
                        val comment = doc.content.collectFirst { case c: CstLineComment => c }
                        assertTrue(comment.isDefined) &&
                        assertTrue(comment.get.content.contains("This is a comment"))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("include directives")(
            test("include directive becomes CstInclude node (not resolved at parse time)") {
                parseCst("include::partial.adoc[]\n") match
                    case Success(doc) =>
                        val inc = doc.content.collectFirst { case i: CstInclude => i }
                        assertTrue(inc.isDefined) &&
                        assertTrue(inc.get.target == "partial.adoc")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("include with opts=optional attribute") {
                parseCst("include::file.adoc[opts=optional]\n") match
                    case Success(doc) =>
                        val inc = doc.content.collectFirst { case i: CstInclude => i }
                        assertTrue(inc.isDefined) &&
                        assertTrue(inc.get.target == "file.adoc")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("paragraph lines")(
            test("multi-line paragraph preserves individual CstParagraphLine nodes") {
                parseCst("Line one.\nLine two.\nLine three.\n") match
                    case Success(doc) =>
                        val para = doc.content.collectFirst { case p: CstParagraph => p }
                        assertTrue(para.isDefined) &&
                        assertTrue(para.get.lines.length == 3)
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("attribute entries")(
            test("body attribute entry becomes CstAttributeEntry node") {
                parseCst(":my-attr: some value\nPara.\n") match
                    case Success(doc) =>
                        val entry = doc.content.collectFirst { case e: CstAttributeEntry => e }
                        assertTrue(entry.isDefined) &&
                        assertTrue(entry.get.name == "my-attr") &&
                        assertTrue(entry.get.value == "some value")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("document header")(
            test("document header captures title as CstHeading") {
                parseCst("= My Document\n\nParagraph.\n") match
                    case Success(doc) =>
                        assertTrue(doc.header.isDefined) &&
                        assertTrue(doc.header.get.title.level == 1) &&
                        assertTrue(doc.header.get.title.marker == "=")
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("document header captures attribute entries") {
                parseCst("= Title\n:author: Jane\n:version: 1.0\n\nParagraph.\n") match
                    case Success(doc) =>
                        assertTrue(doc.header.isDefined) &&
                        assertTrue(doc.header.get.attributes.length == 2) &&
                        assertTrue(doc.header.get.attributes.exists(_.name == "author"))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("headings")(
            test("heading preserves marker string") {
                parseCst("== Section Title\n") match
                    case Success(doc) =>
                        val h = doc.content.collectFirst { case h: CstHeading => h }
                        assertTrue(h.isDefined) &&
                        assertTrue(h.get.marker == "==") &&
                        assertTrue(h.get.level == 2)
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

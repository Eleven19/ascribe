package io.eleven19.ascribe.cst

import parsley.{Failure, Success}
import zio.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.CstMacroAttrList

object CstRendererSpec extends ZIOSpecDefault:

    /** Parse, render, re-parse and compare the re-parsed CST structure. */
    private def roundtrip(source: String): zio.test.TestResult =
        Ascribe.parseCst(source) match
            case Success(cst) =>
                val rendered = CstRenderer.render(cst)
                Ascribe.parseCst(rendered) match
                    case Success(cst2) =>
                        assertTrue(cst.content.length == cst2.content.length)
                    case Failure(msg) =>
                        assertTrue(s"Re-parse failed: $msg, rendered was: $rendered" == "")
            case Failure(msg) => assertTrue(s"Initial parse failed: $msg" == "")

    def spec = suite("CstRenderer")(
        test("empty document roundtrips") {
            roundtrip("")
        },
        test("paragraph roundtrips") {
            roundtrip("Hello world.\n")
        },
        test("heading roundtrips") {
            roundtrip("== Section Title\n")
        },
        test("blank lines preserved in roundtrip") {
            roundtrip("Para one.\n\nPara two.\n")
        },
        test("line comment roundtrips") {
            roundtrip("// This is a comment\nPara.\n")
        },
        test("include directive roundtrips") {
            roundtrip("include::file.adoc[]\n")
        },
        test("attribute entry roundtrips") {
            roundtrip(":my-attr: value\nPara.\n")
        },
        test("render produces correct text for heading") {
            Ascribe.parseCst("== My Section\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("== My Section"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces correct text for line comment") {
            Ascribe.parseCst("// my comment\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("// my comment"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces correct text for include") {
            Ascribe.parseCst("include::file.adoc[]\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("include::file.adoc"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("render produces :!name: for unset entry") {
            Ascribe.parseCst(":!my-attr:\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains(":!my-attr:"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("attribute ref roundtrips") {
            roundtrip("{version} text\n")
        },
        test("render produces correct text for attribute ref") {
            Ascribe.parseCst("Release {version}.\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.contains("{version}"))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        test("admonition paragraph roundtrips") {
            roundtrip("NOTE: Watch out.\n")
        },
        test("render produces NOTE: prefix") {
            Ascribe.parseCst("NOTE: Watch out.\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assertTrue(rendered.startsWith("NOTE: "))
                case Failure(msg) => assertTrue(s"Parse failed: $msg" == "")
        },
        suite("link node rendering")(
            test("renders CstAutolink as bare URL") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstAutolink("https://example.com")(u)
                assertTrue(CstRenderer.renderInline(node) == "https://example.com")
            },
            test("renders CstUrlMacro as url[text]") {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node =
                    CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(List(CstText("click")(u)))(u))(u)
                assertTrue(CstRenderer.renderInline(node) == "https://example.com[click]")
            },
            test("renders CstUrlMacro with empty text as url[]") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(Nil)(u))(u)
                assertTrue(CstRenderer.renderInline(node) == "https://example.com[]")
            },
            test("renders CstLinkMacro as link:target[text]") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro("report.pdf", CstMacroAttrList.textOnly(List(CstText("Get Report")(u)))(u))(u)
                assertTrue(CstRenderer.renderInline(node) == "link:report.pdf[Get Report]")
            },
            test("renders CstMailtoMacro as mailto:addr[text]") {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node =
                    CstMailtoMacro("user@example.com", CstMacroAttrList.textOnly(List(CstText("Email me")(u)))(u))(u)
                assertTrue(CstRenderer.renderInline(node) == "mailto:user@example.com[Email me]")
            },
            test("renders CstLinkMacro with named attributes") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("click")(u)), Nil, List(("window", "_blank")), false)(u)
                )(u)
                assertTrue(CstRenderer.renderInline(node) == "link:target.html[click,window=_blank]")
            },
            test("renders CstLinkMacro with caret shorthand") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("click")(u)), Nil, Nil, hasCaretShorthand = true)(u)
                )(u)
                assertTrue(CstRenderer.renderInline(node) == "link:target.html[click^]")
            },
            test("renders CstLinkMacro with positional and named attributes") {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("text")(u)), List("pos1"), List(("role", "btn")), false)(u)
                )(u)
                assertTrue(CstRenderer.renderInline(node) == "link:target.html[text,pos1,role=btn]")
            },
            test("bare autolink roundtrips") {
                roundtrip("Visit https://example.com today.\n")
            },
            test("link macro roundtrips") {
                roundtrip("See link:report.pdf[Get Report] here.\n")
            },
            test("mailto macro roundtrips") {
                roundtrip("Contact mailto:user@example.com[Email me] now.\n")
            },
            test("URL macro roundtrips") {
                roundtrip("Go to https://example.com[the site].\n")
            }
        )
    )

package io.eleven19.ascribe.cst

import parsley.{Failure, Success}
import kyo.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.cst.CstMacroAttrList

class CstRendererSpec extends Test[Any]:

    /** Parse, render, re-parse and compare the re-parsed CST structure. */
    private def roundtrip(source: String)(using AssertScope): Unit =
        Ascribe.parseCst(source) match
            case Success(cst) =>
                val rendered = CstRenderer.render(cst)
                Ascribe.parseCst(rendered) match
                    case Success(cst2) =>
                        assert(cst.content.length == cst2.content.length)
                    case Failure(msg) =>
                        assert(s"Re-parse failed: $msg, rendered was: $rendered" == "")
            case Failure(msg) => assert(s"Initial parse failed: $msg" == "")

    "CstRenderer" - {
        "empty document roundtrips" in
            roundtrip("")
        "paragraph roundtrips" in
            roundtrip("Hello world.\n")
        "heading roundtrips" in
            roundtrip("== Section Title\n")
        "blank lines preserved in roundtrip" in
            roundtrip("Para one.\n\nPara two.\n")
        "line comment roundtrips" in
            roundtrip("// This is a comment\nPara.\n")
        "include directive roundtrips" in
            roundtrip("include::file.adoc[]\n")
        "attribute entry roundtrips" in
            roundtrip(":my-attr: value\nPara.\n")
        "render produces correct text for heading" in {
            Ascribe.parseCst("== My Section\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.contains("== My Section"))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "render produces correct text for line comment" in {
            Ascribe.parseCst("// my comment\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.contains("// my comment"))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "render produces correct text for include" in {
            Ascribe.parseCst("include::file.adoc[]\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.contains("include::file.adoc"))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "render produces :!name: for unset entry" in {
            Ascribe.parseCst(":!my-attr:\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.contains(":!my-attr:"))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "attribute ref roundtrips" in
            roundtrip("{version} text\n")
        "render produces correct text for attribute ref" in {
            Ascribe.parseCst("Release {version}.\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.contains("{version}"))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "admonition paragraph roundtrips" in
            roundtrip("NOTE: Watch out.\n")
        "render produces NOTE: prefix" in {
            Ascribe.parseCst("NOTE: Watch out.\n") match
                case Success(cst) =>
                    val rendered = CstRenderer.render(cst)
                    assert(rendered.startsWith("NOTE: "))
                case Failure(msg) => assert(s"Parse failed: $msg" == "")
        }
        "link node rendering" - {
            "renders CstAutolink as bare URL" in {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstAutolink("https://example.com")(u)
                assert(CstRenderer.renderInline(node) == "https://example.com")
            }
            "renders CstUrlMacro as url[text]" in {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node =
                    CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(List(CstText("click")(u)))(u))(u)
                assert(CstRenderer.renderInline(node) == "https://example.com[click]")
            }
            "renders CstUrlMacro with empty text as url[]" in {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(Nil)(u))(u)
                assert(CstRenderer.renderInline(node) == "https://example.com[]")
            }
            "renders CstLinkMacro as link:target[text]" in {
                val u    = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro("report.pdf", CstMacroAttrList.textOnly(List(CstText("Get Report")(u)))(u))(u)
                assert(CstRenderer.renderInline(node) == "link:report.pdf[Get Report]")
            }
            "renders CstMailtoMacro as mailto:addr[text]" in {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node =
                    CstMailtoMacro("user@example.com", CstMacroAttrList.textOnly(List(CstText("Email me")(u)))(u))(u)
                assert(CstRenderer.renderInline(node) == "mailto:user@example.com[Email me]")
            }
            "renders CstLinkMacro with named attributes" in {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("click")(u)), Nil, List(("window", "_blank")), false)(u)
                )(u)
                assert(CstRenderer.renderInline(node) == "link:target.html[click,window=_blank]")
            }
            "renders CstLinkMacro with caret shorthand" in {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("click")(u)), Nil, Nil, hasCaretShorthand = true)(u)
                )(u)
                assert(CstRenderer.renderInline(node) == "link:target.html[click^]")
            }
            "renders CstLinkMacro with positional and named attributes" in {
                val u = io.eleven19.ascribe.ast.Span.unknown
                val node = CstLinkMacro(
                    "target.html",
                    CstMacroAttrList(List(CstText("text")(u)), List("pos1"), List(("role", "btn")), false)(u)
                )(u)
                assert(CstRenderer.renderInline(node) == "link:target.html[text,pos1,role=btn]")
            }
            "bare autolink roundtrips" in
                roundtrip("Visit https://example.com today.\n")
            "link macro roundtrips" in
                roundtrip("See link:report.pdf[Get Report] here.\n")
            "mailto macro roundtrips" in
                roundtrip("Contact mailto:user@example.com[Email me] now.\n")
            "URL macro roundtrips" in
                roundtrip("Go to https://example.com[the site].\n")
        }
    }

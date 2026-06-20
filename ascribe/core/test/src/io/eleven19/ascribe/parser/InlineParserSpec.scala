package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import kyo.test.*

import io.eleven19.ascribe.ast.Span
import io.eleven19.ascribe.cst.{
    CstAttributeRef,
    CstAutolink,
    CstBold,
    CstItalic,
    CstLinkMacro,
    CstMacroAttrList,
    CstMailtoMacro,
    CstMono,
    CstText,
    CstUrlMacro
}
import io.eleven19.ascribe.parser.InlineParser.lineContent

class InlineParserSpec extends Test[Any]:
    private val u                    = Span.unknown
    private def parse(input: String) = lineContent.parse(input)

    "InlineParser" - {
        "plain text" - {
            "parses a simple word" in {
                parse("hello") match
                    case Success(inlines) => assert(inlines == List(CstText("hello")(u)))
                    case Failure(msg)     => assert(s"Expected Success but got: $msg" == "")
            }
            "parses a sentence with spaces" in {
                parse("hello world") match
                    case Success(inlines) => assert(inlines == List(CstText("hello world")(u)))
                    case Failure(msg)     => assert(s"Expected Success but got: $msg" == "")
            }
            "stops at newline" in {
                parse("line one\nline two") match
                    case Success(inlines) => assert(inlines == List(CstText("line one")(u)))
                    case Failure(msg)     => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "bold spans" - {
            "parses **bold** text" in {
                parse("**bold**") match
                    case Success(inlines) =>
                        assert(inlines == List(CstBold(List(CstText("bold")(u)), false)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses bold embedded in text" in {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("hello ")(u),
                                CstBold(List(CstText("world")(u)), false)(u),
                                CstText(" end")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "treats lone * as plain text" in {
                parse("a*b") match
                    case Success(inlines) =>
                        assert(inlines == List(CstText("a")(u), CstText("*")(u), CstText("b")(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "italic spans" - {
            "parses __italic__ text" in {
                parse("__italic__") match
                    case Success(inlines) =>
                        assert(inlines == List(CstItalic(List(CstText("italic")(u)), false)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "monospace spans" - {
            "parses ``mono`` text" in {
                parse("``mono``") match
                    case Success(inlines) =>
                        assert(inlines == List(CstMono(List(CstText("mono")(u)), false)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "mixed inline" - {
            "parses bold and italic together" in {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstBold(List(CstText("b")(u)), false)(u),
                                CstText(" and ")(u),
                                CstItalic(List(CstText("i")(u)), false)(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "attribute refs" - {
            "parses {name} as CstAttributeRef" in {
                parse("{version}") match
                    case Success(inlines) =>
                        assert(inlines == List(CstAttributeRef("version")(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses attribute ref embedded in text" in {
                parse("Release {version} now") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("Release ")(u),
                                CstAttributeRef("version")(u),
                                CstText(" now")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "attribute ref name must start with letter" in {
                parse("{1foo}") match
                    case Success(inlines) =>
                        // {1foo} is not a valid attr ref name — parses as plain { text
                        assert(!inlines.exists { case CstAttributeRef(_) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "lone { is plain text fallback" in {
                parse("a{b") match
                    case Success(inlines) =>
                        val texts = inlines.collect { case CstText(c) => c }
                        assert(texts.contains("{"))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "link macros" - {
            "parses link:target[text]" in {
                parse("link:report.pdf[Get Report]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstLinkMacro(
                                    "report.pdf",
                                    CstMacroAttrList.textOnly(List(CstText("Get Report")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses link:target[] with empty text" in {
                parse("link:report.pdf[]") match
                    case Success(inlines) =>
                        assert(inlines == List(CstLinkMacro("report.pdf", CstMacroAttrList.textOnly(Nil)(u))(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses link macro embedded in text" in {
                parse("See link:report.pdf[the report] for details") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("See ")(u),
                                CstLinkMacro(
                                    "report.pdf",
                                    CstMacroAttrList.textOnly(List(CstText("the report")(u)))(u)
                                )(u),
                                CstText(" for details")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "mailto macros" - {
            "parses mailto:addr[text]" in {
                parse("mailto:user@example.com[Email me]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstMailtoMacro(
                                    "user@example.com",
                                    CstMacroAttrList.textOnly(List(CstText("Email me")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses mailto:addr[] with empty text" in {
                parse("mailto:user@example.com[]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(CstMailtoMacro("user@example.com", CstMacroAttrList.textOnly(Nil)(u))(u))
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "URL macros" - {
            "parses https://url[text]" in {
                parse("https://example.com[click here]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstUrlMacro(
                                    "https://example.com",
                                    CstMacroAttrList.textOnly(List(CstText("click here")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses http://url[text]" in {
                parse("http://example.com[visit]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstUrlMacro(
                                    "http://example.com",
                                    CstMacroAttrList.textOnly(List(CstText("visit")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses URL macro with empty text" in {
                parse("https://example.com[]") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(Nil)(u))(u))
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "bare autolinks" - {
            "parses bare https:// URL" in {
                parse("https://example.com") match
                    case Success(inlines) =>
                        assert(inlines == List(CstAutolink("https://example.com")(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses bare URL embedded in text" in {
                parse("Visit https://example.com today") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("Visit ")(u),
                                CstAutolink("https://example.com")(u),
                                CstText(" today")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses ftp:// URL" in {
                parse("ftp://files.example.com/pub") match
                    case Success(inlines) =>
                        assert(inlines == List(CstAutolink("ftp://files.example.com/pub")(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses irc:// URL" in {
                parse("irc://irc.freenode.org/#channel") match
                    case Success(inlines) =>
                        assert(inlines == List(CstAutolink("irc://irc.freenode.org/#channel")(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "constrained italic" - {
            "parses _italic_ at start of line" in {
                parse("_italic_") match
                    case Success(inlines) =>
                        assert(inlines == List(CstItalic(List(CstText("italic")(u)), true)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses _italic_ embedded in text" in {
                parse("hello _world_ end") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("hello ")(u),
                                CstItalic(List(CstText("world")(u)), true)(u),
                                CstText(" end")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "does not parse _italic_ mid-word" in {
                parse("foo_bar_baz") match
                    case Success(inlines) =>
                        assert(!inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses _italic_ after punctuation" in {
                parse("(_italic_)") match
                    case Success(inlines) =>
                        assert(inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "constrained monospace" - {
            "parses `code` at start of line" in {
                parse("`code`") match
                    case Success(inlines) =>
                        assert(inlines == List(CstMono(List(CstText("code")(u)), true)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses `code` embedded in text" in {
                parse("use `cmd` now") match
                    case Success(inlines) =>
                        assert(
                            inlines == List(
                                CstText("use ")(u),
                                CstMono(List(CstText("cmd")(u)), true)(u),
                                CstText(" now")(u)
                            )
                        )
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "does not parse `code` mid-word" in {
                parse("foo`bar`baz") match
                    case Success(inlines) =>
                        assert(!inlines.exists { case _: CstMono => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "constrained bold boundary enforcement" - {
            "*bold* at start of line still works" in {
                parse("*bold*") match
                    case Success(inlines) =>
                        assert(inlines == List(CstBold(List(CstText("bold")(u)), true)(u)))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "does not parse *bold* mid-word" in {
                parse("foo*bar*baz") match
                    case Success(inlines) =>
                        assert(!inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "macro attribute parsing" - {
            "plain text without commas/equals is text-only" in {
                parse("link:path[simple text]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.target == "path")
                        assert(link.attrList.text == List(CstText("simple text")(u)))
                        assert(link.attrList.positional == Nil)
                        assert(link.attrList.named == Nil)
                        assert(link.attrList.hasCaretShorthand == false)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "text with named attribute" in {
                parse("link:path[click here,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == List(CstText("click here")(u)))
                        assert(link.attrList.named == List(("window", "_blank")))
                        assert(link.attrList.positional == Nil)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "caret shorthand" in {
                parse("link:path[click here^]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == List(CstText("click here")(u)))
                        assert(link.attrList.hasCaretShorthand == true)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "quoted text with comma" in {
                parse("""link:path["text, with comma",role=btn]""") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == List(CstText("text, with comma")(u)))
                        assert(link.attrList.named == List(("role", "btn")))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "first positional with equals is kept as text" in {
                parse("link:path[role=btn,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == List(CstText("role=btn")(u)))
                        assert(link.attrList.named == List(("window", "_blank")))
                        assert(link.attrList.positional == Nil)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "empty brackets" in {
                parse("link:path[]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == Nil)
                        assert(link.attrList.positional == Nil)
                        assert(link.attrList.named == Nil)
                        assert(link.attrList.hasCaretShorthand == false)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "mailto with positional params" in {
                parse("mailto:user@host[Subscribe,Subscribe me,I want to join]") match
                    case Success(inlines) =>
                        val mailto = inlines.collectFirst { case m: CstMailtoMacro => m }.get
                        assert(mailto.attrList.text == List(CstText("Subscribe")(u)))
                        assert(mailto.attrList.positional == List("Subscribe me", "I want to join"))
                        assert(mailto.attrList.named == Nil)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "URL macro with attributes" in {
                parse("https://example.com[Example,window=_blank,opts=nofollow]") match
                    case Success(inlines) =>
                        val urlNode = inlines.collectFirst { case um: CstUrlMacro => um }.get
                        assert(urlNode.attrList.text == List(CstText("Example")(u)))
                        assert(urlNode.attrList.named == List(("window", "_blank"), ("opts", "nofollow")))
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "quoted text without attribute signals should be unquoted" in {
                parse("""link:path["just quoted"]""") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assert(link.attrList.text == List(CstText("just quoted")(u)))
                        assert(link.attrList.positional == Nil)
                        assert(link.attrList.named == Nil)
                        assert(link.attrList.hasCaretShorthand == false)
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
        "constrained close before punctuation" - {
            "parses *bold* before colon" in {
                parse("*bold*: rest") match
                    case Success(inlines) =>
                        assert(inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses *bold* before hyphen" in {
                parse("*bold*- rest") match
                    case Success(inlines) =>
                        assert(inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses _italic_ before colon" in {
                parse("_italic_: rest") match
                    case Success(inlines) =>
                        assert(inlines.exists { case CstItalic(_, true) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
            "parses `mono` before slash" in {
                parse("`mono`/ rest") match
                    case Success(inlines) =>
                        assert(inlines.exists { case CstMono(_, true) => true; case _ => false })
                    case Failure(msg) => assert(s"Expected Success but got: $msg" == "")
            }
        }
    }

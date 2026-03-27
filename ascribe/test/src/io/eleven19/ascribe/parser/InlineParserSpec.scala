package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

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

object InlineParserSpec extends ZIOSpecDefault:
    private val u                    = Span.unknown
    private def parse(input: String) = lineContent.parse(input)

    def spec = suite("InlineParser")(
        suite("plain text")(
            test("parses a simple word") {
                parse("hello") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("hello")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses a sentence with spaces") {
                parse("hello world") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("hello world")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("stops at newline") {
                parse("line one\nline two") match
                    case Success(inlines) => assertTrue(inlines == List(CstText("line one")(u)))
                    case Failure(msg)     => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bold spans")(
            test("parses **bold** text") {
                parse("**bold**") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstBold(List(CstText("bold")(u)), false)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bold embedded in text") {
                parse("hello **world** end") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("hello ")(u),
                                CstBold(List(CstText("world")(u)), false)(u),
                                CstText(" end")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("treats lone * as plain text") {
                parse("a*b") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstText("a")(u), CstText("*")(u), CstText("b")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("italic spans")(
            test("parses __italic__ text") {
                parse("__italic__") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstItalic(List(CstText("italic")(u)), false)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("monospace spans")(
            test("parses ``mono`` text") {
                parse("``mono``") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMono(List(CstText("mono")(u)), false)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mixed inline")(
            test("parses bold and italic together") {
                parse("**b** and __i__") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstBold(List(CstText("b")(u)), false)(u),
                                CstText(" and ")(u),
                                CstItalic(List(CstText("i")(u)), false)(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("attribute refs")(
            test("parses {name} as CstAttributeRef") {
                parse("{version}") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAttributeRef("version")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses attribute ref embedded in text") {
                parse("Release {version} now") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("Release ")(u),
                                CstAttributeRef("version")(u),
                                CstText(" now")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("attribute ref name must start with letter") {
                parse("{1foo}") match
                    case Success(inlines) =>
                        // {1foo} is not a valid attr ref name — parses as plain { text
                        assertTrue(!inlines.exists { case CstAttributeRef(_) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("lone { is plain text fallback") {
                parse("a{b") match
                    case Success(inlines) =>
                        val texts = inlines.collect { case CstText(c) => c }
                        assertTrue(texts.contains("{"))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("link macros")(
            test("parses link:target[text]") {
                parse("link:report.pdf[Get Report]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstLinkMacro(
                                    "report.pdf",
                                    CstMacroAttrList.textOnly(List(CstText("Get Report")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses link:target[] with empty text") {
                parse("link:report.pdf[]") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstLinkMacro("report.pdf", CstMacroAttrList.textOnly(Nil)(u))(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses link macro embedded in text") {
                parse("See link:report.pdf[the report] for details") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("See ")(u),
                                CstLinkMacro(
                                    "report.pdf",
                                    CstMacroAttrList.textOnly(List(CstText("the report")(u)))(u)
                                )(u),
                                CstText(" for details")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("mailto macros")(
            test("parses mailto:addr[text]") {
                parse("mailto:user@example.com[Email me]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstMailtoMacro(
                                    "user@example.com",
                                    CstMacroAttrList.textOnly(List(CstText("Email me")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses mailto:addr[] with empty text") {
                parse("mailto:user@example.com[]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(CstMailtoMacro("user@example.com", CstMacroAttrList.textOnly(Nil)(u))(u))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("URL macros")(
            test("parses https://url[text]") {
                parse("https://example.com[click here]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstUrlMacro(
                                    "https://example.com",
                                    CstMacroAttrList.textOnly(List(CstText("click here")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses http://url[text]") {
                parse("http://example.com[visit]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstUrlMacro(
                                    "http://example.com",
                                    CstMacroAttrList.textOnly(List(CstText("visit")(u)))(u)
                                )(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses URL macro with empty text") {
                parse("https://example.com[]") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(Nil)(u))(u))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("bare autolinks")(
            test("parses bare https:// URL") {
                parse("https://example.com") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("https://example.com")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses bare URL embedded in text") {
                parse("Visit https://example.com today") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("Visit ")(u),
                                CstAutolink("https://example.com")(u),
                                CstText(" today")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses ftp:// URL") {
                parse("ftp://files.example.com/pub") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("ftp://files.example.com/pub")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses irc:// URL") {
                parse("irc://irc.freenode.org/#channel") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstAutolink("irc://irc.freenode.org/#channel")(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained italic")(
            test("parses _italic_ at start of line") {
                parse("_italic_") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstItalic(List(CstText("italic")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses _italic_ embedded in text") {
                parse("hello _world_ end") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("hello ")(u),
                                CstItalic(List(CstText("world")(u)), true)(u),
                                CstText(" end")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse _italic_ mid-word") {
                parse("foo_bar_baz") match
                    case Success(inlines) =>
                        assertTrue(!inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses _italic_ after punctuation") {
                parse("(_italic_)") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case _: CstItalic => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained monospace")(
            test("parses `code` at start of line") {
                parse("`code`") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstMono(List(CstText("code")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses `code` embedded in text") {
                parse("use `cmd` now") match
                    case Success(inlines) =>
                        assertTrue(
                            inlines == List(
                                CstText("use ")(u),
                                CstMono(List(CstText("cmd")(u)), true)(u),
                                CstText(" now")(u)
                            )
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse `code` mid-word") {
                parse("foo`bar`baz") match
                    case Success(inlines) =>
                        assertTrue(!inlines.exists { case _: CstMono => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained bold boundary enforcement")(
            test("*bold* at start of line still works") {
                parse("*bold*") match
                    case Success(inlines) =>
                        assertTrue(inlines == List(CstBold(List(CstText("bold")(u)), true)(u)))
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("does not parse *bold* mid-word") {
                parse("foo*bar*baz") match
                    case Success(inlines) =>
                        assertTrue(!inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("macro attribute parsing")(
            test("plain text without commas/equals is text-only") {
                parse("link:path[simple text]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.target == "path",
                            link.attrList.text == List(CstText("simple text")(u)),
                            link.attrList.positional == Nil,
                            link.attrList.named == Nil,
                            link.attrList.hasCaretShorthand == false
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("text with named attribute") {
                parse("link:path[click here,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("click here")(u)),
                            link.attrList.named == List(("window", "_blank")),
                            link.attrList.positional == Nil
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("caret shorthand") {
                parse("link:path[click here^]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("click here")(u)),
                            link.attrList.hasCaretShorthand == true
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("quoted text with comma") {
                parse("""link:path["text, with comma",role=btn]""") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("text, with comma")(u)),
                            link.attrList.named == List(("role", "btn"))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("first positional with equals is kept as text") {
                parse("link:path[role=btn,window=_blank]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("role=btn")(u)),
                            link.attrList.named == List(("window", "_blank")),
                            link.attrList.positional == Nil
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("empty brackets") {
                parse("link:path[]") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == Nil,
                            link.attrList.positional == Nil,
                            link.attrList.named == Nil,
                            link.attrList.hasCaretShorthand == false
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("mailto with positional params") {
                parse("mailto:user@host[Subscribe,Subscribe me,I want to join]") match
                    case Success(inlines) =>
                        val mailto = inlines.collectFirst { case m: CstMailtoMacro => m }.get
                        assertTrue(
                            mailto.attrList.text == List(CstText("Subscribe")(u)),
                            mailto.attrList.positional == List("Subscribe me", "I want to join"),
                            mailto.attrList.named == Nil
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("URL macro with attributes") {
                parse("https://example.com[Example,window=_blank,opts=nofollow]") match
                    case Success(inlines) =>
                        val urlNode = inlines.collectFirst { case um: CstUrlMacro => um }.get
                        assertTrue(
                            urlNode.attrList.text == List(CstText("Example")(u)),
                            urlNode.attrList.named == List(("window", "_blank"), ("opts", "nofollow"))
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("quoted text without commas/equals is unquoted") {
                parse("""link:path["just quoted"]""") match
                    case Success(inlines) =>
                        val link = inlines.collectFirst { case l: CstLinkMacro => l }.get
                        assertTrue(
                            link.attrList.text == List(CstText("just quoted")(u)),
                            link.attrList.positional == Nil,
                            link.attrList.named == Nil,
                            link.attrList.hasCaretShorthand == false
                        )
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        ),
        suite("constrained close before punctuation")(
            test("parses *bold* before colon") {
                parse("*bold*: rest") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses *bold* before hyphen") {
                parse("*bold*- rest") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case CstBold(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses _italic_ before colon") {
                parse("_italic_: rest") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case CstItalic(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses `mono` before slash") {
                parse("`mono`/ rest") match
                    case Success(inlines) =>
                        assertTrue(inlines.exists { case CstMono(_, true) => true; case _ => false })
                    case Failure(msg) => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

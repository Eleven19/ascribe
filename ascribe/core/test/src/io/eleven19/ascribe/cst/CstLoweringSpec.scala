package io.eleven19.ascribe.cst

import kyo.test.*
import io.eleven19.ascribe.ast.{
    Admonition,
    AdmonitionKind,
    CssRole,
    ElementId,
    Heading,
    Link,
    LinkAttributes,
    LinkOption,
    LinkVariant,
    MacroKind,
    Paragraph,
    Section,
    Span,
    WindowTarget
}
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.cst.{
    CstAdmonitionParagraph,
    CstAttributeEntry,
    CstAttributeRef,
    CstAutolink,
    CstDocumentHeader,
    CstDocument,
    CstLinkMacro,
    CstMacroAttrList,
    CstMailtoMacro,
    CstParagraph,
    CstParagraphLine,
    CstHeading,
    CstText,
    CstUrlMacro
}

class CstLoweringSpec extends Test[Any]:
    private val u = Span.unknown

    "CstLowering" - {
        "empty document" in {
            val cst = CstDocument(None, Nil)(u)
            assert(CstLowering.toAst(cst) == document())
        }
        "document with paragraph" in {
            val cst = CstDocument(
                None,
                List(
                    CstParagraph(
                        List(
                            CstParagraphLine(List(CstText("Hello world.")(u)))(u)
                        )
                    )(u)
                )
            )(u)
            assert(CstLowering.toAst(cst) == document(paragraph(text("Hello world."))))
        }
        "multi-line paragraph merges lines" in {
            val cst = CstDocument(
                None,
                List(
                    CstParagraph(
                        List(
                            CstParagraphLine(List(CstText("Line one.")(u)))(u),
                            CstParagraphLine(List(CstText("Line two.")(u)))(u)
                        )
                    )(u)
                )
            )(u)
            // Lines are flattened (no newline separator — matches current AST behaviour)
            assert(
                CstLowering.toAst(cst) ==
                    document(paragraph(text("Line one."), text("Line two.")))
            )
        }
        "blank lines are dropped" in {
            val cst = CstDocument(
                None,
                List(
                    CstBlankLine()(u),
                    CstParagraph(
                        List(
                            CstParagraphLine(List(CstText("Hello.")(u)))(u)
                        )
                    )(u),
                    CstBlankLine()(u)
                )
            )(u)
            assert(CstLowering.toAst(cst) == document(paragraph(text("Hello."))))
        }
        "line comments are dropped" in {
            val cst = CstDocument(
                None,
                List(
                    CstLineComment("a comment")(u),
                    CstParagraph(List(CstParagraphLine(List(CstText("Para.")(u)))(u)))(u)
                )
            )(u)
            assert(CstLowering.toAst(cst) == document(paragraph(text("Para."))))
        }
        "inline lowering" - {
            "unconstrained bold" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(
                                        CstBold(List(CstText("bold")(u)), constrained = false)(u)
                                    )
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                assert(
                    CstLowering.toAst(cst) ==
                        document(paragraph(bold(text("bold"))))
                )
            }
            "constrained bold" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(
                                        CstBold(List(CstText("bold")(u)), constrained = true)(u)
                                    )
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                assert(
                    CstLowering.toAst(cst) ==
                        document(paragraph(constrainedBold(text("bold"))))
                )
            }
            "italic" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstItalic(List(CstText("it")(u)), constrained = false)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(
                    CstLowering.toAst(cst) ==
                        document(paragraph(italic(text("it"))))
                )
            }
            "mono" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstMono(List(CstText("m")(u)), constrained = false)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(
                    CstLowering.toAst(cst) ==
                        document(paragraph(mono(text("m"))))
                )
            }
        }
        "constrained italic/mono lowering" - {
            "CstItalic(constrained=false) lowers to Italic" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstItalic(List(CstText("em")(u)), constrained = false)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(italic(text("em")))))
            }
            "CstItalic(constrained=true) lowers to ConstrainedItalic" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstItalic(List(CstText("em")(u)), constrained = true)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(constrainedItalic(text("em")))))
            }
            "CstMono(constrained=false) lowers to Mono" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstMono(List(CstText("cd")(u)), constrained = false)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(mono(text("cd")))))
            }
            "CstMono(constrained=true) lowers to ConstrainedMono" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstMono(List(CstText("cd")(u)), constrained = true)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(constrainedMono(text("cd")))))
            }
        }
        "attribute references" - {
            "resolves attribute ref defined in header" in {
                val entry = CstAttributeEntry("version", "1.0", false)(u)
                val ref   = CstAttributeRef("version")(u)
                val cst = CstDocument(
                    Some(
                        CstDocumentHeader(
                            CstHeading(1, "=", List(CstText("Doc")(u)))(u),
                            List(entry)
                        )(u)
                    ),
                    List(CstParagraph(List(CstParagraphLine(List(ref))(u)))(u))
                )(u)
                assert(
                    CstLowering.toAst(cst) == document(
                        documentHeader(List(text("Doc")), List("version" -> "1.0")),
                        paragraph(text("1.0"))
                    )
                )
            }
            "resolves attribute ref defined in body" in {
                val entry = CstAttributeEntry("foo", "bar", false)(u)
                val ref   = CstAttributeRef("foo")(u)
                val cst = CstDocument(
                    None,
                    List(
                        entry,
                        CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(text("bar"))))
            }
            "unresolved attribute ref passes through as {name}" in {
                val ref = CstAttributeRef("unknown")(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(text("{unknown}"))))
            }
            "unset body entry removes attribute from scope" in {
                val set   = CstAttributeEntry("foo", "bar", false)(u)
                val unset = CstAttributeEntry("foo", "", true)(u)
                val ref   = CstAttributeRef("foo")(u)
                val cst = CstDocument(
                    None,
                    List(
                        set,
                        unset,
                        CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(text("{foo}"))))
            }
            "built-in {empty} resolves to empty string" in {
                val ref = CstAttributeRef("empty")(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(text(""))))
            }
            "built-in {sp} resolves to space" in {
                val ref = CstAttributeRef("sp")(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                    )
                )(u)
                assert(CstLowering.toAst(cst) == document(paragraph(text(" "))))
            }
            "attribute ref in block title is resolved via lowerInlines" in {
                import io.eleven19.ascribe.ast.{Listing, Title, Text as AstText}
                val entry    = CstAttributeEntry("lang", "ruby", false)(u)
                val titleRef = CstAttributeRef("lang")(u)
                val cst = CstDocument(
                    None,
                    List(
                        entry,
                        CstDelimitedBlock(
                            DelimitedBlockKind.Listing,
                            "----",
                            CstVerbatimContent("puts 'hi'")(u),
                            None,
                            Some(CstBlockTitle(List(titleRef))(u))
                        )(u)
                    )
                )(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(l: Listing) =>
                        assert(
                            l.title.exists(t => t.content.exists { case AstText(c) => c == "ruby"; case _ => false })
                        )
                    case other => assert(s"unexpected: $other" == "")
            }
        }
        "admonition paragraphs" - {
            "NOTE: text lowers to Admonition(Note, [Paragraph])" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstAdmonitionParagraph("NOTE", List(CstText("Watch out.")(u)))(u)
                    )
                )(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(Admonition(AdmonitionKind.Note, List(_: Paragraph))) => assert(true)
                    case other => assert(s"unexpected: $other" == "")
            }
            "WARNING: lowers to AdmonitionKind.Warning" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstAdmonitionParagraph("WARNING", List(CstText("Danger.")(u)))(u)
                    )
                )(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(Admonition(AdmonitionKind.Warning, _)) => assert(true)
                    case other                                       => assert(s"unexpected: $other" == "")
            }
            "all five admonition kinds lower correctly" in {
                val kinds = List(
                    "NOTE"      -> AdmonitionKind.Note,
                    "TIP"       -> AdmonitionKind.Tip,
                    "IMPORTANT" -> AdmonitionKind.Important,
                    "CAUTION"   -> AdmonitionKind.Caution,
                    "WARNING"   -> AdmonitionKind.Warning
                )
                val results = kinds.map { case (label, expected) =>
                    val cst = CstDocument(
                        None,
                        List(
                            CstAdmonitionParagraph(label, List(CstText("text")(u)))(u)
                        )
                    )(u)
                    CstLowering.toAst(cst).blocks match
                        case List(Admonition(kind, _)) => kind == expected
                        case _                         => false
                }
                assert(results.forall(identity))
            }
        }
        "heading and section restructuring" - {
            "level-1 heading is not restructured into a section" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstHeading(1, "=", List(CstText("Title")(u)))(u)
                    )
                )(u)
                // Level-1 headings stay as Heading in the block list
                val doc = CstLowering.toAst(cst)
                assert(doc.blocks.length == 1)
                assert(doc.blocks.head.isInstanceOf[Heading])
            }
            "level-2 heading becomes a section" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstHeading(2, "==", List(CstText("Section")(u)))(u)
                    )
                )(u)
                val doc = CstLowering.toAst(cst)
                assert(doc.blocks.length == 1)
                assert(doc.blocks.head.isInstanceOf[Section])
            }
            "document header is lowered" in {
                val cst = CstDocument(
                    Some(
                        CstDocumentHeader(
                            CstHeading(1, "=", List(CstText("My Doc")(u)))(u),
                            Nil
                        )(u)
                    ),
                    Nil
                )(u)
                assert(
                    CstLowering.toAst(cst) ==
                        document(documentHeader(text("My Doc")))
                )
            }
        }
        "link lowering" - {
            "CstAutolink lowers to Link(Auto, ...)" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstAutolink("https://example.com")(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result = CstLowering.toAst(cst)
                assert(result == document(paragraph(autoLink("https://example.com"))))
            }
            "CstUrlMacro lowers to Link(Macro(Url(...)), ...)" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(
                                        CstUrlMacro(
                                            "https://example.com",
                                            CstMacroAttrList.textOnly(List(CstText("click")(u)))(u)
                                        )(u)
                                    )
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                val result = CstLowering.toAst(cst)
                assert(result == document(paragraph(urlLink("https", "https://example.com", text("click")))))
            }
            "CstLinkMacro lowers to Link(Macro(Link), ...)" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(
                                        CstLinkMacro(
                                            "report.pdf",
                                            CstMacroAttrList.textOnly(List(CstText("Get Report")(u)))(u)
                                        )(u)
                                    )
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                val result = CstLowering.toAst(cst)
                assert(result == document(paragraph(link("report.pdf", text("Get Report")))))
            }
            "CstMailtoMacro lowers to Link(Macro(MailTo), ...)" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(
                                        CstMailtoMacro(
                                            "user@host.com",
                                            CstMacroAttrList.textOnly(List(CstText("Email")(u)))(u)
                                        )(u)
                                    )
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                val result = CstLowering.toAst(cst)
                assert(result == document(paragraph(mailtoLink("user@host.com", text("Email")))))
            }
            "CstUrlMacro with empty text lowers to Link with empty text list" in {
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(
                                    List(CstUrlMacro("https://example.com", CstMacroAttrList.textOnly(Nil)(u))(u))
                                )(u)
                            )
                        )(u)
                    )
                )(u)
                val result = CstLowering.toAst(cst)
                assert(result == document(paragraph(urlLink("https", "https://example.com"))))
            }
        }
        "link attribute lowering" - {
            "caret shorthand produces window=_blank and NoOpener" in {
                val attrList = CstMacroAttrList(List(CstText("click")(u)), Nil, Nil, hasCaretShorthand = true)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes.window == Some(WindowTarget.Blank))
                assert(linkNode.attributes.options.contains(LinkOption.NoOpener))
            }
            "window=_blank named attr produces window and NoOpener" in {
                val attrList = CstMacroAttrList(List(CstText("click")(u)), Nil, List(("window", "_blank")), false)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes.window == Some(WindowTarget.Blank))
                assert(linkNode.attributes.options.contains(LinkOption.NoOpener))
            }
            "role attr produces CssRole" in {
                val attrList = CstMacroAttrList(List(CstText("click")(u)), Nil, List(("role", "btn")), false)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes.roles == List(CssRole("btn")))
            }
            "space-separated roles produce multiple CssRoles" in {
                val attrList = CstMacroAttrList(List(CstText("click")(u)), Nil, List(("role", "btn primary")), false)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes.roles == List(CssRole("btn"), CssRole("primary")))
            }
            "opts=nofollow produces NoFollow" in {
                val attrList = CstMacroAttrList(List(CstText("click")(u)), Nil, List(("opts", "nofollow")), false)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes.options.contains(LinkOption.NoFollow))
            }
            "empty attrs produce LinkAttributes.empty" in {
                val attrList = CstMacroAttrList(Nil, Nil, Nil, false)(u)
                val cst = CstDocument(
                    None,
                    List(
                        CstParagraph(
                            List(
                                CstParagraphLine(List(CstLinkMacro("path", attrList)(u)))(u)
                            )
                        )(u)
                    )
                )(u)
                val result   = CstLowering.toAst(cst)
                val linkNode = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Link]
                assert(linkNode.attributes == LinkAttributes.empty)
            }
        }
    }

package io.eleven19.ascribe.cst

import zio.test.*
import io.eleven19.ascribe.ast.{Admonition, AdmonitionKind, Heading, Paragraph, Section, Span}
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.cst.{CstAdmonitionParagraph, CstAttributeEntry, CstAttributeRef, CstDocumentHeader, CstDocument, CstParagraph, CstParagraphLine, CstHeading, CstText}

object CstLoweringSpec extends ZIOSpecDefault:
    private val u = Span.unknown

    def spec = suite("CstLowering")(
        test("empty document") {
            val cst = CstDocument(None, Nil)(u)
            assertTrue(CstLowering.toAst(cst) == document())
        },
        test("document with paragraph") {
            val cst = CstDocument(
                None,
                List(CstParagraph(List(
                    CstParagraphLine(List(CstText("Hello world.")(u)))(u)
                ))(u))
            )(u)
            assertTrue(CstLowering.toAst(cst) == document(paragraph(text("Hello world."))))
        },
        test("multi-line paragraph merges lines") {
            val cst = CstDocument(
                None,
                List(CstParagraph(List(
                    CstParagraphLine(List(CstText("Line one.")(u)))(u),
                    CstParagraphLine(List(CstText("Line two.")(u)))(u)
                ))(u))
            )(u)
            // Lines are flattened (no newline separator — matches current AST behaviour)
            assertTrue(CstLowering.toAst(cst) ==
                document(paragraph(text("Line one."), text("Line two."))))
        },
        test("blank lines are dropped") {
            val cst = CstDocument(
                None,
                List(CstBlankLine()(u), CstParagraph(List(
                    CstParagraphLine(List(CstText("Hello.")(u)))(u)
                ))(u), CstBlankLine()(u))
            )(u)
            assertTrue(CstLowering.toAst(cst) == document(paragraph(text("Hello."))))
        },
        test("line comments are dropped") {
            val cst = CstDocument(
                None,
                List(
                    CstLineComment("a comment")(u),
                    CstParagraph(List(CstParagraphLine(List(CstText("Para.")(u)))(u)))(u)
                )
            )(u)
            assertTrue(CstLowering.toAst(cst) == document(paragraph(text("Para."))))
        },
        suite("inline lowering")(
            test("unconstrained bold") {
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(
                        CstBold(List(CstText("bold")(u)), constrained = false)(u)
                    ))(u)
                ))(u)))(u)
                assertTrue(CstLowering.toAst(cst) ==
                    document(paragraph(bold(text("bold")))))
            },
            test("constrained bold") {
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(
                        CstBold(List(CstText("bold")(u)), constrained = true)(u)
                    ))(u)
                ))(u)))(u)
                assertTrue(CstLowering.toAst(cst) ==
                    document(paragraph(constrainedBold(text("bold")))))
            },
            test("italic") {
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstItalic(List(CstText("it")(u)))(u)))(u)
                ))(u)))(u)
                assertTrue(CstLowering.toAst(cst) ==
                    document(paragraph(italic(text("it")))))
            },
            test("mono") {
                val cst = CstDocument(None, List(CstParagraph(List(
                    CstParagraphLine(List(CstMono(List(CstText("m")(u)))(u)))(u)
                ))(u)))(u)
                assertTrue(CstLowering.toAst(cst) ==
                    document(paragraph(mono(text("m")))))
            }
        ),
        suite("attribute references")(
            test("resolves attribute ref defined in header") {
                val entry = CstAttributeEntry("version", "1.0", false)(u)
                val ref   = CstAttributeRef("version")(u)
                val cst   = CstDocument(
                    Some(CstDocumentHeader(
                        CstHeading(1, "=", List(CstText("Doc")(u)))(u),
                        List(entry)
                    )(u)),
                    List(CstParagraph(List(CstParagraphLine(List(ref))(u)))(u))
                )(u)
                assertTrue(CstLowering.toAst(cst) == document(documentHeader(List(text("Doc")), List("version" -> "1.0")), paragraph(text("1.0"))))
            },
            test("resolves attribute ref defined in body") {
                val entry = CstAttributeEntry("foo", "bar", false)(u)
                val ref   = CstAttributeRef("foo")(u)
                val cst = CstDocument(None, List(
                    entry,
                    CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                ))(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(text("bar"))))
            },
            test("unresolved attribute ref passes through as {name}") {
                val ref = CstAttributeRef("unknown")(u)
                val cst = CstDocument(None, List(
                    CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                ))(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(text("{unknown}"))))
            },
            test("unset body entry removes attribute from scope") {
                val set   = CstAttributeEntry("foo", "bar", false)(u)
                val unset = CstAttributeEntry("foo", "", true)(u)
                val ref   = CstAttributeRef("foo")(u)
                val cst = CstDocument(None, List(
                    set, unset,
                    CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                ))(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(text("{foo}"))))
            },
            test("built-in {empty} resolves to empty string") {
                val ref = CstAttributeRef("empty")(u)
                val cst = CstDocument(None, List(
                    CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                ))(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(text(""))))
            },
            test("built-in {sp} resolves to space") {
                val ref = CstAttributeRef("sp")(u)
                val cst = CstDocument(None, List(
                    CstParagraph(List(CstParagraphLine(List(ref))(u)))(u)
                ))(u)
                assertTrue(CstLowering.toAst(cst) == document(paragraph(text(" "))))
            },
            test("attribute ref in block title is resolved via lowerInlines") {
                import io.eleven19.ascribe.ast.{Listing, Title, Text as AstText}
                val entry = CstAttributeEntry("lang", "ruby", false)(u)
                val titleRef = CstAttributeRef("lang")(u)
                val cst = CstDocument(None, List(
                    entry,
                    CstDelimitedBlock(
                        DelimitedBlockKind.Listing,
                        "----",
                        CstVerbatimContent("puts 'hi'")(u),
                        None,
                        Some(CstBlockTitle(List(titleRef))(u))
                    )(u)
                ))(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(l: Listing) =>
                        assertTrue(l.title.exists(t => t.content.exists { case AstText(c) => c == "ruby"; case _ => false }))
                    case other => assertTrue(s"unexpected: $other" == "")
            }
        ),
        suite("admonition paragraphs")(
            test("NOTE: text lowers to Admonition(Note, [Paragraph])") {
                val cst = CstDocument(None, List(
                    CstAdmonitionParagraph("NOTE", List(CstText("Watch out.")(u)))(u)
                ))(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(Admonition(AdmonitionKind.Note, List(_: Paragraph))) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("WARNING: lowers to AdmonitionKind.Warning") {
                val cst = CstDocument(None, List(
                    CstAdmonitionParagraph("WARNING", List(CstText("Danger.")(u)))(u)
                ))(u)
                val doc = CstLowering.toAst(cst)
                doc.blocks match
                    case List(Admonition(AdmonitionKind.Warning, _)) => assertTrue(true)
                    case other => assertTrue(s"unexpected: $other" == "")
            },
            test("all five admonition kinds lower correctly") {
                val kinds = List(
                    "NOTE"      -> AdmonitionKind.Note,
                    "TIP"       -> AdmonitionKind.Tip,
                    "IMPORTANT" -> AdmonitionKind.Important,
                    "CAUTION"   -> AdmonitionKind.Caution,
                    "WARNING"   -> AdmonitionKind.Warning
                )
                val results = kinds.map { case (label, expected) =>
                    val cst = CstDocument(None, List(
                        CstAdmonitionParagraph(label, List(CstText("text")(u)))(u)
                    ))(u)
                    CstLowering.toAst(cst).blocks match
                        case List(Admonition(kind, _)) => kind == expected
                        case _                         => false
                }
                assertTrue(results.forall(identity))
            }
        ),
        suite("heading and section restructuring")(
            test("level-1 heading is not restructured into a section") {
                val cst = CstDocument(None, List(
                    CstHeading(1, "=", List(CstText("Title")(u)))(u)
                ))(u)
                // Level-1 headings stay as Heading in the block list
                val doc = CstLowering.toAst(cst)
                assertTrue(doc.blocks.length == 1) &&
                assertTrue(doc.blocks.head.isInstanceOf[Heading])
            },
            test("level-2 heading becomes a section") {
                val cst = CstDocument(None, List(
                    CstHeading(2, "==", List(CstText("Section")(u)))(u)
                ))(u)
                val doc = CstLowering.toAst(cst)
                assertTrue(doc.blocks.length == 1) &&
                assertTrue(doc.blocks.head.isInstanceOf[Section])
            },
            test("document header is lowered") {
                val cst = CstDocument(
                    Some(CstDocumentHeader(
                        CstHeading(1, "=", List(CstText("My Doc")(u)))(u),
                        Nil
                    )(u)),
                    Nil
                )(u)
                assertTrue(CstLowering.toAst(cst) ==
                    document(documentHeader(text("My Doc"))))
            }
        )
    )

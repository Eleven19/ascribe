package io.eleven19.ascribe.cst

import zio.test.*
import io.eleven19.ascribe.ast.{Heading, Section, Span}
import io.eleven19.ascribe.ast.dsl.{*, given}

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

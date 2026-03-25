package io.eleven19.ascribe.pipeline

import zio.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import scala.language.implicitConversions
import kyo.<

object RewriteRuleSpec extends ZIOSpecDefault:

    private def runPure[A](v: A < Any): A = v.asInstanceOf[A]

    private def rewriteDoc(doc: Document, rule: RewriteRule[Any]): Document =
        runPure(RewriteRule.rewrite(doc, rule))

    def spec = suite("RewriteRule")(
        test("block rule replaces matching blocks") {
            val rule = RewriteRule.forBlocks {
                case _: Paragraph => RewriteAction.Replace(paragraph("replaced"))
            }
            val doc    = document(paragraph("original"))
            val result = rewriteDoc(doc, rule)
            val text   = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Text].content
            assertTrue(text == "replaced")
        },
        test("block rule removes matching blocks") {
            val rule = RewriteRule.forBlocks {
                case _: Comment => RewriteAction.Remove
            }
            val doc = Document(None, scala.List(paragraph("keep"), Comment("////", "a comment")(Span.unknown)))(Span.unknown)
            val result = rewriteDoc(doc, rule)
            assertTrue(result.blocks.size == 1)
        },
        test("unmatched blocks are retained") {
            val rule = RewriteRule.forBlocks {
                case _: Comment => RewriteAction.Remove
            }
            val doc    = document(paragraph("stay"))
            val result = rewriteDoc(doc, rule)
            assertTrue(result.blocks.size == 1)
        },
        test("inline rule replaces matching inlines") {
            val rule = RewriteRule.forInlines {
                case Bold(content) => RewriteAction.Replace(Italic(content)(Span.unknown))
            }
            val doc    = document(paragraph(bold(text("emphasis"))))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            assertTrue(para.content.head.isInstanceOf[Italic])
        },
        test("inline rule removes matching inlines") {
            val rule = RewriteRule.forInlines {
                case _: Bold => RewriteAction.Remove
            }
            val doc    = document(paragraph(text("keep "), bold(text("drop")), text(" this")))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            assertTrue(para.content.size == 2) // bold removed, two Text nodes remain
        },
        test("compose applies first matching rule") {
            val rule1 = RewriteRule.forBlocks {
                case _: Paragraph => RewriteAction.Replace(paragraph("from rule1"))
            }
            val rule2 = RewriteRule.forBlocks {
                case _: Paragraph => RewriteAction.Replace(paragraph("from rule2"))
            }
            val composed = RewriteRule.compose(rule1, rule2)
            val doc      = document(paragraph("original"))
            val result   = rewriteDoc(doc, composed)
            val text     = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Text].content
            assertTrue(text == "from rule1")
        },
        test("rewrite traverses nested sections") {
            val rule = RewriteRule.forInlines {
                case Text(content) => RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc = document(
                section(1, scala.List(text("title")), paragraph(text("body")))
            )
            val result    = rewriteDoc(doc, rule)
            val sec       = result.blocks.head.asInstanceOf[Section]
            val titleText = sec.title.head.asInstanceOf[Text].content
            val bodyPara  = sec.blocks.head.asInstanceOf[Paragraph]
            val bodyText  = bodyPara.content.head.asInstanceOf[Text].content
            assertTrue(titleText == "TITLE", bodyText == "BODY")
        },
        test("rewrite traverses nested inlines") {
            val rule = RewriteRule.forInlines {
                case Text(content) => RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc    = document(paragraph(bold(text("nested"))))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            val b      = para.content.head.asInstanceOf[Bold]
            val inner  = b.content.head.asInstanceOf[Text].content
            assertTrue(inner == "NESTED")
        },
        test("rewrite on empty document is unchanged") {
            val rule   = RewriteRule.forBlocks { case _ => RewriteAction.Remove }
            val doc    = document()
            val result = rewriteDoc(doc, rule)
            assertTrue(result.blocks.isEmpty)
        },
        test("rewrite traverses list items") {
            val rule = RewriteRule.forInlines {
                case Text(content) => RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc    = document(unorderedList(listItem(text("item"))))
            val result = rewriteDoc(doc, rule)
            val list     = result.blocks.head.asInstanceOf[UnorderedList]
            val itemText = list.items.head.content.head.asInstanceOf[Text].content
            assertTrue(itemText == "ITEM")
        }
    )

package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.dsl.{*, given}
import io.eleven19.ascribe.pipeline.core.{RewriteAction, RewriteRule}
import kyo.test.*
import scala.language.implicitConversions

class RewriteRuleSpec extends Test[Any]:

    private def rewriteDoc(doc: Document, rule: RewriteRule): Document =
        RewriteRule.rewrite(doc, rule)

    "RewriteRule (Ox, core)" - {
        "block rule replaces matching blocks" in {
            val rule = RewriteRule.forBlocks { case _: Paragraph =>
                RewriteAction.Replace(paragraph("replaced"))
            }
            val doc    = document(paragraph("original"))
            val result = rewriteDoc(doc, rule)
            val text   = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Text].content
            assert(text == "replaced")
        }
        "block rule removes matching blocks" in {
            val rule = RewriteRule.forBlocks { case _: Comment =>
                RewriteAction.Remove
            }
            val doc =
                Document(None, scala.List(paragraph("keep"), Comment("////", "a comment")(Span.unknown)))(Span.unknown)
            val result = rewriteDoc(doc, rule)
            assert(result.blocks.size == 1)
        }
        "unmatched blocks are retained" in {
            val rule = RewriteRule.forBlocks { case _: Comment =>
                RewriteAction.Remove
            }
            val doc    = document(paragraph("stay"))
            val result = rewriteDoc(doc, rule)
            assert(result.blocks.size == 1)
        }
        "inline rule replaces matching inlines" in {
            val rule = RewriteRule.forInlines { case Bold(content) =>
                RewriteAction.Replace(Italic(content)(Span.unknown))
            }
            val doc    = document(paragraph(bold(text("emphasis"))))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            assert(para.content.head.isInstanceOf[Italic])
        }
        "inline rule removes matching inlines" in {
            val rule = RewriteRule.forInlines { case _: Bold =>
                RewriteAction.Remove
            }
            val doc    = document(paragraph(text("keep "), bold(text("drop")), text(" this")))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            assert(para.content.size == 2)
        }
        "compose applies first matching rule" in {
            val rule1 = RewriteRule.forBlocks { case _: Paragraph =>
                RewriteAction.Replace(paragraph("from rule1"))
            }
            val rule2 = RewriteRule.forBlocks { case _: Paragraph =>
                RewriteAction.Replace(paragraph("from rule2"))
            }
            val composed = RewriteRule.compose(rule1, rule2)
            val doc      = document(paragraph("original"))
            val result   = rewriteDoc(doc, composed)
            val text     = result.blocks.head.asInstanceOf[Paragraph].content.head.asInstanceOf[Text].content
            assert(text == "from rule1")
        }
        "rewrite traverses nested sections" in {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc = document(
                section(1, scala.List(text("title")), paragraph(text("body")))
            )
            val result    = rewriteDoc(doc, rule)
            val sec       = result.blocks.head.asInstanceOf[Section]
            val titleText = sec.title.head.asInstanceOf[Text].content
            val bodyPara  = sec.blocks.head.asInstanceOf[Paragraph]
            val bodyText  = bodyPara.content.head.asInstanceOf[Text].content
            assert(titleText == "TITLE")
            assert(bodyText == "BODY")
        }
        "rewrite traverses nested inlines" in {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc    = document(paragraph(bold(text("nested"))))
            val result = rewriteDoc(doc, rule)
            val para   = result.blocks.head.asInstanceOf[Paragraph]
            val b      = para.content.head.asInstanceOf[Bold]
            val inner  = b.content.head.asInstanceOf[Text].content
            assert(inner == "NESTED")
        }
        "rewrite on empty document is unchanged" in {
            val rule   = RewriteRule.forBlocks { case _ => RewriteAction.Remove }
            val doc    = document()
            val result = rewriteDoc(doc, rule)
            assert(result.blocks.isEmpty)
        }
        "rewrite traverses list items" in {
            val rule = RewriteRule.forInlines { case Text(content) =>
                RewriteAction.Replace(Text(content.toUpperCase)(Span.unknown))
            }
            val doc      = document(unorderedList(listItem(text("item"))))
            val result   = rewriteDoc(doc, rule)
            val list     = result.blocks.head.asInstanceOf[UnorderedList]
            val itemText = list.items.head.content.head.asInstanceOf[Text].content
            assert(itemText == "ITEM")
        }
    }

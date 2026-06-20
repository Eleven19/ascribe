package io.eleven19.ascribe.cst

import kyo.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.Span
import io.eleven19.ascribe.cst.CstMacroAttrList
import parsley.Success

class CstVisitorSpec extends Test[Any]:

    "CstVisitor" - {
        "count counts all nodes" in {
            Ascribe.parseCst("= Title\n\n// comment\n\nPara.\n") match
                case Success(doc) =>
                    val n = doc.count
                    assert(n > 5) // document + header + heading + blank + comment + blank + paragraph + line + text
                case _ => assert(false)
        }
        "foldLeft visits all nodes pre-order" in {
            Ascribe.parseCst("Para one.\n") match
                case Success(doc) =>
                    val types = doc
                        .foldLeft(List.empty[String]) { (acc, n) =>
                            n.getClass.getSimpleName :: acc
                        }
                        .reverse
                    assert(types.head == "CstDocument")
                case _ => assert(false)
        }
        "collect extracts CstText values" in {
            Ascribe.parseCst("Hello **world**.\n") match
                case Success(doc) =>
                    val texts = doc.collect { case t: CstText => t.content }
                    assert(texts.contains("Hello "))
                    assert(texts.contains("world"))
                case _ => assert(false)
        }
        "collect finds CstLineComment nodes" in {
            Ascribe.parseCst("// comment here\nPara.\n") match
                case Success(doc) =>
                    val comments = doc.collect { case c: CstLineComment => c.content }
                    assert(comments.nonEmpty)
                case _ => assert(false)
        }
        "count returns 1 for a leaf node" in {
            val t = CstText("hello")(Span.unknown)
            assert(t.count == 1)
        }
        "collect finds CstAttributeRef nodes" in {
            Ascribe.parseCst("{version} text\n") match
                case Success(doc) =>
                    val refs = doc.collect { case r: CstAttributeRef => r.name }
                    assert(refs == List("version"))
                case _ => assert(false)
        }
        "count includes CstAttributeRef in total" in {
            Ascribe.parseCst("{x}\n") match
                case Success(doc) =>
                    assert(doc.count > 3) // document + paragraph + line + ref
                case _ => assert(false)
        }
        "collect finds CstAdmonitionParagraph nodes" in {
            Ascribe.parseCst("NOTE: Watch out.\n") match
                case Success(doc) =>
                    val kinds = doc.collect { case a: CstAdmonitionParagraph => a.kind }
                    assert(kinds == List("NOTE"))
                case _ => assert(false)
        }
        "collect finds CstLink nodes via sealed trait" in {
            Ascribe.parseCst("See https://example.com today.\n") match
                case Success(doc) =>
                    val links = doc.collect { case l: CstLink => l.target }
                    assert(links == List("https://example.com"))
                case _ => assert(false)
        }
        "collect finds all link variants via CstLink" in {
            Ascribe.parseCst("https://a.com and link:b.pdf[B] end.\n") match
                case Success(doc) =>
                    val links = doc.collect { case l: CstLink => l.target }
                    assert(links == List("https://a.com", "b.pdf"))
                case _ => assert(false)
        }
        "visitLink groups all link node types" in {
            val u       = Span.unknown
            var visited = List.empty[String]
            val visitor = new CstVisitor[Unit]:
                def visitNode(node: CstNode): Unit = ()
                override def visitLink(node: CstLink): Unit =
                    visited = visited :+ node.target

            CstVisitor.visit(CstAutolink("https://a.com")(u), visitor)
            CstVisitor.visit(CstUrlMacro("https://b.com", CstMacroAttrList.textOnly(Nil)(u))(u), visitor)
            CstVisitor.visit(CstLinkMacro("c.pdf", CstMacroAttrList.textOnly(Nil)(u))(u), visitor)
            CstVisitor.visit(CstMailtoMacro("d@e.com", CstMacroAttrList.textOnly(Nil)(u))(u), visitor)

            assert(visited == List("https://a.com", "https://b.com", "c.pdf", "d@e.com"))
        }
        "children returns CstMacroAttrList for CstUrlMacro" in {
            val u        = Span.unknown
            val text     = CstText("click")(u)
            val attrList = CstMacroAttrList.textOnly(List(text))(u)
            val link     = CstUrlMacro("https://example.com", attrList)(u)
            assert(link.children == List(attrList))
        }
        "children is empty for CstAutolink" in {
            val u = Span.unknown
            assert(CstAutolink("https://example.com")(u).children.isEmpty)
        }
    }

package io.eleven19.ascribe.cst

import zio.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.Span
import io.eleven19.ascribe.cst.CstMacroAttrList
import parsley.Success

object CstVisitorSpec extends ZIOSpecDefault:

    def spec = suite("CstVisitor")(
        test("count counts all nodes") {
            Ascribe.parseCst("= Title\n\n// comment\n\nPara.\n") match
                case Success(doc) =>
                    val n = doc.count
                    assertTrue(n > 5) // document + header + heading + blank + comment + blank + paragraph + line + text
                case _ => assertTrue(false)
        },
        test("foldLeft visits all nodes pre-order") {
            Ascribe.parseCst("Para one.\n") match
                case Success(doc) =>
                    val types = doc
                        .foldLeft(List.empty[String]) { (acc, n) =>
                            n.getClass.getSimpleName :: acc
                        }
                        .reverse
                    assertTrue(types.head == "CstDocument")
                case _ => assertTrue(false)
        },
        test("collect extracts CstText values") {
            Ascribe.parseCst("Hello **world**.\n") match
                case Success(doc) =>
                    val texts = doc.collect { case t: CstText => t.content }
                    assertTrue(texts.contains("Hello ")) &&
                    assertTrue(texts.contains("world"))
                case _ => assertTrue(false)
        },
        test("collect finds CstLineComment nodes") {
            Ascribe.parseCst("// comment here\nPara.\n") match
                case Success(doc) =>
                    val comments = doc.collect { case c: CstLineComment => c.content }
                    assertTrue(comments.nonEmpty)
                case _ => assertTrue(false)
        },
        test("count returns 1 for a leaf node") {
            val t = CstText("hello")(Span.unknown)
            assertTrue(t.count == 1)
        },
        test("collect finds CstAttributeRef nodes") {
            Ascribe.parseCst("{version} text\n") match
                case Success(doc) =>
                    val refs = doc.collect { case r: CstAttributeRef => r.name }
                    assertTrue(refs == List("version"))
                case _ => assertTrue(false)
        },
        test("count includes CstAttributeRef in total") {
            Ascribe.parseCst("{x}\n") match
                case Success(doc) =>
                    assertTrue(doc.count > 3) // document + paragraph + line + ref
                case _ => assertTrue(false)
        },
        test("collect finds CstAdmonitionParagraph nodes") {
            Ascribe.parseCst("NOTE: Watch out.\n") match
                case Success(doc) =>
                    val kinds = doc.collect { case a: CstAdmonitionParagraph => a.kind }
                    assertTrue(kinds == List("NOTE"))
                case _ => assertTrue(false)
        },
        test("collect finds CstLink nodes via sealed trait") {
            Ascribe.parseCst("See https://example.com today.\n") match
                case Success(doc) =>
                    val links = doc.collect { case l: CstLink => l.target }
                    assertTrue(links == List("https://example.com"))
                case _ => assertTrue(false)
        },
        test("collect finds all link variants via CstLink") {
            Ascribe.parseCst("https://a.com and link:b.pdf[B] end.\n") match
                case Success(doc) =>
                    val links = doc.collect { case l: CstLink => l.target }
                    assertTrue(links == List("https://a.com", "b.pdf"))
                case _ => assertTrue(false)
        },
        test("visitLink groups all link node types") {
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

            assertTrue(visited == List("https://a.com", "https://b.com", "c.pdf", "d@e.com"))
        },
        test("children returns CstMacroAttrList for CstUrlMacro") {
            val u       = Span.unknown
            val text    = CstText("click")(u)
            val attrList = CstMacroAttrList.textOnly(List(text))(u)
            val link    = CstUrlMacro("https://example.com", attrList)(u)
            assertTrue(link.children == List(attrList))
        },
        test("children is empty for CstAutolink") {
            val u = Span.unknown
            assertTrue(CstAutolink("https://example.com")(u).children.isEmpty)
        }
    )

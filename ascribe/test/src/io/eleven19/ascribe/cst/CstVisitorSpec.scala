package io.eleven19.ascribe.cst

import zio.test.*
import io.eleven19.ascribe.Ascribe
import io.eleven19.ascribe.ast.Span
import parsley.Success

object CstVisitorSpec extends ZIOSpecDefault:

    def spec = suite("CstVisitor")(
        test("count counts all nodes") {
            Ascribe.parseCst("= Title\n\n// comment\n\nPara.\n") match
                case Success(doc) =>
                    val n = doc.count
                    assertTrue(n > 5)  // document + header + heading + blank + comment + blank + paragraph + line + text
                case _ => assertTrue(false)
        },
        test("foldLeft visits all nodes pre-order") {
            Ascribe.parseCst("Para one.\n") match
                case Success(doc) =>
                    val types = doc.foldLeft(List.empty[String]) { (acc, n) =>
                        n.getClass.getSimpleName :: acc
                    }.reverse
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
                    assertTrue(doc.count > 3)  // document + paragraph + line + ref
                case _ => assertTrue(false)
        }
    )

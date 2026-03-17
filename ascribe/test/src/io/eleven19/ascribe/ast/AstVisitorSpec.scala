package io.eleven19.ascribe.ast

import zio.test.*

object AstVisitorSpec extends ZIOSpecDefault:

    private val u = Span.unknown

    def spec = suite("AstVisitor")(
        test("visit dispatches to correct visitor method") {
            val visitor = new AstVisitor[String]:
                def visitNode(node: AstNode): String = "node"
                override def visitText(node: Text): String = s"text:${node.content}"
                override def visitParagraph(node: Paragraph): String = "paragraph"

            assertTrue(
                Text("hello")(u).visit(visitor) == "text:hello",
                Paragraph(scala.List(Text("x")(u)))(u).visit(visitor) == "paragraph"
            )
        },
        test("visitor defaults delegate up the hierarchy") {
            var visited = ""
            val visitor = new AstVisitor[Unit]:
                def visitNode(node: AstNode): Unit = visited = "node"
                override def visitBlock(node: Block): Unit = visited = "block"
                override def visitInline(node: Inline): Unit = visited = "inline"

            Text("hi")(u).visit(visitor)
            val inlineResult = visited
            Paragraph(Nil)(u).visit(visitor)
            val blockResult = visited
            Document(Nil)(u).visit(visitor)
            val docResult = visited

            assertTrue(
                inlineResult == "inline",
                blockResult == "block",
                docResult == "node"
            )
        },
        test("children returns direct child nodes") {
            val t1   = Text("a")(u)
            val t2   = Text("b")(u)
            val para = Paragraph(scala.List(t1, t2))(u)
            val doc  = Document(scala.List(para))(u)

            assertTrue(
                doc.children.size == 1,
                doc.children.head == para,
                para.children.size == 2,
                t1.children.isEmpty
            )
        },
        test("children returns list items for UnorderedList") {
            val item = ListItem(scala.List(Text("a")(u)))(u)
            val list = UnorderedList(scala.List(item))(u)

            assertTrue(list.children.size == 1, list.children.head == item)
        },
        test("children returns inlines for ListItem") {
            val t    = Text("content")(u)
            val item = ListItem(scala.List(t))(u)

            assertTrue(item.children.size == 1, item.children.head == t)
        },
        test("fold counts all nodes in a tree") {
            val text = Text("hello")(u)
            val para = Paragraph(scala.List(text))(u)
            val doc  = Document(scala.List(para))(u)

            assertTrue(doc.count == 3)
        },
        test("fold on empty document counts one node") {
            assertTrue(Document(Nil)(u).count == 1)
        },
        test("fold collects all text content") {
            val t1   = Text("hello")(u)
            val t2   = Text(" world")(u)
            val bold = Bold(scala.List(t2))(u)
            val para = Paragraph(scala.List(t1, bold))(u)
            val doc  = Document(scala.List(para))(u)

            val texts = doc.collect { case t: Text => t.content }
            assertTrue(texts == scala.List("hello", " world"))
        },
        test("foldLeft visits parent before children") {
            val text = Text("hello")(u)
            val para = Paragraph(scala.List(text))(u)
            val doc  = Document(scala.List(para))(u)

            val order = doc.foldLeft(scala.List.empty[String]) { (acc, node) =>
                node match
                    case _: Document  => acc :+ "doc"
                    case _: Paragraph => acc :+ "para"
                    case _: Text      => acc :+ "text"
                    case _            => acc
            }
            assertTrue(order == scala.List("doc", "para", "text"))
        },
        test("foldRight visits children before parent") {
            val text = Text("hello")(u)
            val para = Paragraph(scala.List(text))(u)
            val doc  = Document(scala.List(para))(u)

            val order = doc.foldRight(scala.List.empty[String]) { (node, acc) =>
                node match
                    case _: Document  => acc :+ "doc"
                    case _: Paragraph => acc :+ "para"
                    case _: Text      => acc :+ "text"
                    case _            => acc
            }
            assertTrue(order == scala.List("text", "para", "doc"))
        },
        test("collectPostOrder returns values in post-order") {
            val t1   = Text("a")(u)
            val t2   = Text("b")(u)
            val para = Paragraph(scala.List(t1, t2))(u)
            val doc  = Document(scala.List(para))(u)

            val preOrder  = doc.collect { case t: Text => t.content }
            val postOrder = doc.collectPostOrder { case t: Text => t.content }
            assertTrue(preOrder == scala.List("a", "b"), postOrder == scala.List("b", "a"))
        },
        test("fold traverses nested list structures") {
            val item1 = ListItem(scala.List(Text("a")(u)))(u)
            val item2 = ListItem(scala.List(Text("b")(u)))(u)
            val list  = UnorderedList(scala.List(item1, item2))(u)
            val doc   = Document(scala.List(list))(u)

            val texts = doc.collect { case t: Text => t.content }
            assertTrue(texts == scala.List("a", "b"))
        },
        test("fold is stack-safe for deeply nested trees") {
            val depth = 10000
            var inline: Inline = Text("leaf")(u)
            for _ <- 1 to depth do inline = Bold(scala.List(inline))(u)
            val para = Paragraph(scala.List(inline))(u)
            val doc  = Document(scala.List(para))(u)

            assertTrue(doc.count == depth + 3)
        },
        test("foldRight is stack-safe for deeply nested trees") {
            val depth = 10000
            var inline: Inline = Text("leaf")(u)
            for _ <- 1 to depth do inline = Bold(scala.List(inline))(u)
            val para = Paragraph(scala.List(inline))(u)
            val doc  = Document(scala.List(para))(u)

            val count = doc.foldRight(0) { (_, n) => n + 1 }
            assertTrue(count == depth + 3)
        }
    )

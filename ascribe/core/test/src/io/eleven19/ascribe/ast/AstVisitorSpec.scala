package io.eleven19.ascribe.ast

import kyo.test.*

class AstVisitorSpec extends Test[Any]:

    private val u = Span.unknown

    "AstVisitor" - {
        "visit dispatches to correct visitor method" in {
            val visitor = new AstVisitor[String]:
                def visitNode(node: AstNode): String                 = "node"
                override def visitText(node: Text): String           = s"text:${node.content}"
                override def visitParagraph(node: Paragraph): String = "paragraph"

            assert(Text("hello")(u).visit(visitor) == "text:hello")
            assert(Paragraph(scala.List(Text("x")(u)))(u).visit(visitor) == "paragraph")
        }
        "visitor defaults delegate up the hierarchy" in {
            var visited = ""
            val visitor = new AstVisitor[Unit]:
                def visitNode(node: AstNode): Unit           = visited = "node"
                override def visitBlock(node: Block): Unit   = visited = "block"
                override def visitInline(node: Inline): Unit = visited = "inline"

            Text("hi")(u).visit(visitor)
            val inlineResult = visited
            Paragraph(Nil)(u).visit(visitor)
            val blockResult = visited
            Document(Nil)(u).visit(visitor)
            val docResult = visited

            assert(inlineResult == "inline")
            assert(blockResult == "block")
            assert(docResult == "node")
        }
        "children returns direct child nodes" in {
            val t1   = Text("a")(u)
            val t2   = Text("b")(u)
            val para = Paragraph(scala.List(t1, t2))(u)
            val doc  = Document(scala.List(para))(u)

            assert(doc.children.size == 1)
            assert(doc.children.head == para)
            assert(para.children.size == 2)
            assert(t1.children.isEmpty)
        }
        "children returns list items for UnorderedList" in {
            val item = ListItem(scala.List(Text("a")(u)))(u)
            val list = UnorderedList(scala.List(item))(u)

            assert(list.children.size == 1)
            assert(list.children.head == item)
        }
        "children returns inlines for ListItem" in {
            val t    = Text("content")(u)
            val item = ListItem(scala.List(t))(u)

            assert(item.children.size == 1)
            assert(item.children.head == t)
        }
        "fold counts all nodes in a tree" in {
            val text = Text("hello")(u)
            val para = Paragraph(scala.List(text))(u)
            val doc  = Document(scala.List(para))(u)

            assert(doc.count == 3)
        }
        "fold on empty document counts one node" in
            assert(Document(Nil)(u).count == 1)
        "fold collects all text content" in {
            val t1   = Text("hello")(u)
            val t2   = Text(" world")(u)
            val bold = Bold(scala.List(t2))(u)
            val para = Paragraph(scala.List(t1, bold))(u)
            val doc  = Document(scala.List(para))(u)

            val texts = doc.collect { case t: Text => t.content }
            assert(texts == scala.List("hello", " world"))
        }
        "foldLeft visits parent before children" in {
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
            assert(order == scala.List("doc", "para", "text"))
        }
        "foldRight visits children before parent" in {
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
            assert(order == scala.List("text", "para", "doc"))
        }
        "collectPostOrder returns values in post-order" in {
            val t1   = Text("a")(u)
            val t2   = Text("b")(u)
            val para = Paragraph(scala.List(t1, t2))(u)
            val doc  = Document(scala.List(para))(u)

            val preOrder  = doc.collect { case t: Text => t.content }
            val postOrder = doc.collectPostOrder { case t: Text => t.content }
            assert(preOrder == scala.List("a", "b"))
            assert(postOrder == scala.List("b", "a"))
        }
        "fold traverses nested list structures" in {
            val item1 = ListItem(scala.List(Text("a")(u)))(u)
            val item2 = ListItem(scala.List(Text("b")(u)))(u)
            val list  = UnorderedList(scala.List(item1, item2))(u)
            val doc   = Document(scala.List(list))(u)

            val texts = doc.collect { case t: Text => t.content }
            assert(texts == scala.List("a", "b"))
        }
        "fold is stack-safe for deeply nested trees" in {
            val depth          = 10000
            var inline: Inline = Text("leaf")(u)
            for _ <- 1 to depth do inline = Bold(scala.List(inline))(u)
            val para = Paragraph(scala.List(inline))(u)
            val doc  = Document(scala.List(para))(u)

            val count = doc.count
            if count != depth + 3 then throw new AssertionError(s"expected ${depth + 3}, got $count")
            succeed
        }
        "foldRight is stack-safe for deeply nested trees" in {
            val depth          = 10000
            var inline: Inline = Text("leaf")(u)
            for _ <- 1 to depth do inline = Bold(scala.List(inline))(u)
            val para = Paragraph(scala.List(inline))(u)
            val doc  = Document(scala.List(para))(u)

            val count = doc.foldRight(0)((_, n) => n + 1)
            assert(count == depth + 3)
        }
        "visitAdmonition dispatches correctly" in {
            val admonition = Admonition(AdmonitionKind.Note, List(Paragraph(Nil)(u)))(u)
            val visitor = new AstVisitor[String]:
                def visitNode(node: AstNode): String                   = "node"
                override def visitAdmonition(node: Admonition): String = s"admonition:${node.kind}"
            assert(admonition.visit(visitor) == "admonition:Note")
        }
        "admonition children are traversable" in {
            val para       = Paragraph(List(Text("warn")(u)))(u)
            val admonition = Admonition(AdmonitionKind.Warning, List(para))(u)
            val texts      = admonition.collect { case Text(c) => c }
            assert(texts.contains("warn"))
        }
        "visitLink dispatches correctly" in {
            import io.eleven19.ascribe.ast.dsl.{autoLink, link, mailtoLink}
            val visitor = new AstVisitor[String]:
                def visitNode(node: AstNode): String       = "node"
                override def visitLink(node: Link): String = s"link:${node.target}"

            assert(autoLink("https://example.com").visit(visitor) == "link:https://example.com")
            assert(link("report.pdf", Text("R")(u)).visit(visitor) == "link:report.pdf")
            assert(mailtoLink("a@b.com", Text("E")(u)).visit(visitor) == "link:a@b.com")
        }
        "Link children returns text inlines" in {
            import io.eleven19.ascribe.ast.dsl.link
            val t = Text("click")(u)
            val l = link("path", t)
            assert(l.children == scala.List(t))
        }
        "Link children is empty for autolinks" in {
            import io.eleven19.ascribe.ast.dsl.autoLink
            assert(autoLink("https://example.com").children.isEmpty)
        }
        "collect finds Link nodes in document" in {
            import io.eleven19.ascribe.ast.dsl.{document, paragraph, autoLink, text}
            val doc   = document(paragraph(text("See "), autoLink("https://example.com")))
            val links = doc.collect { case l: Link => l.target }
            assert(links == scala.List("https://example.com"))
        }
    }

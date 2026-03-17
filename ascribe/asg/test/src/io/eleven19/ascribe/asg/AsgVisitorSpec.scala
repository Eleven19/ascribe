package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import munit.FunSuite

class AsgVisitorSpec extends FunSuite:

  val loc: Location = Location(Position(1, 1), Position(1, 10))

  test("visit dispatches to correct visitor method") {
    val visitor = new AsgVisitor[String]:
      def visitNode(node: Node): String = "node"
      override def visitText(node: Text): String = s"text:${node.value}"
      override def visitParagraph(node: Paragraph): String = "paragraph"

    assertEquals(Text("hello", loc).visit(visitor), "text:hello")
    assertEquals(Paragraph(inlines = Chunk(Text("x", loc)), location = loc).visit(visitor), "paragraph")
  }

  test("visitor defaults delegate up the hierarchy") {
    var visited = ""
    val visitor = new AsgVisitor[Unit]:
      def visitNode(node: Node): Unit = visited = "node"
      override def visitBlock(node: Block): Unit = visited = "block"
      override def visitInline(node: Inline): Unit = visited = "inline"

    Text("hi", loc).visit(visitor)
    assertEquals(visited, "inline")

    Paragraph(location = loc).visit(visitor)
    assertEquals(visited, "block")

    Document(location = loc).visit(visitor)
    assertEquals(visited, "node")
  }

  test("children returns direct child nodes") {
    val text1 = Text("a", loc)
    val text2 = Text("b", loc)
    val para = Paragraph(inlines = Chunk(text1, text2), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    assertEquals(doc.children.size, 1)
    assertEquals(doc.children.head, para)
    assertEquals(para.children.size, 2)
    assertEquals(text1.children.size, 0)
  }

  test("fold counts all nodes in a tree") {
    val text = Text("hello", loc)
    val para = Paragraph(inlines = Chunk(text), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    val count = doc.fold(0)((n, _) => n + 1)
    assertEquals(count, 3) // doc + para + text
  }

  test("fold collects all text values") {
    val t1 = Text("hello", loc)
    val t2 = Text(" world", loc)
    val bold = Span(variant = "strong", form = "constrained", inlines = Chunk(t2), location = loc)
    val para = Paragraph(inlines = Chunk(t1, bold), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    val texts = doc.fold(Chunk.empty[String]) { (acc, node) =>
      node match
        case t: Text => acc :+ t.value
        case _       => acc
    }
    assertEquals(texts, Chunk("hello", " world"))
  }

  test("fold traverses nested list structures") {
    val item1 = ListItem(marker = "*", principal = Chunk(Text("a", loc)), location = loc)
    val item2 = ListItem(marker = "*", principal = Chunk(Text("b", loc)), location = loc)
    val list = List(variant = "unordered", marker = "*", items = Chunk(item1, item2), location = loc)

    val texts = list.fold(Chunk.empty[String]) { (acc, node) =>
      node match
        case t: Text => acc :+ t.value
        case _       => acc
    }
    assertEquals(texts, Chunk("a", "b"))
  }

  test("collect finds nodes matching a predicate") {
    val t1 = Text("hello", loc)
    val t2 = Text("world", loc)
    val para = Paragraph(inlines = Chunk(t1, t2), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    val texts = AsgVisitor.collect(doc) { case t: Text => t }
    assertEquals(texts.size, 2)
  }

  test("count returns total number of nodes") {
    val para = Paragraph(inlines = Chunk(Text("a", loc), Text("b", loc)), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)
    assertEquals(AsgVisitor.count(doc), 4) // doc + para + 2 texts
  }

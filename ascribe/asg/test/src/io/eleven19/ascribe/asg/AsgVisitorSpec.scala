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

  test("children includes Document header title inlines") {
    val titleText = Text("My Title", loc)
    val header = Header(title = Some(Chunk(titleText)))
    val doc = Document(header = Some(header), location = loc)

    val kids = doc.children
    assertEquals(kids.size, 1)
    assertEquals(kids.head, titleText)
  }

  test("children includes block title and reftext inlines") {
    val titleText = Text("title", loc)
    val reftextText = Text("ref", loc)
    val bodyText = Text("body", loc)
    val para = Paragraph(
      title = Some(Chunk(titleText)),
      reftext = Some(Chunk(reftextText)),
      inlines = Chunk(bodyText),
      location = loc
    )

    val kids = para.children
    assertEquals(kids.size, 3)
    assertEquals(kids(0), titleText)
    assertEquals(kids(1), reftextText)
    assertEquals(kids(2), bodyText)
  }

  test("children handles DListItem with terms and principal") {
    val term1 = Text("term1", loc)
    val term2 = Text("term2", loc)
    val defn = Text("definition", loc)
    val dli = DListItem(
      marker = "::",
      terms = Chunk(Chunk(term1), Chunk(term2)),
      principal = Some(Chunk(defn)),
      location = loc
    )

    val kids = dli.children
    assert(kids.contains(term1))
    assert(kids.contains(term2))
    assert(kids.contains(defn))
  }

  test("fold counts all nodes in a tree") {
    val text = Text("hello", loc)
    val para = Paragraph(inlines = Chunk(text), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    assertEquals(doc.count, 3) // doc + para + text
  }

  test("fold on empty document counts one node") {
    val doc = Document(location = loc)
    assertEquals(doc.count, 1)
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

  test("fold includes document header title text") {
    val titleText = Text("Doc Title", loc)
    val header = Header(title = Some(Chunk(titleText)))
    val bodyText = Text("body", loc)
    val doc = Document(
      header = Some(header),
      blocks = Chunk(Paragraph(inlines = Chunk(bodyText), location = loc)),
      location = loc
    )

    val texts = doc.collect { case t: Text => t.value }
    assertEquals(texts, Chunk("Doc Title", "body"))
  }

  test("fold traverses nested list structures") {
    val item1 = ListItem(marker = "*", principal = Chunk(Text("a", loc)), location = loc)
    val item2 = ListItem(marker = "*", principal = Chunk(Text("b", loc)), location = loc)
    val list = List(variant = "unordered", marker = "*", items = Chunk(item1, item2), location = loc)

    val texts = list.collect { case t: Text => t.value }
    assertEquals(texts, Chunk("a", "b"))
  }

  test("collect finds nodes matching a predicate") {
    val t1 = Text("hello", loc)
    val t2 = Text("world", loc)
    val para = Paragraph(inlines = Chunk(t1, t2), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    val texts = doc.collect { case t: Text => t }
    assertEquals(texts.size, 2)
  }

  test("collect returns typed values") {
    val t1 = Text("hello", loc)
    val t2 = Text("world", loc)
    val para = Paragraph(inlines = Chunk(t1, t2), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)

    val values: Chunk[String] = doc.collect { case t: Text => t.value }
    assertEquals(values, Chunk("hello", "world"))
  }

  test("count returns total number of nodes") {
    val para = Paragraph(inlines = Chunk(Text("a", loc), Text("b", loc)), location = loc)
    val doc = Document(blocks = Chunk(para), location = loc)
    assertEquals(doc.count, 4) // doc + para + 2 texts
  }

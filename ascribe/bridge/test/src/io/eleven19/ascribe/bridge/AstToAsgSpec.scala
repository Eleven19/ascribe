package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import munit.FunSuite
import io.github.eleven19.ascribe.{ast}
import io.eleven19.ascribe.{asg}

class AstToAsgSpec extends FunSuite:

  private val u = ast.Span.unknown

  // Convenience constructors using Span.unknown
  private def text(s: String): ast.Text = ast.Text(s)(u)
  private def bold(inlines: ast.Inline*): ast.Bold = ast.Bold(inlines.toList)(u)
  private def italic(inlines: ast.Inline*): ast.Italic = ast.Italic(inlines.toList)(u)
  private def mono(inlines: ast.Inline*): ast.Mono = ast.Mono(inlines.toList)(u)
  private def listItem(inlines: ast.Inline*): ast.ListItem = ast.ListItem(inlines.toList)(u)
  private def heading(level: Int, inlines: ast.Inline*): ast.Heading =
    ast.Heading(level, inlines.toList)(u)
  private def paragraph(inlines: ast.Inline*): ast.Paragraph = ast.Paragraph(inlines.toList)(u)
  private def unorderedList(items: ast.ListItem*): ast.UnorderedList =
    ast.UnorderedList(items.toList)(u)
  private def orderedList(items: ast.ListItem*): ast.OrderedList =
    ast.OrderedList(items.toList)(u)
  private def document(blocks: ast.Block*): ast.Document = ast.Document(blocks.toList)(u)

  test("converts empty document") {
    val astDoc = document()
    val asgDoc = AstToAsg.convert(astDoc)
    assert(asgDoc.isInstanceOf[asg.Document])
    assertEquals(asgDoc.blocks, Chunk.empty)
  }

  test("converts paragraph with plain text") {
    val astDoc = document(paragraph(text("hello")))
    val asgDoc = AstToAsg.convert(astDoc)
    assertEquals(asgDoc.blocks.size, 1)
    asgDoc.blocks.head match
      case p: asg.Paragraph =>
        assertEquals(p.inlines.size, 1)
        p.inlines.head match
          case t: asg.Text => assertEquals(t.value, "hello")
          case other       => fail(s"Expected Text, got $other")
      case other => fail(s"Expected Paragraph, got $other")
  }

  test("converts heading to heading") {
    val astDoc = document(heading(1, text("Title")))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case h: asg.Heading =>
        assertEquals(h.level, 1)
      case other => fail(s"Expected Heading, got $other")
  }

  test("converts bold to strong span") {
    val astDoc = document(paragraph(bold(text("bold"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "strong")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts italic to emphasis span") {
    val astDoc = document(paragraph(italic(text("em"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "emphasis")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts mono to code span") {
    val astDoc = document(paragraph(mono(text("code"))))
    val asgDoc = AstToAsg.convert(astDoc)
    val para = asgDoc.blocks.head.asInstanceOf[asg.Paragraph]
    para.inlines.head match
      case s: asg.Span =>
        assertEquals(s.variant, "code")
        assertEquals(s.form, "unconstrained")
      case other => fail(s"Expected Span, got $other")
  }

  test("converts unordered list") {
    val astDoc = document(unorderedList(listItem(text("item"))))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case l: asg.List =>
        assertEquals(l.variant, "unordered")
        assertEquals(l.marker, "*")
        assertEquals(l.items.size, 1)
      case other => fail(s"Expected List, got $other")
  }

  test("converts ordered list") {
    val astDoc = document(orderedList(listItem(text("item"))))
    val asgDoc = AstToAsg.convert(astDoc)
    asgDoc.blocks.head match
      case l: asg.List =>
        assertEquals(l.variant, "ordered")
        assertEquals(l.marker, ".")
      case other => fail(s"Expected List, got $other")
  }

  test("converts position spans") {
    val span = ast.Span(ast.Position(1, 1), ast.Position(1, 10))
    val astDoc = ast.Document(
      scala.List(ast.Paragraph(scala.List(ast.Text("hi")(span)))(span))
    )(span)
    val asgDoc = AstToAsg.convert(astDoc)
    assertEquals(asgDoc.location, asg.Location(asg.Position(1, 1), asg.Position(1, 10)))
  }

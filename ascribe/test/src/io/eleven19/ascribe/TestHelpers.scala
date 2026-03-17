package io.eleven19.ascribe

import io.eleven19.ascribe.ast.*

/** Convenience constructors for building AST nodes in tests without specifying positions. */
object TestHelpers:
  private val u = Span.unknown

  def text(s: String): Text = Text(s)(u)
  def bold(inlines: Inline*): Bold = Bold(inlines.toList)(u)
  def italic(inlines: Inline*): Italic = Italic(inlines.toList)(u)
  def mono(inlines: Inline*): Mono = Mono(inlines.toList)(u)
  def listItem(inlines: Inline*): ListItem = ListItem(inlines.toList)(u)
  def heading(level: Int, inlines: Inline*): Heading = Heading(level, inlines.toList)(u)
  def section(level: Int, title: List[Inline], blocks: Block*): Section =
      Section(level, title, blocks.toList)(u)
  def paragraph(inlines: Inline*): Paragraph = Paragraph(inlines.toList)(u)
  def unorderedList(items: ListItem*): UnorderedList = UnorderedList(items.toList)(u)
  def orderedList(items: ListItem*): OrderedList = OrderedList(items.toList)(u)
  def documentHeader(title: Inline*): DocumentHeader = DocumentHeader(title.toList, Nil)(u)
  def documentWithHeader(header: DocumentHeader, blocks: Block*): Document =
      Document(Some(header), blocks.toList)(u)
  def document(blocks: Block*): Document = Document(blocks.toList)(u)

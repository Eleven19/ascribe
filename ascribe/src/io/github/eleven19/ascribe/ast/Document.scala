package io.github.eleven19.ascribe.ast

/** An inline element within a paragraph, heading, or list item. */
enum Inline derives CanEqual:
    /** Plain text content. */
    case Text(content: String)

    /** Bold span, surrounded by double asterisks: **bold**. */
    case Bold(content: List[Inline])

    /** Italic span, surrounded by double underscores: __italic__. */
    case Italic(content: List[Inline])

    /** Monospace span, surrounded by double backticks: ``mono``. */
    case Mono(content: List[Inline])

/** A list of inline elements forming the content of a single line. */
type InlineContent = List[Inline]

/** A single item in a list block. */
case class ListItem(content: InlineContent) derives CanEqual

/** A block-level element in an AsciiDoc document. */
enum Block derives CanEqual:
    /** A section heading.
      *
      * @param level
      *   heading level 1–5 corresponding to = through =====
      * @param title
      *   the inline content forming the heading text
      */
    case Heading(level: Int, title: InlineContent)

    /** A paragraph of one or more lines of inline content. */
    case Paragraph(content: InlineContent)

    /** A bullet list (items prefixed with "* "). */
    case UnorderedList(items: List[ListItem])

    /** A numbered list (items prefixed with ". "). */
    case OrderedList(items: List[ListItem])

/** The top-level document containing an ordered sequence of blocks. */
case class Document(blocks: List[Block]) derives CanEqual

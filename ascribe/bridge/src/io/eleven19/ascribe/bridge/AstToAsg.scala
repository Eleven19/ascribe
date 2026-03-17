package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import io.eleven19.ascribe.ast
import io.eleven19.ascribe.asg

/** Converts parsed AST documents to ASG format. */
object AstToAsg:

    /** Convert an AST Document to an ASG Document. */
    def convert(doc: ast.Document): asg.Document =
        asg.Document(
            blocks = Chunk.from(doc.blocks.map(convertBlock)),
            location = contentLocation(doc.span.start, lastContentPos(doc))
        )

    private def convertBlock(block: ast.Block): asg.Block = block match
        case ast.Heading(level, title) =>
            asg.Heading(
                level = level,
                title = Some(Chunk.from(title.map(convertInline))),
                location = contentLocation(block.span.start, lastContentPos(block))
            )
        case ast.Section(level, title, blocks) =>
            asg.Section(
                level = level,
                title = Some(Chunk.from(title.map(convertInline))),
                blocks = Chunk.from(blocks.map(convertBlock)),
                location = contentLocation(block.span.start, lastContentPos(block))
            )
        case ast.Paragraph(content) =>
            val converted = mergeAdjacentTexts(content.map(convertInline))
            asg.Paragraph(
                inlines = Chunk.from(converted),
                location = contentLocation(block.span.start, lastContentPos(block))
            )
        case ast.ListingBlock(delimiter, content) =>
            val loc = contentLocation(block.span.start, lastContentPos(block))
            // Content location: lines inside the delimiters
            val contentStart = ast.Position(block.span.start.line + 1, 1)
            val contentEnd   = lastContentPos(block)
            asg.Listing(
                form = Some("delimited"),
                delimiter = Some(delimiter),
                inlines = Chunk(
                    asg.Text(
                        content,
                        asg.Location(
                            asg.Position(contentStart.line, contentStart.col),
                            asg.Position(contentEnd.line, contentEnd.col - 1)
                        )
                    )
                ),
                location = loc
            )
        case ast.UnorderedList(items) =>
            asg.List(
                variant = "unordered",
                marker = "*",
                items = Chunk.from(items.map(convertListItem("*"))),
                location = contentLocation(block.span.start, lastContentPos(block))
            )
        case ast.OrderedList(items) =>
            asg.List(
                variant = "ordered",
                marker = ".",
                items = Chunk.from(items.map(convertListItem("."))),
                location = contentLocation(block.span.start, lastContentPos(block))
            )

    private def convertListItem(marker: String)(item: ast.ListItem): asg.ListItem =
        asg.ListItem(
            marker = marker,
            principal = Chunk.from(mergeAdjacentTexts(item.content.map(convertInline))),
            location = contentLocation(item.span.start, lastContentPos(item))
        )

    private def convertInline(inline: ast.Inline): asg.Inline = inline match
        case ast.Text(content) =>
            asg.Text(value = content, location = inclusiveLocation(inline.span))
        case ast.Bold(content) =>
            asg.Span(
                variant = "strong",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
        case ast.ConstrainedBold(content) =>
            asg.Span(
                variant = "strong",
                form = "constrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
        case ast.Italic(content) =>
            asg.Span(
                variant = "emphasis",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )
        case ast.Mono(content) =>
            asg.Span(
                variant = "code",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = inclusiveLocation(inline.span)
            )

    /** Convert a Parsley past-end span to an inclusive ASG location. Subtracts 1 from end col. */
    private def inclusiveLocation(span: ast.Span): asg.Location =
        asg.Location(
            asg.Position(span.start.line, span.start.col),
            asg.Position(span.end.line, span.end.col - 1)
        )

    /** Build an ASG location from a start position and a last-content past-end position. */
    private def contentLocation(start: ast.Position, contentEnd: ast.Position): asg.Location =
        asg.Location(
            asg.Position(start.line, start.col),
            asg.Position(contentEnd.line, contentEnd.col - 1)
        )

    /** Find the past-end position of the last content character in an AST node. For blocks, this traverses to the
      * deepest last child to avoid positions that include consumed newlines.
      */
    private def lastContentPos(node: ast.AstNode): ast.Position = node match
        case d: ast.Document       => d.blocks.lastOption.map(lastContentPos).getOrElse(d.span.end)
        case h: ast.Heading        => h.title.lastOption.map(lastContentPos).getOrElse(h.span.end)
        case s: ast.Section        => s.blocks.lastOption.map(lastContentPos).getOrElse(lastContentPos(s))
        case p: ast.Paragraph      => p.content.lastOption.map(lastContentPos).getOrElse(p.span.end)
        case lb: ast.ListingBlock  => lb.span.end // listing block span includes closing delimiter
        case ul: ast.UnorderedList => ul.items.lastOption.map(lastContentPos).getOrElse(ul.span.end)
        case ol: ast.OrderedList   => ol.items.lastOption.map(lastContentPos).getOrElse(ol.span.end)
        case li: ast.ListItem      => li.content.lastOption.map(lastContentPos).getOrElse(li.span.end)
        case i: ast.Inline         => i.span.end

    /** Merge consecutive asg.Text nodes into a single Text with newline-joined content. This handles multi-line
      * paragraphs where each line was parsed separately.
      */
    private def mergeAdjacentTexts(inlines: scala.List[asg.Inline]): scala.List[asg.Inline] =
        inlines.foldRight(scala.List.empty[asg.Inline]) { (elem, acc) =>
            (elem, acc) match
                case (t1: asg.Text, (t2: asg.Text) :: rest) =>
                    asg.Text(
                        t1.value + "\n" + t2.value,
                        asg.Location(t1.location.start, t2.location.end)
                    ) :: rest
                case _ => elem :: acc
        }

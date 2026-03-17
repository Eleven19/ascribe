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
            location = convertLocation(doc.span)
        )

    private def convertBlock(block: ast.Block): asg.Block = block match
        case ast.Heading(level, title) =>
            asg.Heading(
                level = level,
                title = Some(Chunk.from(title.map(convertInline))),
                location = convertLocation(block.span)
            )
        case ast.Paragraph(content) =>
            asg.Paragraph(
                inlines = Chunk.from(content.map(convertInline)),
                location = convertLocation(block.span)
            )
        case ast.UnorderedList(items) =>
            asg.List(
                variant = "unordered",
                marker = "*",
                items = Chunk.from(items.map(convertListItem("*"))),
                location = convertLocation(block.span)
            )
        case ast.OrderedList(items) =>
            asg.List(
                variant = "ordered",
                marker = ".",
                items = Chunk.from(items.map(convertListItem("."))),
                location = convertLocation(block.span)
            )

    private def convertListItem(marker: String)(item: ast.ListItem): asg.ListItem =
        asg.ListItem(
            marker = marker,
            principal = Chunk.from(item.content.map(convertInline)),
            location = convertLocation(item.span)
        )

    private def convertInline(inline: ast.Inline): asg.Inline = inline match
        case ast.Text(content) =>
            asg.Text(value = content, location = convertLocation(inline.span))
        case ast.Bold(content) =>
            asg.Span(
                variant = "strong",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = convertLocation(inline.span)
            )
        case ast.Italic(content) =>
            asg.Span(
                variant = "emphasis",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = convertLocation(inline.span)
            )
        case ast.Mono(content) =>
            asg.Span(
                variant = "code",
                form = "unconstrained",
                inlines = Chunk.from(content.map(convertInline)),
                location = convertLocation(inline.span)
            )

    private def convertLocation(span: ast.Span): asg.Location =
        asg.Location(
            asg.Position(span.start.line, span.start.col),
            asg.Position(span.end.line, span.end.col)
        )

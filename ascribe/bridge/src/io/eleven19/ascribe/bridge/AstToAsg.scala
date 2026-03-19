package io.eleven19.ascribe.bridge

import zio.blocks.chunk.Chunk
import io.eleven19.ascribe.ast
import io.eleven19.ascribe.asg

/** Converts parsed AST documents to ASG format. */
object AstToAsg:

    /** Convert an AST Document to an ASG Document. */
    def convert(doc: ast.Document): asg.Document =
        val header = doc.header.map(convertHeader)
        val attributes = doc.header.map { h =>
            h.attributes.map((k, v) => (k, Some(v))).toMap
        }
        asg.Document(
            attributes = attributes,
            header = header,
            blocks = Chunk.from(doc.blocks.map(convertBlock)),
            location = contentLocation(doc.span.start, lastContentPos(doc))
        )

    private def convertHeader(h: ast.DocumentHeader): asg.Header =
        val titleInlines = h.title.map(convertInline)
        val headerLoc    = contentLocation(h.span.start, lastContentPos(h))
        asg.Header(
            title = Some(Chunk.from(titleInlines)),
            location = Some(headerLoc)
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
        case tb @ ast.TableBlock(rows, delimiter, _, attrsOpt, titleOpt, hasBlankAfterFirst) =>
            val options = attrsOpt.toList.flatMap(_.options).map(_.value)
            val named   = attrsOpt.map(_.named.map((k, v) => (k.value, v.value))).getOrElse(Map.empty)

            val columns = named.get("cols").map(ColsParser.parse)

            val hasHeader =
                if options.contains("noheader") then false
                else if options.contains("header") then true
                else hasBlankAfterFirst

            val hasFooter = options.contains("footer")

            val colSpecs = columns.getOrElse(Chunk.empty)
            val allRows  = rows.map(convertTableRow(_, colSpecs))
            val (headerRow, bodyStart) =
                if hasHeader && allRows.nonEmpty then
                    // Header row ignores style operators per spec
                    val headerCells = allRows.head.cells.toList.map { c =>
                        val cell = c.asInstanceOf[asg.TableCell]
                        asg.TableCell(style = None, inlines = cell.inlines, location = cell.location): asg.Block
                    }
                    (Some(Chunk.from(headerCells)), allRows.tail)
                else (None, allRows)
            val (bodyRows, footerRow) =
                if hasFooter && bodyStart.nonEmpty then
                    (bodyStart.init, Some(Chunk.from(bodyStart.last.cells.toList.map(c => c: asg.Block))))
                else (bodyStart, None)

            val title = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))

            val validFrames  = Set("all", "ends", "sides", "none")
            val validGrids   = Set("all", "cols", "rows", "none")
            val validStripes = Set("none", "even", "odd", "hover", "all")
            val frame        = named.get("frame").filter(validFrames.contains)
            val grid         = named.get("grid").filter(validGrids.contains)
            val stripes      = named.get("stripes").filter(validStripes.contains)

            val metadata = attrsOpt.map(al =>
                asg.BlockMetadata(
                    attributes = al.named.map((k, v) => (k.value, v.value)),
                    options = Chunk.from(al.options.map(_.value)),
                    roles = Chunk.from(al.roles.map(_.value))
                )
            )

            asg.Table(
                title = title,
                metadata = metadata,
                form = "delimited",
                delimiter = delimiter,
                columns = columns,
                header = headerRow,
                rows = Chunk.from(bodyRows.map(r => r: asg.Block)),
                footer = footerRow,
                frame = frame,
                grid = grid,
                stripes = stripes,
                location = inclusiveLocation(block.span)
            )
        case ast.ListingBlock(delimiter, content, attrsOpt, titleOpt) =>
            // Block location spans from opening delimiter to closing delimiter
            val blockLoc = inclusiveLocation(block.span)
            // Content location: lines inside the delimiters
            val contentStartLine = block.span.start.line + 1
            val contentLines     = content.split("\n", -1)
            val lastLineLen      = contentLines.last.length
            val contentEndLine   = contentStartLine + contentLines.length - 1
            val contentLoc = asg.Location(
                asg.Position(contentStartLine, 1),
                asg.Position(contentEndLine, lastLineLen)
            )
            // Extract source style and language from attribute list
            val positional = attrsOpt.toList.flatMap(_.positional).map(_.value)
            val named      = attrsOpt.map(_.named.map((k, v) => (k.value, v.value))).getOrElse(Map.empty)
            val style      = positional.headOption.orElse(named.get("style"))
            val language   = positional.lift(1).orElse(named.get("language"))
            val title      = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            val metadata = attrsOpt.map { al =>
                val attrs = al.named.map((k, v) => (k.value, v.value)) ++
                    style.map("style" -> _) ++
                    language.map("language" -> _)
                asg.BlockMetadata(
                    attributes = attrs,
                    options = Chunk.from(al.options.map(_.value)),
                    roles = Chunk.from(al.roles.map(_.value))
                )
            }
            asg.Listing(
                title = title,
                metadata = metadata,
                form = Some("delimited"),
                delimiter = Some(delimiter),
                inlines = Chunk(asg.Text(content, contentLoc)),
                location = blockLoc
            )
        case ast.SidebarBlock(delimiter, blocks, _, _) =>
            asg.Sidebar(
                form = "delimited",
                delimiter = delimiter,
                blocks = Chunk.from(blocks.map(convertBlock)),
                location = inclusiveLocation(block.span)
            )
        case ast.ExampleBlock(delimiter, blocks, attrsOpt, titleOpt) =>
            val positional = attrsOpt.toList.flatMap(_.positional).map(_.value)
            val style      = positional.headOption
            val title      = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            // Admonition masquerading: [NOTE], [TIP], [WARNING], etc. on example block
            val admonitionVariants = Set("NOTE", "TIP", "WARNING", "CAUTION", "IMPORTANT")
            if style.exists(admonitionVariants.contains) then
                asg.Admonition(
                    title = title,
                    form = "delimited",
                    delimiter = delimiter,
                    variant = style.get.toLowerCase,
                    blocks = Chunk.from(blocks.map(convertBlock)),
                    location = inclusiveLocation(block.span)
                )
            else
                asg.Example(
                    title = title,
                    form = "delimited",
                    delimiter = delimiter,
                    blocks = Chunk.from(blocks.map(convertBlock)),
                    location = inclusiveLocation(block.span)
                )
        case ast.QuoteBlock(delimiter, blocks, attrsOpt, titleOpt) =>
            val positional = attrsOpt.toList.flatMap(_.positional).map(_.value)
            val style      = positional.headOption
            val title      = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            // Verse masquerading: [verse] on quote block
            if style.contains("verse") then
                val verbatimContent = blocks.flatMap {
                    case ast.Paragraph(content) => content.map(convertInline)
                    case _                      => Nil
                }
                asg.Verse(
                    title = title,
                    form = Some("delimited"),
                    delimiter = Some(delimiter),
                    inlines = Chunk.from(verbatimContent),
                    location = inclusiveLocation(block.span)
                )
            else
                asg.Quote(
                    title = title,
                    form = "delimited",
                    delimiter = delimiter,
                    blocks = Chunk.from(blocks.map(convertBlock)),
                    location = inclusiveLocation(block.span)
                )
        case ast.LiteralBlock(delimiter, content, _, titleOpt) =>
            val blockLoc         = inclusiveLocation(block.span)
            val contentStartLine = block.span.start.line + 1
            val contentLines     = content.split("\n", -1)
            val lastLineLen      = contentLines.last.length
            val contentEndLine   = contentStartLine + contentLines.length - 1
            val contentLoc = asg.Location(
                asg.Position(contentStartLine, 1),
                asg.Position(contentEndLine, lastLineLen)
            )
            val title = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            asg.Literal(
                title = title,
                form = Some("delimited"),
                delimiter = Some(delimiter),
                inlines = Chunk(asg.Text(content, contentLoc)),
                location = blockLoc
            )
        case ast.OpenBlock(delimiter, blocks, _, titleOpt) =>
            val title = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            asg.Open(
                title = title,
                form = "delimited",
                delimiter = delimiter,
                blocks = Chunk.from(blocks.map(convertBlock)),
                location = inclusiveLocation(block.span)
            )
        case _: ast.CommentBlock =>
            // Comment blocks are not preserved in the ASG per spec.
            // Return an empty pass block as placeholder (will be filtered by caller if needed).
            asg.Pass(
                form = Some("delimited"),
                delimiter = Some("////"),
                location = inclusiveLocation(block.span)
            )
        case ast.PassBlock(delimiter, content, _, titleOpt) =>
            val blockLoc         = inclusiveLocation(block.span)
            val contentStartLine = block.span.start.line + 1
            val contentLines     = content.split("\n", -1)
            val lastLineLen      = contentLines.last.length
            val contentEndLine   = contentStartLine + contentLines.length - 1
            val contentLoc = asg.Location(
                asg.Position(contentStartLine, 1),
                asg.Position(contentEndLine, lastLineLen)
            )
            val title = titleOpt.map(bt => Chunk.from(bt.content.map(convertInline)))
            asg.Pass(
                title = title,
                form = Some("delimited"),
                delimiter = Some(delimiter),
                inlines = Chunk(asg.Raw(content, contentLoc)),
                location = blockLoc
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

    private def convertTableRow(row: ast.TableRow, colSpecs: Chunk[asg.ColumnSpec]): asg.TableRow =
        asg.TableRow(
            cells = Chunk.from(row.cells.zipWithIndex.map { (cell, idx) =>
                val colStyle = colSpecs.lift(idx).flatMap(_.style)
                convertTableCell(cell, colStyle)
            }),
            location = contentLocation(row.span.start, lastContentPos(row))
        )

    private def convertTableCell(cell: ast.TableCell, colStyle: Option[asg.CellStyle]): asg.TableCell =
        // Cell specifier style overrides column style
        val effectiveStyle = cell.style.map(_.value).flatMap(asg.CellStyle.fromChar).orElse(colStyle)
        val loc            = contentLocation(cell.span.start, lastContentPos(cell))
        cell.content match
            case ast.CellContent.Inlines(content) =>
                asg.TableCell(
                    style = effectiveStyle,
                    colSpan = cell.colSpan.map(f => asg.ColSpan(f.value)),
                    rowSpan = cell.rowSpan.map(f => asg.RowSpan(f.value)),
                    dupCount = cell.dupFactor.map(f => asg.DupCount(f.value)),
                    inlines = Chunk.from(content.map(convertInline)),
                    location = loc
                )
            case ast.CellContent.Blocks(blocks) =>
                asg.TableCell.withBlocks(
                    style = effectiveStyle,
                    colSpan = cell.colSpan.map(f => asg.ColSpan(f.value)),
                    rowSpan = cell.rowSpan.map(f => asg.RowSpan(f.value)),
                    dupCount = cell.dupFactor.map(f => asg.DupCount(f.value)),
                    blocks = Chunk.from(blocks.map(convertBlock)),
                    location = loc
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
        case d: ast.Document =>
            d.blocks.lastOption
                .map(lastContentPos)
                .orElse(d.header.map(lastContentPos))
                .getOrElse(d.span.end)
        case dh: ast.DocumentHeader =>
            dh.attributes.lastOption
                .map((_, v) => dh.span.end) // attributes extend span
                .orElse(dh.title.lastOption.map(lastContentPos))
                .getOrElse(dh.span.end)
        case h: ast.Heading        => h.title.lastOption.map(lastContentPos).getOrElse(h.span.end)
        case s: ast.Section        => s.blocks.lastOption.map(lastContentPos).getOrElse(lastContentPos(s))
        case p: ast.Paragraph      => p.content.lastOption.map(lastContentPos).getOrElse(p.span.end)
        case lb: ast.ListingBlock  => lb.span.end // listing block span includes closing delimiter
        case _: ast.LiteralBlock   => node.span.end
        case sb: ast.SidebarBlock  => sb.span.end // sidebar block span includes closing delimiter
        case _: ast.ExampleBlock   => node.span.end
        case _: ast.QuoteBlock     => node.span.end
        case _: ast.OpenBlock      => node.span.end
        case _: ast.CommentBlock   => node.span.end
        case _: ast.PassBlock      => node.span.end
        case tb: ast.TableBlock    => tb.span.end // table block span includes closing delimiter
        case al: ast.AttributeList => al.span.end
        case bt: ast.BlockTitle    => bt.content.lastOption.map(lastContentPos).getOrElse(bt.span.end)
        case tr: ast.TableRow      => tr.cells.lastOption.map(lastContentPos).getOrElse(tr.span.end)
        case tc: ast.TableCell =>
            tc.content match
                case ast.CellContent.Inlines(content) => content.lastOption.map(lastContentPos).getOrElse(tc.span.end)
                case ast.CellContent.Blocks(blocks)   => blocks.lastOption.map(lastContentPos).getOrElse(tc.span.end)
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

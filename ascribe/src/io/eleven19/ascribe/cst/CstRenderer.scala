package io.eleven19.ascribe.cst

/** Reconstructs source text from a CST.
  *
  * Best-effort fidelity: blank lines between blocks, line breaks within paragraphs, and marker strings are preserved.
  * Exact trailing spaces and CRLF vs LF differences are not guaranteed to be byte-identical.
  */
object CstRenderer:

    def render(cst: CstDocument): String =
        val sb = new StringBuilder
        cst.header.foreach(h => renderHeader(h, sb))
        cst.content.foreach(n => renderTopLevel(n, sb))
        sb.toString

    private def renderHeader(h: CstDocumentHeader, sb: StringBuilder): Unit =
        sb.append(h.title.marker)
        sb.append(' ')
        renderInlines(h.title.title, sb)
        sb.append('\n')
        h.attributes.foreach(e => renderAttributeEntry(e, sb))

    private def renderTopLevel(node: CstTopLevel, sb: StringBuilder): Unit = node match
        case _: CstBlankLine => sb.append('\n')
        case b: CstBlock     => renderBlock(b, sb)

    private def renderBlock(block: CstBlock, sb: StringBuilder): Unit = block match
        case h: CstHeading =>
            sb.append(h.marker)
            sb.append(' ')
            renderInlines(h.title, sb)
            sb.append('\n')

        case p: CstParagraph =>
            p.lines.foreach { line =>
                renderInlines(line.content, sb)
                sb.append('\n')
            }

        case db: CstDelimitedBlock =>
            db.attributes.foreach(al => renderAttributeList(al, sb))
            db.title.foreach { t =>
                sb.append('.')
                renderInlines(t.content, sb)
                sb.append('\n')
            }
            sb.append(db.delimiter)
            sb.append('\n')
            db.content match
                case CstVerbatimContent(raw) =>
                    sb.append(raw)
                    if raw.nonEmpty && !raw.endsWith("\n") then sb.append('\n')
                case CstNestedContent(children) =>
                    children.foreach(renderTopLevel(_, sb))
            sb.append(db.delimiter)
            sb.append('\n')

        case l: CstList =>
            l.items.foreach { item =>
                sb.append(item.marker)
                renderInlines(item.content, sb)
                sb.append('\n')
            }

        case t: CstTable =>
            t.attributes.foreach(al => renderAttributeList(al, sb))
            t.title.foreach { bt =>
                sb.append('.')
                renderInlines(bt.content, sb)
                sb.append('\n')
            }
            sb.append(t.delimiter)
            sb.append('\n')
            t.rows.foreach { row =>
                row.cells.foreach { cell =>
                    cell.content match
                        case CstCellInlines(inlines) =>
                            sb.append('|')
                            renderInlines(inlines, sb)
                        case CstCellBlocks(blocks) =>
                            sb.append('|')
                            sb.append('\n')
                            blocks.foreach(renderTopLevel(_, sb))
                }
                sb.append('\n')
            }
            sb.append(t.delimiter)
            sb.append('\n')

        case i: CstInclude =>
            sb.append("include::")
            sb.append(i.target)
            sb.append('[')
            sb.append(renderAttrString(i.attributes))
            sb.append(']')
            sb.append('\n')

        case c: CstLineComment =>
            sb.append("//")
            if c.content.nonEmpty then
                sb.append(' ')
                sb.append(c.content)
            sb.append('\n')

        case e: CstAttributeEntry =>
            renderAttributeEntry(e, sb)

    private def renderAttributeEntry(e: CstAttributeEntry, sb: StringBuilder): Unit =
        sb.append(':')
        sb.append(e.name)
        sb.append(": ")
        sb.append(e.value)
        sb.append('\n')

    private def renderAttributeList(al: CstAttributeList, sb: StringBuilder): Unit =
        if al.positional.nonEmpty || al.named.nonEmpty || al.options.nonEmpty || al.roles.nonEmpty then
            sb.append('[')
            sb.append(renderAttrString(al))
            sb.append(']')
            sb.append('\n')

    private def renderAttrString(al: CstAttributeList): String =
        val parts =
            al.positional.map(identity) ++
            al.options.map(o => s"%$o") ++
            al.roles.map(r => s".$r") ++
            al.named.map((k, v) => s"$k=$v")
        parts.mkString(",")

    private def renderInlines(inlines: List[CstInline], sb: StringBuilder): Unit =
        inlines.foreach(renderInline(_, sb))

    private def renderInline(inline: CstInline, sb: StringBuilder): Unit = inline match
        case CstText(content) =>
            sb.append(content)
        case CstBold(content, false) =>
            sb.append("**")
            renderInlines(content, sb)
            sb.append("**")
        case CstBold(content, true) =>
            sb.append("*")
            renderInlines(content, sb)
            sb.append("*")
        case CstItalic(content) =>
            sb.append("__")
            renderInlines(content, sb)
            sb.append("__")
        case CstMono(content) =>
            sb.append("``")
            renderInlines(content, sb)
            sb.append("``")

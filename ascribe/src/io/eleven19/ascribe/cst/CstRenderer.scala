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

    /** Render a single [[CstInline]] node to a string. Mirrors the public API of `AsciiDocRenderer.renderInline`. */
    def renderInline(inline: CstInline): String =
        val sb = new StringBuilder
        renderInline(inline, sb)
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

        case ap: CstAdmonitionParagraph =>
            sb.append(ap.kind)
            sb.append(": ")
            renderInlines(ap.content, sb)
            sb.append('\n')

    private def renderAttributeEntry(e: CstAttributeEntry, sb: StringBuilder): Unit =
        if e.unset then
            sb.append(':')
            sb.append('!')
            sb.append(e.name)
            sb.append(':')
            sb.append('\n')
        else
            sb.append(':')
            sb.append(e.name)
            sb.append(':')
            if e.value.nonEmpty then
                sb.append(' ')
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
        case CstItalic(content, false) =>
            sb.append("__")
            renderInlines(content, sb)
            sb.append("__")
        case CstItalic(content, true) =>
            sb.append("_")
            renderInlines(content, sb)
            sb.append("_")
        case CstMono(content, false) =>
            sb.append("``")
            renderInlines(content, sb)
            sb.append("``")
        case CstMono(content, true) =>
            sb.append("`")
            renderInlines(content, sb)
            sb.append("`")
        case CstAttributeRef(name) =>
            sb.append('{')
            sb.append(name)
            sb.append('}')
        case CstAutolink(target) =>
            sb.append(target)
        case CstUrlMacro(target, attrList) =>
            sb.append(target)
            sb.append('[')
            renderMacroAttrList(attrList, sb)
            sb.append(']')
        case CstLinkMacro(target, attrList) =>
            sb.append("link:")
            sb.append(target)
            sb.append('[')
            renderMacroAttrList(attrList, sb)
            sb.append(']')
        case CstMailtoMacro(target, attrList) =>
            sb.append("mailto:")
            sb.append(target)
            sb.append('[')
            renderMacroAttrList(attrList, sb)
            sb.append(']')

    private def renderMacroAttrList(attrList: CstMacroAttrList, sb: StringBuilder): Unit =
        val hasStructuredAttrs = attrList.positional.nonEmpty || attrList.named.nonEmpty
        if !hasStructuredAttrs && !attrList.hasCaretShorthand then renderInlines(attrList.text, sb)
        else
            val parts  = List.newBuilder[String]
            val textSb = new StringBuilder
            renderInlines(attrList.text, textSb)
            val textStr = if attrList.hasCaretShorthand then textSb.toString + "^" else textSb.toString
            parts += textStr
            attrList.positional.foreach(p => parts += p)
            attrList.named.foreach((k, v) => parts += s"$k=$v")
            sb.append(parts.result().mkString(","))

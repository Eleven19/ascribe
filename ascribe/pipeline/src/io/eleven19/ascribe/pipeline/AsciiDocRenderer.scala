package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.{LinkAttributes, LinkOption}
import kyo.<

/** Renders AST nodes back to AsciiDoc source text.
  *
  * This is the inverse of parsing: given an AST Document, produce AsciiDoc that would parse back to equivalent AST.
  * Delimiter lengths, heading levels, and markup styles are preserved from the original AST.
  */
object AsciiDocRenderer extends Renderer[Any]:

    def render(document: Document): String < Any =
        val sb = new StringBuilder
        document.header.foreach { header =>
            sb.append("= ").append(renderInlines(header.title)).append('\n'): Unit
            header.attributes.foreach { (key, value) =>
                sb.append(':').append(key).append(':'): Unit
                if value.nonEmpty then { sb.append(' ').append(value): Unit }
                sb.append('\n'): Unit
            }
        }
        if document.header.isDefined && document.blocks.nonEmpty then { sb.append('\n'): Unit }
        renderBlocks(document.blocks, sb)
        sb.toString

    /** Render a single block to AsciiDoc source. */
    def renderBlock(block: Block): String =
        val sb = new StringBuilder
        renderBlockTo(block, sb)
        sb.toString

    private def renderBlocks(blocks: List[Block], sb: StringBuilder): Unit =
        blocks.zipWithIndex.foreach { (block, idx) =>
            if idx > 0 then { sb.append('\n'): Unit }
            renderBlockTo(block, sb)
        }

    private def renderBlockTo(block: Block, sb: StringBuilder): Unit = block match
        case Heading(level, title) =>
            sb.append("=" * (level + 1)).append(' ').append(renderInlines(title)).append('\n'): Unit

        case Section(level, title, blocks) =>
            sb.append("=" * (level + 1)).append(' ').append(renderInlines(title)).append('\n'): Unit
            if blocks.nonEmpty then
                sb.append('\n'): Unit
                renderBlocks(blocks, sb)

        case Paragraph(content) =>
            sb.append(renderInlines(content)).append('\n'): Unit

        case Listing(delimiter, content, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n').append(content).append('\n').append(delimiter).append('\n'): Unit

        case Literal(delimiter, content, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n').append(content).append('\n').append(delimiter).append('\n'): Unit

        case Comment(delimiter, content) =>
            sb.append(delimiter).append('\n').append(content).append('\n').append(delimiter).append('\n'): Unit

        case Pass(delimiter, content, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n').append(content).append('\n').append(delimiter).append('\n'): Unit

        case Sidebar(delimiter, blocks, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n'): Unit
            renderBlocks(blocks, sb)
            sb.append('\n').append(delimiter).append('\n'): Unit

        case Example(delimiter, blocks, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n'): Unit
            renderBlocks(blocks, sb)
            sb.append('\n').append(delimiter).append('\n'): Unit

        case Quote(delimiter, blocks, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n'): Unit
            renderBlocks(blocks, sb)
            sb.append('\n').append(delimiter).append('\n'): Unit

        case Open(delimiter, blocks, attrs, title) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n'): Unit
            renderBlocks(blocks, sb)
            sb.append('\n').append(delimiter).append('\n'): Unit

        case Table(rows, delimiter, format, attrs, title, _) =>
            renderBlockMetadata(attrs, title, sb)
            sb.append(delimiter).append('\n'): Unit
            rows.foreach { row =>
                format match
                    case TableFormat.PSV =>
                        val cellStrs = row.cells.map(c => renderCellContent(c))
                        sb.append(cellStrs.map("| " + _).mkString(" ")).append('\n'): Unit
                    case TableFormat.CSV =>
                        sb.append(row.cells.map(renderCellContent).mkString(",")).append('\n'): Unit
                    case TableFormat.DSV =>
                        sb.append(row.cells.map(renderCellContent).mkString(":")).append('\n'): Unit
                    case TableFormat.TSV =>
                        sb.append(row.cells.map(renderCellContent).mkString("\t")).append('\n'): Unit
            }
            sb.append(delimiter).append('\n'): Unit

        case UnorderedList(items) =>
            items.foreach { item =>
                sb.append("* ").append(renderInlines(item.content)).append('\n'): Unit
            }

        case OrderedList(items) =>
            items.foreach { item =>
                sb.append(". ").append(renderInlines(item.content)).append('\n'): Unit
            }

        case Admonition(kind, blocks) =>
            val label = kind.toString.toUpperCase
            blocks match
                case List(Paragraph(content)) =>
                    sb.append(label).append(": ").append(renderInlines(content)).append('\n'): Unit
                case _ =>
                    sb.append(label).append(":\n"): Unit
                    renderBlocks(blocks, sb)

    private def renderCellContent(cell: TableCell): String =
        cell.content match
            case CellContent.Inlines(inlines) => renderInlines(inlines)
            case CellContent.Blocks(blocks)   => blocks.map(renderBlock).mkString

    private def renderBlockMetadata(
        attrs: Option[AttributeList],
        title: Option[Title],
        sb: StringBuilder
    ): Unit =
        title.foreach { t =>
            sb.append('.').append(renderInlines(t.content)).append('\n'): Unit
        }
        attrs.foreach { al =>
            sb.append('['): Unit
            val parts = scala.collection.mutable.ListBuffer.empty[String]
            al.positional.foreach(v => parts += v.value)
            al.named.foreach((k, v) => parts += s"${k.value}=${v.value}")
            al.options.foreach(o => parts += s"%${o.value}")
            al.roles.foreach(r => parts += s".${r.value}")
            sb.append(parts.mkString(",")).append(']').append('\n'): Unit
        }

    /** Render a list of inline elements to AsciiDoc source. */
    def renderInlines(inlines: List[Inline]): String =
        inlines.map(renderInline).mkString

    /** Render a single inline element to AsciiDoc source. */
    def renderInline(inline: Inline): String = inline match
        case Text(content)                     => content
        case Bold(content)                     => s"**${renderInlines(content)}**"
        case ConstrainedBold(content)          => s"*${renderInlines(content)}*"
        case Italic(content)                   => s"__${renderInlines(content)}__"
        case Mono(content)                     => s"``${renderInlines(content)}``"
        case ConstrainedItalic(content)        => s"_${renderInlines(content)}_"
        case ConstrainedMono(content)          => s"`${renderInlines(content)}`"
        case Link(LinkVariant.Auto, target, _, _) => target
        case Link(LinkVariant.Macro(MacroKind.Link), target, text, attrs) =>
            s"link:$target[${renderLinkBracketContent(text, attrs)}]"
        case Link(LinkVariant.Macro(MacroKind.MailTo), target, text, attrs) =>
            s"mailto:$target[${renderLinkBracketContent(text, attrs)}]"
        case Link(LinkVariant.Macro(MacroKind.Url(_)), target, text, attrs) =>
            s"$target[${renderLinkBracketContent(text, attrs)}]"

    private def renderLinkBracketContent(text: List[Inline], attrs: LinkAttributes): String =
        if attrs == LinkAttributes.empty then renderInlines(text)
        else
            val parts = List.newBuilder[String]
            if text.nonEmpty then parts += renderInlines(text)
            attrs.id.foreach(id => parts += s"id=$id")
            attrs.roles.foreach(r => parts += s"role=$r")
            attrs.title.foreach(t => parts += s"title=$t")
            attrs.window.foreach(w => parts += s"window=$w")
            if attrs.options.nonEmpty then
                val optStrs = attrs.options.map {
                    case LinkOption.NoFollow => "nofollow"
                    case LinkOption.NoOpener => "noopener"
                }
                parts += s"opts=${optStrs.mkString(",")}"
            parts.result().mkString(",")

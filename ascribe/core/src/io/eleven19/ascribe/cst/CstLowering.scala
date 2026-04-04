package io.eleven19.ascribe.cst

import io.eleven19.ascribe.ast.*
import io.eleven19.ascribe.ast.AttributeList.{AttributeName, AttributeValue, OptionName, RoleName}
import io.eleven19.ascribe.ast.{LinkAttributes, LinkOption, WindowTarget, CssRole, ElementId}

opaque type AttributeMap = Map[String, String]

object AttributeMap:

    val builtIns: AttributeMap =
        Map("empty" -> "", "sp" -> " ", "nbsp" -> "\u00A0", "zwsp" -> "\u200B")

    def fromHeader(entries: List[CstAttributeEntry]): AttributeMap =
        entries
            .filterNot(_.unset)
            .foldLeft(builtIns)((m, e) => m + (e.name -> e.value))

    extension (m: AttributeMap)
        def set(name: String, value: String): AttributeMap = m + (name -> value)
        def unset(name: String): AttributeMap              = m - name
        def resolve(name: String): String                  = m.getOrElse(name, s"{$name}")

/** Lowers a `CstDocument` to the AST `Document` representation.
  *
  * Transformations applied:
  *   - `CstBlankLine` and `CstLineComment` are dropped
  *   - `CstDelimitedBlock(Comment, ...)` is dropped
  *   - `CstParagraphLine` sequences are flattened into `Paragraph`
  *   - `CstBold(constrained=true)` → `ConstrainedBold`, `false` → `Bold`
  *   - `CstItalic(constrained=true)` → `ConstrainedItalic`, `false` → `Italic`
  *   - `CstMono(constrained=true)` → `ConstrainedMono`, `false` → `Mono`
  *   - `CstDelimitedBlock(kind, ...)` → corresponding AST block type
  *   - `CstList(Unordered/Ordered, ...)` → `UnorderedList`/`OrderedList`
  *   - `CstTable` → `Table`
  *   - Flat headings restructured into `Section` hierarchy via `restructure`
  *   - Header `CstAttributeEntry` values seeded into attribute map
  *   - Body-level `CstAttributeEntry` updates attribute map (no AST representation)
  *   - `CstAttributeRef(name)` resolved via attribute map (falls back to `{name}`)
  *   - Unresolved `CstInclude` nodes cause an error
  */
object CstLowering:

    def toAst(cst: CstDocument): Document =
        var attrs = AttributeMap.fromHeader(cst.header.toList.flatMap(_.attributes))

        def lowerInline(inline: CstInline): Inline = inline match
            case CstText(content)          => Text(content)(inline.span)
            case CstBold(content, false)   => Bold(lowerInlines(content))(inline.span)
            case CstBold(content, true)    => ConstrainedBold(lowerInlines(content))(inline.span)
            case CstItalic(content, false) => Italic(lowerInlines(content))(inline.span)
            case CstItalic(content, true)  => ConstrainedItalic(lowerInlines(content))(inline.span)
            case CstMono(content, false)   => Mono(lowerInlines(content))(inline.span)
            case CstMono(content, true)    => ConstrainedMono(lowerInlines(content))(inline.span)
            case CstAttributeRef(name)     => Text(attrs.resolve(name))(inline.span)
            case CstAutolink(target) =>
                Link(LinkVariant.Auto, target, Nil, LinkAttributes.empty)(inline.span)
            case CstUrlMacro(target, attrList) =>
                val scheme = target.indexOf("://") match
                    case -1  => ""
                    case idx => target.substring(0, idx)
                Link(
                    LinkVariant.Macro(MacroKind.Url(scheme)),
                    target,
                    lowerInlines(attrList.text),
                    lowerMacroAttrs(attrList)
                )(inline.span)
            case CstLinkMacro(target, attrList) =>
                Link(LinkVariant.Macro(MacroKind.Link), target, lowerInlines(attrList.text), lowerMacroAttrs(attrList))(
                    inline.span
                )
            case CstMailtoMacro(target, attrList) =>
                Link(
                    LinkVariant.Macro(MacroKind.MailTo),
                    target,
                    lowerInlines(attrList.text),
                    lowerMacroAttrs(attrList)
                )(inline.span)

        def lowerMacroAttrs(attrList: CstMacroAttrList): LinkAttributes =
            var la = LinkAttributes.empty
            if attrList.hasCaretShorthand then
                la = la.copy(window = Some(WindowTarget.Blank), options = la.options + LinkOption.NoOpener)
            attrList.named.foreach { (key, value) =>
                key match
                    case "window" =>
                        val wt = WindowTarget(value)
                        la = la.copy(window = Some(wt))
                        if wt == WindowTarget.Blank then la = la.copy(options = la.options + LinkOption.NoOpener)
                    case "role" =>
                        la = la.copy(roles = la.roles ++ value.split("\\s+").filter(_.nonEmpty).map(CssRole(_)))
                    case "id"    => la = la.copy(id = Some(ElementId(value)))
                    case "title" => la = la.copy(title = Some(value))
                    case "opts" =>
                        value.split("[,\\s]+").foreach {
                            case "nofollow" => la = la.copy(options = la.options + LinkOption.NoFollow)
                            case "noopener" => la = la.copy(options = la.options + LinkOption.NoOpener)
                            case _          => ()
                        }
                    case _ => ()
            }
            la

        def lowerInlines(inlines: List[CstInline]): List[Inline] = inlines.map(lowerInline)

        def lowerBlockTitle(bt: CstBlockTitle): Title =
            Title(lowerInlines(bt.content))(bt.span)

        def lowerBlock(block: CstBlock): Option[Block] = block match
            case _: CstLineComment    => None
            case _: CstAttributeEntry => None
            case CstAdmonitionParagraph(kind, content) =>
                val k = kind match
                    case "NOTE"      => AdmonitionKind.Note
                    case "TIP"       => AdmonitionKind.Tip
                    case "IMPORTANT" => AdmonitionKind.Important
                    case "CAUTION"   => AdmonitionKind.Caution
                    case "WARNING"   => AdmonitionKind.Warning
                    case other       => sys.error(s"Unknown admonition kind: $other")
                Some(Admonition(k, List(Paragraph(lowerInlines(content))(block.span)))(block.span))
            case CstInclude(target, _) =>
                sys.error(s"Unresolved CstInclude: $target — resolve includes before lowering")

            case CstHeading(level, _, title) =>
                Some(Heading(level, lowerInlines(title))(block.span))

            case p: CstParagraph =>
                val content = p.lines.flatMap(line => lowerInlines(line.content))
                Some(Paragraph(content, p.attributes.map(lowerAttrList), p.title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Comment, _, _, _, _) =>
                None

            case CstDelimitedBlock(DelimitedBlockKind.Listing, delim, CstVerbatimContent(raw), attrs2, title) =>
                Some(Listing(delim, raw, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Literal, delim, CstVerbatimContent(raw), attrs2, title) =>
                Some(Literal(delim, raw, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Pass, delim, CstVerbatimContent(raw), attrs2, title) =>
                Some(Pass(delim, raw, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Sidebar, delim, CstNestedContent(children), attrs2, title) =>
                val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                Some(Sidebar(delim, blocks, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Example, delim, CstNestedContent(children), attrs2, title) =>
                val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                Some(Example(delim, blocks, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Quote, delim, CstNestedContent(children), attrs2, title) =>
                val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                Some(Quote(delim, blocks, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(DelimitedBlockKind.Open, delim, CstNestedContent(children), attrs2, title) =>
                val blocks = children.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                Some(Open(delim, blocks, attrs2.map(lowerAttrList), title.map(lowerBlockTitle))(block.span))

            case CstDelimitedBlock(_, delim, _, _, _) =>
                sys.error(s"Unexpected CstDelimitedBlock combination: delim=$delim")

            case CstList(ListVariant.Unordered, items) =>
                Some(UnorderedList(items.map(lowerListItem))(block.span))

            case CstList(ListVariant.Ordered, items) =>
                Some(OrderedList(items.map(lowerListItem))(block.span))

            case CstTable(rows, delim, format, attrs2, title, hasBlank) =>
                Some(
                    Table(
                        rows.map(lowerTableRow),
                        delim,
                        format,
                        attrs2.map(lowerAttrList),
                        title.map(lowerBlockTitle),
                        hasBlank
                    )(block.span)
                )

        def lowerListItem(item: CstListItem): ListItem =
            ListItem(lowerInlines(item.content))(item.span)

        def lowerTableRow(row: CstTableRow): TableRow =
            TableRow(row.cells.map(lowerTableCell))(row.span)

        def lowerTableCell(cell: CstTableCell): TableCell =
            val content = cell.content match
                case CstCellInlines(inlines) => CellContent.Inlines(lowerInlines(inlines))
                case CstCellBlocks(topLevels) =>
                    val blocks = topLevels.collect { case b: CstBlock => b }.flatMap(lowerBlock)
                    CellContent.Blocks(blocks)
            TableCell(
                content,
                cell.style.flatMap(s => s.headOption.map(CellSpecifier.StyleOperator(_))),
                cell.colSpan.map(CellSpecifier.ColSpanFactor(_)),
                cell.rowSpan.map(CellSpecifier.RowSpanFactor(_)),
                cell.dupFactor.map(CellSpecifier.DupFactor(_))
            )(cell.span)

        val blocks = cst.content
            .collect { case b: CstBlock => b }
            .flatMap {
                case CstAttributeEntry(name, value, false) => attrs = attrs.set(name, value); None
                case CstAttributeEntry(name, _, true)      => attrs = attrs.unset(name); None
                case other                                 => lowerBlock(other)
            }
        val header = cst.header.map(lowerHeader(_, lowerInlines))
        Document(header, restructure(blocks))(cst.span)

    private def lowerHeader(h: CstDocumentHeader, lowerInlines: List[CstInline] => List[Inline]): DocumentHeader =
        DocumentHeader(
            title = lowerInlines(h.title.title),
            attributes = h.attributes.filterNot(_.unset).map(e => (e.name, e.value))
        )(h.span)

    private def lowerAttrList(al: CstAttributeList): AttributeList =
        AttributeList(
            positional = al.positional.map(AttributeValue(_)),
            named = al.named.map((k, v) => (AttributeName(k), AttributeValue(v))),
            options = al.options.map(OptionName(_)),
            roles = al.roles.map(RoleName(_))
        )(al.span)

    private def restructure(blocks: List[Block]): List[Block] =
        blocks match
            case Nil => Nil
            case (h: Heading) :: rest if h.level >= 2 =>
                val sectionLevel = h.level - 1
                val (nested, remaining) = rest.span {
                    case hh: Heading if hh.level <= h.level => false
                    case _                                  => true
                }
                val sectionSpan = nested.lastOption.map(_.span).getOrElse(h.span)
                val section = Section(sectionLevel, h.title, restructure(nested))(
                    Span(h.span.start, sectionSpan.end)
                )
                section :: restructure(remaining)
            case head :: rest =>
                head :: restructure(rest)

package io.eleven19.ascribe.ast

/** ScalaTags-inspired DSL for constructing AST nodes without position boilerplate.
  *
  * All builders default to `Span.unknown`. Import `given` to enable implicit `String → Text` conversion.
  *
  * {{{
  * import io.eleven19.ascribe.ast.dsl.{*, given}
  *
  * val doc = document(
  *   section(1, List(text("Title")),
  *     paragraph("Hello ", bold("world"), "!"),
  *     unorderedList(listItem("item one"), listItem("item two"))
  *   )
  * )
  * }}}
  */
object dsl:
    private val u = Span.unknown

    // --- Implicit String → Inline conversion ---
    given Conversion[String, Text] = s => Text(s)(u)

    // --- Inlines ---
    def text(s: String): Text                              = Text(s)(u)
    def bold(inlines: Inline*): Bold                       = Bold(inlines.toList)(u)
    def constrainedBold(inlines: Inline*): ConstrainedBold = ConstrainedBold(inlines.toList)(u)
    def italic(inlines: Inline*): Italic                   = Italic(inlines.toList)(u)
    def mono(inlines: Inline*): Mono                       = Mono(inlines.toList)(u)

    def autoLink(target: String): Link =
        Link(LinkVariant.Auto, target, Nil)(u)

    def urlLink(scheme: String, target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Url(scheme)), target, text.toList)(u)

    def link(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.Link), target, text.toList)(u)

    def mailtoLink(target: String, text: Inline*): Link =
        Link(LinkVariant.Macro(MacroKind.MailTo), target, text.toList)(u)

    // --- Blocks ---
    def paragraph(inlines: Inline*): Paragraph = Paragraph(inlines.toList)(u)

    def heading(level: Int, inlines: Inline*): Heading =
        Heading(level, inlines.toList)(u)

    def section(level: Int, title: scala.List[Inline], blocks: Block*): Section =
        Section(level, title, blocks.toList)(u)

    def listingBlock(delimiter: String, content: String): Listing =
        Listing(delimiter, content)(u)

    def sidebarBlock(delimiter: String, blocks: Block*): Sidebar =
        Sidebar(delimiter, blocks.toList)(u)

    // --- Attribute lists and block titles ---
    def attributeList(
        named: (String, String)*
    ): AttributeList = AttributeList(
        positional = Nil,
        named = named.map((k, v) => (AttributeList.AttributeName(k), AttributeList.AttributeValue(v))).toMap,
        options = Nil,
        roles = Nil
    )(u)

    def attributeList(
        options: List[String],
        named: Map[String, String] = Map.empty
    ): AttributeList = AttributeList(
        positional = Nil,
        named = named.map((k, v) => (AttributeList.AttributeName(k), AttributeList.AttributeValue(v))),
        options = options.map(AttributeList.OptionName(_)),
        roles = Nil
    )(u)

    def blockTitle(inlines: Inline*): Title = Title(inlines.toList)(u)

    // --- Tables ---
    def tableBlock(rows: TableRow*): Table = Table(rows.toList, "|===")(u)

    def tableBlock(attrs: AttributeList, rows: TableRow*): Table =
        Table(rows.toList, "|===", TableFormat.PSV, Some(attrs))(u)

    def tableBlock(title: Title, attrs: AttributeList, rows: TableRow*): Table =
        Table(rows.toList, "|===", TableFormat.PSV, Some(attrs), Some(title))(u)

    def tableRow(cells: TableCell*): TableRow  = TableRow(cells.toList)(u)
    def tableCell(inlines: Inline*): TableCell = TableCell(CellContent.Inlines(inlines.toList))(u)
    def blockCell(blocks: Block*): TableCell   = TableCell(CellContent.Blocks(blocks.toList))(u)

    // --- Admonitions ---
    def admonition(kind: AdmonitionKind, blocks: Block*): Admonition =
        Admonition(kind, blocks.toList)(u)

    // --- Lists ---
    def listItem(inlines: Inline*): ListItem           = ListItem(inlines.toList)(u)
    def unorderedList(items: ListItem*): UnorderedList = UnorderedList(items.toList)(u)
    def orderedList(items: ListItem*): OrderedList     = OrderedList(items.toList)(u)

    // --- Document ---
    def documentHeader(title: Inline*): DocumentHeader =
        DocumentHeader(title.toList, Nil)(u)

    def documentHeader(title: scala.List[Inline], attrs: scala.List[(String, String)]): DocumentHeader =
        DocumentHeader(title, attrs)(u)

    def document(blocks: Block*): Document =
        Document(blocks.toList)(u)

    def document(header: DocumentHeader, blocks: Block*): Document =
        Document(Some(header), blocks.toList)(u)

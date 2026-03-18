package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk

/** DSL for constructing ASG nodes with implicit location threading.
  *
  * Import `given` to get the default `noLoc` location. Override with your own `given Location` for positioned
  * construction.
  *
  * {{{
  * import io.eleven19.ascribe.asg.dsl.{*, given}
  *
  * // Test construction — no location noise
  * val doc = document(
  *   paragraph(text("Hello"), span("strong", "constrained", text("world")))
  * )
  *
  * // With explicit location
  * given Location = loc(1, 1, 3, 9)
  * val located = document(paragraph(text("Hello")))
  * }}}
  */
object dsl:
    // --- Location helpers ---
    val noLoc: Location = Location(Position(0, 0), Position(0, 0))

    def loc(startLine: Int, startCol: Int, endLine: Int, endCol: Int): Location =
        Location(Position(startLine, startCol), Position(endLine, endCol))

    // --- Default given Location ---
    given Location = noLoc

    // --- Leaf inlines ---
    def text(value: String)(using l: Location): Text       = Text(value, l)
    def charRef(value: String)(using l: Location): CharRef = CharRef(value, l)
    def raw(value: String)(using l: Location): Raw         = Raw(value, l)

    // --- Parent inlines ---
    def span(variant: String, form: String, inlines: Inline*)(using l: Location): Span =
        Span(variant, form, Chunk.from(inlines), l)

    def ref(variant: String, target: String, inlines: Inline*)(using l: Location): Ref =
        Ref(variant, target, Chunk.from(inlines), l)

    // --- Blocks ---
    def paragraph(inlines: Inline*)(using l: Location): Paragraph =
        Paragraph(inlines = Chunk.from(inlines), location = l)

    def heading(level: Int, inlines: Inline*)(using l: Location): Heading =
        Heading(level = level, title = Some(Chunk.from(inlines)), location = l)

    def section(level: Int, title: Seq[Inline], blocks: Block*)(using l: Location): Section =
        Section(level = level, title = Some(Chunk.from(title)), blocks = Chunk.from(blocks), location = l)

    def listing(inlines: Inline*)(using l: Location): Listing =
        Listing(inlines = Chunk.from(inlines), location = l)

    def sidebar(form: String, delimiter: String, blocks: Block*)(using l: Location): Sidebar =
        Sidebar(form = form, delimiter = delimiter, blocks = Chunk.from(blocks), location = l)

    // --- Tables ---
    def table(rows: Block*)(using l: Location): Table =
        Table(rows = Chunk.from(rows), location = l)

    def tableRow(cells: Block*)(using l: Location): TableRow =
        TableRow(cells = Chunk.from(cells), location = l)
    def tr(cells: Block*)(using l: Location): TableRow = tableRow(cells*)

    def tableCell(inlines: Inline*)(using l: Location): TableCell =
        TableCell(inlines = Chunk.from(inlines), location = l)
    def tc(inlines: Inline*)(using l: Location): TableCell = tableCell(inlines*)

    // --- Lists ---
    def list(variant: String, marker: String, items: Block*)(using l: Location): List =
        List(variant = variant, marker = marker, items = Chunk.from(items), location = l)

    def listItem(marker: String, principal: Inline*)(using l: Location): ListItem =
        ListItem(marker = marker, principal = Chunk.from(principal), location = l)

    // --- Document ---
    def document(blocks: Block*)(using l: Location): Document =
        Document(blocks = Chunk.from(blocks), location = l)

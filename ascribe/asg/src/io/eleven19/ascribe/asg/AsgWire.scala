package io.eleven19.ascribe.asg

import kyo.{Chunk, Structure}
import kyo.Structure.Value

private[asg] object AsgWire:

    def toValue(node: Node): Value =
        node match
            case node: Document => document(node)
            case node: Block    => block(node)
            case node: Inline   => inlineValue(node)

    def fromValue(value: Value): Either[String, Node] =
        value match
            case Value.Record(fields) =>
                fieldString(fields, "name").flatMap { name =>
                    if name == "document" then documentFrom(fields)
                    else if blockNames.contains(name) then blockFrom(name, fields)
                    else if inlineNames.contains(name) then inlineFrom(name, fields)
                    else Left(s"Unknown ASG node name: $name")
                }
            case other => Left(s"Expected ASG node object, got $other")

    private val blockNames: Set[String] =
        Set(
            "section",
            "heading",
            "paragraph",
            "listing",
            "literal",
            "pass",
            "stem",
            "verse",
            "sidebar",
            "example",
            "admonition",
            "open",
            "quote",
            "list",
            "dlist",
            "listItem",
            "dlistItem",
            "break",
            "table",
            "tableRow",
            "tableCell",
            "audio",
            "video",
            "image",
            "toc"
        )

    private val inlineNames: Set[String] =
        Set("span", "ref", "text", "charref", "raw")

    private def blockName(block: Block): String =
        block match
            case _: Section    => "section"
            case _: Heading    => "heading"
            case _: Paragraph  => "paragraph"
            case _: Listing    => "listing"
            case _: Literal    => "literal"
            case _: Pass       => "pass"
            case _: Stem       => "stem"
            case _: Verse      => "verse"
            case _: Sidebar    => "sidebar"
            case _: Example    => "example"
            case _: Admonition => "admonition"
            case _: Open       => "open"
            case _: Quote      => "quote"
            case _: List       => "list"
            case _: DList      => "dlist"
            case _: ListItem   => "listItem"
            case _: DListItem  => "dlistItem"
            case _: Break      => "break"
            case _: Table      => "table"
            case _: TableRow   => "tableRow"
            case _: TableCell  => "tableCell"
            case _: Audio      => "audio"
            case _: Video      => "video"
            case _: Image      => "image"
            case _: Toc        => "toc"

    private def inlineName(inline: Inline): String =
        inline match
            case _: Span    => "span"
            case _: Ref     => "ref"
            case _: Text    => "text"
            case _: CharRef => "charref"
            case _: Raw     => "raw"

    private def record(fields: Chunk[(String, Value)]): Value =
        Value.Record(fields)

    private def arr(values: Chunk[Value]): Value =
        Value.Sequence(values)

    private def str(value: String): Value =
        Value.Str(value)

    private def int(value: Int): Value =
        Value.Integer(value.toLong)

    private def bool(value: Boolean): Value =
        Value.Bool(value)

    private def field(name: String, value: Value): Chunk[(String, Value)] =
        Chunk((name, value))

    private def opt[A](name: String, value: Option[A])(f: A => Value): Chunk[(String, Value)] =
        value match
            case Some(v) => field(name, f(v))
            case None    => Chunk.empty

    private def nonEmpty[A](name: String, value: Chunk[A])(f: A => Value): Chunk[(String, Value)] =
        if value.isEmpty then Chunk.empty
        else field(name, arr(value.map(f)))

    private def requiredChunk[A](name: String, value: Chunk[A])(f: A => Value): Chunk[(String, Value)] =
        field(name, arr(value.map(f)))

    private def nonEmptyMap[A](name: String, value: Map[String, A], f: A => Value): Chunk[(String, Value)] =
        if value.isEmpty then Chunk.empty
        else field(name, Value.Record(Chunk.from(value.toSeq.map((k, v) => k -> f(v)))))

    private def optionalMap[A](name: String, value: Option[Map[String, A]], f: A => Value): Chunk[(String, Value)] =
        value match
            case Some(values) => field(name, Value.Record(Chunk.from(values.toSeq.map((k, v) => k -> f(v)))))
            case None         => Chunk.empty

    private def position(value: Position): Value =
        record(
            field("line", int(value.line)) ++
                field("col", int(value.col)) ++
                opt("file", value.file)(files => arr(files.map(str)))
        )

    private def location(value: Location): Value =
        arr(Chunk(position(value.start), position(value.end)))

    private def metadata(value: BlockMetadata): Value =
        record(
            nonEmptyMap("attributes", value.attributes, str) ++
                nonEmpty("options", value.options)(str) ++
                nonEmpty("roles", value.roles)(str) ++
                opt("location", value.location)(location)
        )

    private def header(value: Header): Value =
        record(
            opt("title", value.title)(inlines) ++
                nonEmpty("authors", value.authors)(author) ++
                opt("location", value.location)(location)
        )

    private def author(value: Author): Value =
        record(
            opt("fullname", value.fullname)(str) ++
                opt("initials", value.initials)(str) ++
                opt("firstname", value.firstname)(str) ++
                opt("middlename", value.middlename)(str) ++
                opt("lastname", value.lastname)(str) ++
                opt("address", value.address)(str)
        )

    private def attributes(value: Map[String, Option[String]]): Value =
        Value.Record(
            Chunk.from(value.toSeq.map {
                case (key, Some(v)) => key -> str(v)
                case (key, None)    => key -> Value.Null
            })
        )

    private def columnSpec(value: ColumnSpec): Value =
        record(
            opt("width", value.width)(int) ++
                opt("halign", value.halign)(v => str(halignName(v))) ++
                opt("valign", value.valign)(v => str(valignName(v))) ++
                opt("style", value.style)(v => str(cellStyleName(v)))
        )

    private def inlines(value: Chunk[Inline]): Value =
        arr(value.map(inlineValue))

    private def blocks(value: Chunk[Block]): Value =
        arr(value.map(block))

    private def blockBase(name: String, value: Block): Chunk[(String, Value)] =
        field("name", str(name)) ++
            opt("id", value.id)(str) ++
            opt("title", value.title)(inlines) ++
            opt("reftext", value.reftext)(inlines) ++
            opt("metadata", value.metadata)(metadata)

    private def blockType(value: Block): Chunk[(String, Value)] =
        field("type", str(value.nodeType))

    private def nodeType(value: Node): Chunk[(String, Value)] =
        field("type", str(value.nodeType))

    private def document(value: Document): Value =
        record(
            field("name", str("document")) ++
                optionalMap("attributes", value.attributes, {
                    case Some(v) => str(v)
                    case None    => Value.Null
                }) ++
                opt("header", value.header)(header) ++
                nonEmpty("blocks", value.blocks)(block) ++
                field("location", location(value.location)) ++
                nodeType(value)
        )

    private def leafBlock(
        value: Block,
        form: Option[String],
        delimiter: Option[String],
        children: Chunk[Inline]
    ): Value =
        record(
            blockBase(blockName(value), value) ++
                opt("form", form)(str) ++
                opt("delimiter", delimiter)(str) ++
                nonEmpty("inlines", children)(inlineValue) ++
                field("location", location(value.location)) ++
                blockType(value)
        )

    private def parentBlock(value: Block, form: String, delimiter: String, children: Chunk[Block]): Chunk[(String, Value)] =
        blockBase(blockName(value), value) ++
            field("form", str(form)) ++
            field("delimiter", str(delimiter)) ++
            nonEmpty("blocks", children)(block) ++
            field("location", location(value.location)) ++
            blockType(value)

    private def block(value: Block): Value =
        value match
            case n: Section =>
                record(blockBase(blockName(n), n) ++ field("level", int(n.level)) ++ nonEmpty("blocks", n.blocks)(block) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Heading =>
                record(blockBase(blockName(n), n) ++ field("level", int(n.level)) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Paragraph =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Listing =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Literal =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Pass =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Stem =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Verse =>
                leafBlock(n, n.form, n.delimiter, n.inlines)
            case n: Sidebar =>
                record(parentBlock(n, n.form, n.delimiter, n.blocks))
            case n: Example =>
                record(parentBlock(n, n.form, n.delimiter, n.blocks))
            case n: Admonition =>
                record(parentBlock(n, n.form, n.delimiter, n.blocks).patch(5, field("variant", str(n.variant)), 0))
            case n: Open =>
                record(parentBlock(n, n.form, n.delimiter, n.blocks))
            case n: Quote =>
                record(parentBlock(n, n.form, n.delimiter, n.blocks))
            case n: List =>
                record(blockBase(blockName(n), n) ++ field("variant", str(n.variant)) ++ field("marker", str(n.marker)) ++ requiredChunk("items", n.items)(block) ++ field("location", location(n.location)) ++ blockType(n))
            case n: DList =>
                record(blockBase(blockName(n), n) ++ field("marker", str(n.marker)) ++ requiredChunk("items", n.items)(block) ++ field("location", location(n.location)) ++ blockType(n))
            case n: ListItem =>
                record(blockBase(blockName(n), n) ++ field("marker", str(n.marker)) ++ requiredChunk("principal", n.principal)(inlineValue) ++ nonEmpty("blocks", n.blocks)(block) ++ field("location", location(n.location)) ++ blockType(n))
            case n: DListItem =>
                record(
                    blockBase(blockName(n), n) ++
                        field("marker", str(n.marker)) ++
                        requiredChunk("terms", n.terms)(term => arr(term.map(inlineValue))) ++
                        opt("principal", n.principal)(inlines) ++
                        nonEmpty("blocks", n.blocks)(block) ++
                        field("location", location(n.location)) ++
                        blockType(n)
                )
            case n: Break =>
                record(blockBase(blockName(n), n) ++ field("variant", str(n.variant)) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Table =>
                record(
                    blockBase(blockName(n), n) ++
                        field("form", str(n.form)) ++
                        field("delimiter", str(n.delimiter)) ++
                        opt("columns", n.columns)(cols => arr(cols.map(columnSpec))) ++
                        opt("header", n.header)(blocks) ++
                        requiredChunk("rows", n.rows)(block) ++
                        opt("footer", n.footer)(blocks) ++
                        opt("frame", n.frame)(str) ++
                        opt("grid", n.grid)(str) ++
                        opt("stripes", n.stripes)(str) ++
                        field("location", location(n.location)) ++
                        blockType(n)
                )
            case n: TableRow =>
                record(blockBase(blockName(n), n) ++ requiredChunk("cells", n.cells)(block) ++ field("location", location(n.location)) ++ blockType(n))
            case n: TableCell =>
                record(
                        blockBase(blockName(n), n) ++
                        opt("style", n.style)(v => str(cellStyleName(v))) ++
                        opt("colSpan", n.colSpan)(v => int(v.value)) ++
                        opt("rowSpan", n.rowSpan)(v => int(v.value)) ++
                        opt("dupCount", n.dupCount)(v => int(v.value)) ++
                        nonEmpty("inlines", n.inlines)(inlineValue) ++
                        nonEmpty("blocks", n.blocks)(block) ++
                        field("location", location(n.location)) ++
                        blockType(n)
                )
            case n: Audio =>
                record(blockBase(blockName(n), n) ++ field("form", str(n.form)) ++ opt("target", n.target)(str) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Video =>
                record(blockBase(blockName(n), n) ++ field("form", str(n.form)) ++ opt("target", n.target)(str) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Image =>
                record(blockBase(blockName(n), n) ++ field("form", str(n.form)) ++ opt("target", n.target)(str) ++ field("location", location(n.location)) ++ blockType(n))
            case n: Toc =>
                record(blockBase(blockName(n), n) ++ field("form", str(n.form)) ++ opt("target", n.target)(str) ++ field("location", location(n.location)) ++ blockType(n))

    private def inlineValue(value: Inline): Value =
        value match
            case n: Span =>
                record(field("name", str("span")) ++ field("variant", str(n.variant)) ++ field("form", str(n.form)) ++ nonEmpty("inlines", n.inlines)(inlineValue) ++ field("location", location(n.location)) ++ nodeType(n))
            case n: Ref =>
                record(field("name", str("ref")) ++ field("variant", str(n.variant)) ++ field("target", str(n.target)) ++ nonEmpty("inlines", n.inlines)(inlineValue) ++ field("location", location(n.location)) ++ nodeType(n))
            case n: Text =>
                record(field("name", str("text")) ++ field("value", str(n.value)) ++ field("location", location(n.location)) ++ nodeType(n))
            case n: CharRef =>
                record(field("name", str("charref")) ++ field("value", str(n.value)) ++ field("location", location(n.location)) ++ nodeType(n))
            case n: Raw =>
                record(field("name", str("raw")) ++ field("value", str(n.value)) ++ field("location", location(n.location)) ++ nodeType(n))

    private def fieldValue(fields: Chunk[(String, Value)], name: String): Either[String, Value] =
        fields.find(_._1 == name).map(_._2).toRight(s"Missing field: $name")

    private def halignName(value: HAlign): String =
        value match
            case HAlign.Left   => "left"
            case HAlign.Center => "center"
            case HAlign.Right  => "right"

    private def valignName(value: VAlign): String =
        value match
            case VAlign.Top    => "top"
            case VAlign.Middle => "middle"
            case VAlign.Bottom => "bottom"

    private def cellStyleName(value: CellStyle): String =
        value match
            case CellStyle.Default   => "default"
            case CellStyle.AsciiDoc  => "asciiDoc"
            case CellStyle.Emphasis  => "emphasis"
            case CellStyle.Header    => "header"
            case CellStyle.Literal   => "literal"
            case CellStyle.Monospace => "monospace"
            case CellStyle.Strong    => "strong"

    private def fieldString(fields: Chunk[(String, Value)], name: String): Either[String, String] =
        fieldValue(fields, name).flatMap {
            case Value.Str(value) => Right(value)
            case other           => Left(s"Expected string field $name, got $other")
        }

    private def fieldInt(fields: Chunk[(String, Value)], name: String): Either[String, Int] =
        fieldValue(fields, name).flatMap {
            case Value.Integer(value) => Right(value.toInt)
            case other                => Left(s"Expected integer field $name, got $other")
        }

    private def optional[A](fields: Chunk[(String, Value)], name: String)(f: Value => Either[String, A]): Either[String, Option[A]] =
        fields.find(_._1 == name) match
            case Some((_, Value.Null)) => Right(None)
            case Some((_, value))      => f(value).map(Some(_))
            case None                  => Right(None)

    private def optionalString(fields: Chunk[(String, Value)], name: String): Either[String, Option[String]] =
        optional(fields, name) {
            case Value.Str(value) => Right(value)
            case other           => Left(s"Expected string field $name, got $other")
        }

    private def optionalInt(fields: Chunk[(String, Value)], name: String): Either[String, Option[Int]] =
        optional(fields, name) {
            case Value.Integer(value) => Right(value.toInt)
            case other                => Left(s"Expected integer field $name, got $other")
        }

    private def chunkOf[A](value: Value)(f: Value => Either[String, A]): Either[String, Chunk[A]] =
        value match
            case Value.Sequence(values) =>
                values.foldLeft[Either[String, Chunk[A]]](Right(Chunk.empty)) { (acc, next) =>
                    for
                        chunk <- acc
                        item  <- f(next)
                    yield chunk.append(item)
                }
            case other => Left(s"Expected array, got $other")

    private def optionalChunk[A](fields: Chunk[(String, Value)], name: String)(f: Value => Either[String, A]): Either[String, Chunk[A]] =
        optional(fields, name)(chunkOf(_)(f)).map(_.getOrElse(Chunk.empty))

    private def requiredChunkFrom[A](fields: Chunk[(String, Value)], name: String)(f: Value => Either[String, A]): Either[String, Chunk[A]] =
        fieldValue(fields, name).flatMap(chunkOf(_)(f))

    private def positionFrom(value: Value): Either[String, Position] =
        value match
            case Value.Record(fields) =>
                for
                    line <- fieldInt(fields, "line")
                    col  <- fieldInt(fields, "col")
                    file <- optionalChunk(fields, "file") {
                        case Value.Str(value) => Right(value)
                        case other            => Left(s"Expected file path string, got $other")
                    }
                yield Position(line, col, Option.when(file.nonEmpty)(file))
            case other => Left(s"Expected position object, got $other")

    private def locationFrom(value: Value): Either[String, Location] =
        value match
            case Value.Sequence(values) if values.size == 2 =>
                for
                    start <- positionFrom(values(0))
                    end   <- positionFrom(values(1))
                yield Location(start, end)
            case other => Left(s"Expected two-element location array, got $other")

    private def metadataFrom(value: Value): Either[String, BlockMetadata] =
        value match
            case Value.Record(fields) =>
                for
                    attributes <- optionalStringMap(fields, "attributes")
                    options    <- optionalChunk(fields, "options")(stringFrom)
                    roles      <- optionalChunk(fields, "roles")(stringFrom)
                    loc        <- optional(fields, "location")(locationFrom)
                yield BlockMetadata(attributes, options, roles, loc)
            case other => Left(s"Expected metadata object, got $other")

    private def optionalStringMap(fields: Chunk[(String, Value)], name: String): Either[String, Map[String, String]] =
        fields.find(_._1 == name) match
            case Some((_, Value.Record(values))) =>
                values.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
                    case (acc, (key, Value.Str(value))) => acc.map(_ + (key -> value))
                    case (_, (key, other))              => Left(s"Expected string value for map key $key, got $other")
                }
            case Some((_, other)) => Left(s"Expected object field $name, got $other")
            case None             => Right(Map.empty)

    private def attributesFrom(fields: Chunk[(String, Value)], name: String): Either[String, Option[Map[String, Option[String]]]] =
        fields.find(_._1 == name) match
            case Some((_, Value.Record(values))) =>
                values.foldLeft[Either[String, Map[String, Option[String]]]](Right(Map.empty)) {
                    case (acc, (key, Value.Str(value))) => acc.map(_ + (key -> Some(value)))
                    case (acc, (key, Value.Null))       => acc.map(_ + (key -> None))
                    case (_, (key, other))              => Left(s"Expected string or null for attribute $key, got $other")
                }.map(Some(_))
            case Some((_, other)) => Left(s"Expected object field $name, got $other")
            case None             => Right(None)

    private def stringFrom(value: Value): Either[String, String] =
        value match
            case Value.Str(value) => Right(value)
            case other            => Left(s"Expected string, got $other")

    private def blockBaseFrom(fields: Chunk[(String, Value)]): Either[String, (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata])] =
        for
            id       <- optionalString(fields, "id")
            title    <- optional(fields, "title")(chunkOf(_)(inlineFrom))
            reftext  <- optional(fields, "reftext")(chunkOf(_)(inlineFrom))
            metadata <- optional(fields, "metadata")(metadataFrom)
        yield (id, title, reftext, metadata)

    private def headerFrom(value: Value): Either[String, Header] =
        value match
            case Value.Record(fields) =>
                for
                    title   <- optional(fields, "title")(chunkOf(_)(inlineFrom))
                    authors <- optionalChunk(fields, "authors")(authorFrom)
                    loc     <- optional(fields, "location")(locationFrom)
                yield Header(title, authors, loc)
            case other => Left(s"Expected header object, got $other")

    private def authorFrom(value: Value): Either[String, Author] =
        value match
            case Value.Record(fields) =>
                for
                    fullname   <- optionalString(fields, "fullname")
                    initials   <- optionalString(fields, "initials")
                    firstname  <- optionalString(fields, "firstname")
                    middlename <- optionalString(fields, "middlename")
                    lastname   <- optionalString(fields, "lastname")
                    address    <- optionalString(fields, "address")
                yield Author(fullname, initials, firstname, middlename, lastname, address)
            case other => Left(s"Expected author object, got $other")

    private def columnSpecFrom(value: Value): Either[String, ColumnSpec] =
        value match
            case Value.Record(fields) =>
                for
                    width  <- optionalInt(fields, "width")
                    halign <- optionalString(fields, "halign").flatMap(_.fold[Either[String, Option[HAlign]]](Right(None))(halignFrom(_).map(Some(_))))
                    valign <- optionalString(fields, "valign").flatMap(_.fold[Either[String, Option[VAlign]]](Right(None))(valignFrom(_).map(Some(_))))
                    style  <- optionalString(fields, "style").flatMap(_.fold[Either[String, Option[CellStyle]]](Right(None))(cellStyleFrom(_).map(Some(_))))
                yield ColumnSpec(width, halign, valign, style)
            case other => Left(s"Expected column spec object, got $other")

    private def halignFrom(value: String): Either[String, HAlign] =
        value match
            case "left" | "Left"     => Right(HAlign.Left)
            case "center" | "Center" => Right(HAlign.Center)
            case "right" | "Right"   => Right(HAlign.Right)
            case other               => Left(s"Unknown halign value: $other")

    private def valignFrom(value: String): Either[String, VAlign] =
        value match
            case "top" | "Top"       => Right(VAlign.Top)
            case "middle" | "Middle" => Right(VAlign.Middle)
            case "bottom" | "Bottom" => Right(VAlign.Bottom)
            case other               => Left(s"Unknown valign value: $other")

    private def cellStyleFrom(value: String): Either[String, CellStyle] =
        value match
            case "default" | "Default"     => Right(CellStyle.Default)
            case "asciiDoc" | "AsciiDoc"   => Right(CellStyle.AsciiDoc)
            case "emphasis" | "Emphasis"   => Right(CellStyle.Emphasis)
            case "header" | "Header"       => Right(CellStyle.Header)
            case "literal" | "Literal"     => Right(CellStyle.Literal)
            case "monospace" | "Monospace" => Right(CellStyle.Monospace)
            case "strong" | "Strong"       => Right(CellStyle.Strong)
            case other                     => Left(s"Unknown style value: $other")

    private def documentFrom(fields: Chunk[(String, Value)]): Either[String, Document] =
        for
            attributes <- attributesFrom(fields, "attributes")
            header     <- optional(fields, "header")(headerFrom)
            blocks     <- optionalChunk(fields, "blocks")(blockFromValue)
            loc        <- fieldValue(fields, "location").flatMap(locationFrom)
        yield Document(attributes, header, blocks, loc)

    private def blockFromValue(value: Value): Either[String, Block] =
        value match
            case Value.Record(fields) => fieldString(fields, "name").flatMap(blockFrom(_, fields))
            case other                => Left(s"Expected block object, got $other")

    private def inlineFrom(value: Value): Either[String, Inline] =
        value match
            case Value.Record(fields) => fieldString(fields, "name").flatMap(inlineFrom(_, fields))
            case other                => Left(s"Expected inline object, got $other")

    private def inlineFrom(name: String, fields: Chunk[(String, Value)]): Either[String, Inline] =
        name match
            case "span" =>
                for
                    variant <- fieldString(fields, "variant")
                    form    <- fieldString(fields, "form")
                    items   <- optionalChunk(fields, "inlines")(inlineFrom)
                    loc     <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Span(variant, form, items, loc)
            case "ref" =>
                for
                    variant <- fieldString(fields, "variant")
                    target  <- fieldString(fields, "target")
                    items   <- optionalChunk(fields, "inlines")(inlineFrom)
                    loc     <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Ref(variant, target, items, loc)
            case "text" =>
                for
                    value <- fieldString(fields, "value")
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Text(value, loc)
            case "charref" =>
                for
                    value <- fieldString(fields, "value")
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield CharRef(value, loc)
            case "raw" =>
                for
                    value <- fieldString(fields, "value")
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Raw(value, loc)
            case other => Left(s"Unknown inline node name: $other")

    private def blockFrom(name: String, fields: Chunk[(String, Value)]): Either[String, Block] =
        for
            base <- blockBaseFrom(fields)
            node <- blockFrom(name, fields, base)
        yield node

    private def blockFrom(
        name: String,
        fields: Chunk[(String, Value)],
        base: (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata])
    ): Either[String, Block] =
        val (id, title, reftext, metadata) = base
        name match
            case "section" =>
                for
                    level <- fieldInt(fields, "level")
                    child <- optionalChunk(fields, "blocks")(blockFromValue)
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Section(id, title, reftext, metadata, level, child, loc)
            case "heading" =>
                for
                    level <- fieldInt(fields, "level")
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Heading(id, title, reftext, metadata, level, loc)
            case "paragraph" =>
                leafFrom(fields, id, title, reftext, metadata)(Paragraph.apply)
            case "listing" =>
                leafFrom(fields, id, title, reftext, metadata)(Listing.apply)
            case "literal" =>
                leafFrom(fields, id, title, reftext, metadata)(Literal.apply)
            case "pass" =>
                leafFrom(fields, id, title, reftext, metadata)(Pass.apply)
            case "stem" =>
                leafFrom(fields, id, title, reftext, metadata)(Stem.apply)
            case "verse" =>
                leafFrom(fields, id, title, reftext, metadata)(Verse.apply)
            case "sidebar" =>
                parentFrom(fields, id, title, reftext, metadata)(Sidebar.apply)
            case "example" =>
                parentFrom(fields, id, title, reftext, metadata)(Example.apply)
            case "admonition" =>
                for
                    form      <- fieldString(fields, "form")
                    delimiter <- fieldString(fields, "delimiter")
                    variant   <- fieldString(fields, "variant")
                    child     <- optionalChunk(fields, "blocks")(blockFromValue)
                    loc       <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Admonition(id, title, reftext, metadata, form, delimiter, variant, child, loc)
            case "open" =>
                parentFrom(fields, id, title, reftext, metadata)(Open.apply)
            case "quote" =>
                parentFrom(fields, id, title, reftext, metadata)(Quote.apply)
            case "list" =>
                for
                    variant <- fieldString(fields, "variant")
                    marker  <- fieldString(fields, "marker")
                    items   <- requiredChunkFrom(fields, "items")(blockFromValue)
                    loc     <- fieldValue(fields, "location").flatMap(locationFrom)
                yield List(id, title, reftext, metadata, variant, marker, items, loc)
            case "dlist" =>
                for
                    marker <- fieldString(fields, "marker")
                    items  <- requiredChunkFrom(fields, "items")(blockFromValue)
                    loc    <- fieldValue(fields, "location").flatMap(locationFrom)
                yield DList(id, title, reftext, metadata, marker, items, loc)
            case "listItem" =>
                for
                    marker    <- fieldString(fields, "marker")
                    principal <- requiredChunkFrom(fields, "principal")(inlineFrom)
                    child     <- optionalChunk(fields, "blocks")(blockFromValue)
                    loc       <- fieldValue(fields, "location").flatMap(locationFrom)
                yield ListItem(id, title, reftext, metadata, marker, principal, child, loc)
            case "dlistItem" =>
                for
                    marker    <- fieldString(fields, "marker")
                    terms     <- requiredChunkFrom(fields, "terms")(chunkOf(_)(inlineFrom))
                    principal <- optional(fields, "principal")(chunkOf(_)(inlineFrom))
                    child     <- optionalChunk(fields, "blocks")(blockFromValue)
                    loc       <- fieldValue(fields, "location").flatMap(locationFrom)
                yield DListItem(id, title, reftext, metadata, marker, terms, principal, child, loc)
            case "break" =>
                for
                    variant <- fieldString(fields, "variant")
                    loc     <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Break(id, title, reftext, metadata, variant, loc)
            case "table" =>
                for
                    form      <- optionalString(fields, "form").map(_.getOrElse("delimited"))
                    delimiter <- optionalString(fields, "delimiter").map(_.getOrElse("|==="))
                    columns   <- optional(fields, "columns")(chunkOf(_)(columnSpecFrom))
                    header    <- optional(fields, "header")(chunkOf(_)(blockFromValue))
                    rows      <- requiredChunkFrom(fields, "rows")(blockFromValue)
                    footer    <- optional(fields, "footer")(chunkOf(_)(blockFromValue))
                    frame     <- optionalString(fields, "frame")
                    grid      <- optionalString(fields, "grid")
                    stripes   <- optionalString(fields, "stripes")
                    loc       <- fieldValue(fields, "location").flatMap(locationFrom)
                yield Table(id, title, reftext, metadata, form, delimiter, columns, header, rows, footer, frame, grid, stripes, loc)
            case "tableRow" =>
                for
                    cells <- requiredChunkFrom(fields, "cells")(blockFromValue)
                    loc   <- fieldValue(fields, "location").flatMap(locationFrom)
                yield TableRow(id, title, reftext, metadata, cells, loc)
            case "tableCell" =>
                for
                    style    <- optionalString(fields, "style").flatMap(_.fold[Either[String, Option[CellStyle]]](Right(None))(cellStyleFrom(_).map(Some(_))))
                    colSpan  <- optionalInt(fields, "colSpan").map(_.map(ColSpan(_)))
                    rowSpan  <- optionalInt(fields, "rowSpan").map(_.map(RowSpan(_)))
                    dupCount <- optionalInt(fields, "dupCount").map(_.map(DupCount(_)))
                    items    <- optionalChunk(fields, "inlines")(inlineFrom)
                    child    <- optionalChunk(fields, "blocks")(blockFromValue)
                    loc      <- fieldValue(fields, "location").flatMap(locationFrom)
                yield TableCell.fromWire(id, title, reftext, metadata, style, colSpan, rowSpan, dupCount, items, child, loc)
            case "audio" =>
                macroFrom(fields, id, title, reftext, metadata)(Audio.apply)
            case "video" =>
                macroFrom(fields, id, title, reftext, metadata)(Video.apply)
            case "image" =>
                macroFrom(fields, id, title, reftext, metadata)(Image.apply)
            case "toc" =>
                macroFrom(fields, id, title, reftext, metadata)(Toc.apply)
            case other => Left(s"Unknown block node name: $other")

    private def leafFrom(
        fields: Chunk[(String, Value)],
        id: Option[String],
        title: Option[Chunk[Inline]],
        reftext: Option[Chunk[Inline]],
        metadata: Option[BlockMetadata]
    )(
        f: (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata], Option[String], Option[String], Chunk[Inline], Location) => Block
    ): Either[String, Block] =
        for
            form      <- optionalString(fields, "form")
            delimiter <- optionalString(fields, "delimiter")
            items     <- optionalChunk(fields, "inlines")(inlineFrom)
            loc       <- fieldValue(fields, "location").flatMap(locationFrom)
        yield f(id, title, reftext, metadata, form, delimiter, items, loc)

    private def parentFrom(
        fields: Chunk[(String, Value)],
        id: Option[String],
        title: Option[Chunk[Inline]],
        reftext: Option[Chunk[Inline]],
        metadata: Option[BlockMetadata]
    )(
        f: (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata], String, String, Chunk[Block], Location) => Block
    ): Either[String, Block] =
        for
            form      <- fieldString(fields, "form")
            delimiter <- fieldString(fields, "delimiter")
            child     <- optionalChunk(fields, "blocks")(blockFromValue)
            loc       <- fieldValue(fields, "location").flatMap(locationFrom)
        yield f(id, title, reftext, metadata, form, delimiter, child, loc)

    private def macroFrom(
        fields: Chunk[(String, Value)],
        id: Option[String],
        title: Option[Chunk[Inline]],
        reftext: Option[Chunk[Inline]],
        metadata: Option[BlockMetadata]
    )(
        f: (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata], String, Option[String], Location) => Block
    ): Either[String, Block] =
        for
            form   <- optionalString(fields, "form").map(_.getOrElse("macro"))
            target <- optionalString(fields, "target")
            loc    <- fieldValue(fields, "location").flatMap(locationFrom)
        yield f(id, title, reftext, metadata, form, target, loc)

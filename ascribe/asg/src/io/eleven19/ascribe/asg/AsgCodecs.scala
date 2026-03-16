package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.{Json, JsonDecoder, JsonEncoder}

/** JSON codec facade for ASG nodes.
  * Handles encoding ASG nodes to JSON strings and decoding JSON strings back to ASG nodes.
  * The JSON format matches the AsciiDoc TCK's expected ASG JSON schema.
  *
  * Uses manual encoding/decoding because the `name` and `type` discriminator fields
  * come from sealed abstract class constructor vals, which Schema.derived doesn't handle.
  */
object AsgCodecs:

  // Schema-derived encoders/decoders for leaf types
  private val positionEncoder = JsonEncoder.fromSchema(using summon[Schema[Position]])
  private val positionDecoder = JsonDecoder.fromSchema(using summon[Schema[Position]])
  private val locationEncoder = JsonEncoder.fromSchema(using summon[Schema[Chunk[Position]]])
  private val locationDecoder = JsonDecoder.fromSchema(using summon[Schema[Chunk[Position]]])
  private val blockMetadataEncoder = JsonEncoder.fromSchema(using summon[Schema[BlockMetadata]])
  private val blockMetadataDecoder = JsonDecoder.fromSchema(using summon[Schema[BlockMetadata]])
  private val authorEncoder = JsonEncoder.fromSchema(using summon[Schema[Author]])
  private val authorDecoder = JsonDecoder.fromSchema(using summon[Schema[Author]])

  /** Encode an ASG Node to a JSON string. */
  def encode(node: Node): String =
    encodeNode(node)

  /** Decode a JSON string to an ASG Node. */
  def decode(json: String): Either[String, Node] =
    Json.parse(json) match
      case Left(err) => Left(s"JSON parse error: $err")
      case Right(j)  => decodeNode(j)

  // --- Encoding helpers ---

  private def encodeJson(value: String): String = Json.from(value).toString
  private def encodeJson(value: Int): String = Json.from(value).toString

  private def encodeLocation(loc: Location): String =
    Json.from(loc)(using locationEncoder).toString

  private def encodeOptStr(key: String, value: Option[String]): String =
    value.fold("")(v => s""""$key":${encodeJson(v)},""")

  private def encodeOptInlines(key: String, value: Option[Chunk[Inline]]): String =
    value.fold("")(v => s""""$key":${encodeInlines(v)},""")

  private def encodeOptMetadata(key: String, value: Option[BlockMetadata]): String =
    value.fold("")(v => s""""$key":${Json.from(v)(using blockMetadataEncoder)},""")

  private def encodeInlines(inlines: Chunk[Inline]): String =
    inlines.map(encodeInline).mkString("[", ",", "]")

  private def encodeBlocks(blocks: Chunk[Block]): String =
    blocks.map(encodeBlock).mkString("[", ",", "]")

  private def encodeBlockCommon(b: Block): String =
    val sb = new StringBuilder
    b.id.foreach(v => sb.append(s""""id":${encodeJson(v)},"""))
    b.title.foreach(v => sb.append(s""""title":${encodeInlines(v)},"""))
    b.reftext.foreach(v => sb.append(s""""reftext":${encodeInlines(v)},"""))
    b.metadata.foreach(v => sb.append(s""""metadata":${Json.from(v)(using blockMetadataEncoder)},"""))
    sb.toString

  private def encodeInline(inline: Inline): String = inline match
    case Text(value, location) =>
      s"""{"name":"text","type":"string","value":${encodeJson(value)},"location":${encodeLocation(location)}}"""
    case CharRef(value, location) =>
      s"""{"name":"charref","type":"string","value":${encodeJson(value)},"location":${encodeLocation(location)}}"""
    case Raw(value, location) =>
      s"""{"name":"raw","type":"string","value":${encodeJson(value)},"location":${encodeLocation(location)}}"""
    case Span(variant, form, inlines, location) =>
      s"""{"name":"span","type":"inline","variant":${encodeJson(variant)},"form":${encodeJson(form)},"inlines":${encodeInlines(inlines)},"location":${encodeLocation(location)}}"""
    case Ref(variant, target, inlines, location) =>
      s"""{"name":"ref","type":"inline","variant":${encodeJson(variant)},"target":${encodeJson(target)},"inlines":${encodeInlines(inlines)},"location":${encodeLocation(location)}}"""

  private def encodeBlock(block: Block): String =
    val common = encodeBlockCommon(block)
    block match
      case Section(_, _, _, _, level, blocks, location) =>
        s"""{"name":"section","type":"block",${common}"level":${encodeJson(level)},"blocks":${encodeBlocks(blocks)},"location":${encodeLocation(location)}}"""
      case Heading(_, _, _, _, level, location) =>
        s"""{"name":"heading","type":"block",${common}"level":${encodeJson(level)},"location":${encodeLocation(location)}}"""
      case p: Paragraph =>
        val extras = new StringBuilder
        p.form.foreach(v => extras.append(s""""form":${encodeJson(v)},"""))
        p.delimiter.foreach(v => extras.append(s""""delimiter":${encodeJson(v)},"""))
        s"""{"name":"paragraph","type":"block",${common}${extras}"inlines":${encodeInlines(p.inlines)},"location":${encodeLocation(p.location)}}"""
      case l: Listing =>
        val extras = new StringBuilder
        l.form.foreach(v => extras.append(s""""form":${encodeJson(v)},"""))
        l.delimiter.foreach(v => extras.append(s""""delimiter":${encodeJson(v)},"""))
        s"""{"name":"listing","type":"block",${common}${extras}"inlines":${encodeInlines(l.inlines)},"location":${encodeLocation(l.location)}}"""
      case l: Literal =>
        val extras = new StringBuilder
        l.form.foreach(v => extras.append(s""""form":${encodeJson(v)},"""))
        l.delimiter.foreach(v => extras.append(s""""delimiter":${encodeJson(v)},"""))
        s"""{"name":"literal","type":"block",${common}${extras}"inlines":${encodeInlines(l.inlines)},"location":${encodeLocation(l.location)}}"""
      case p: Pass =>
        val extras = new StringBuilder
        p.form.foreach(v => extras.append(s""""form":${encodeJson(v)},"""))
        p.delimiter.foreach(v => extras.append(s""""delimiter":${encodeJson(v)},"""))
        s"""{"name":"pass","type":"block",${common}${extras}"inlines":${encodeInlines(p.inlines)},"location":${encodeLocation(p.location)}}"""
      case s: Stem =>
        val extras = new StringBuilder
        s.form.foreach(v => extras.append(s""""form":${encodeJson(v)},"""))
        s.delimiter.foreach(v => extras.append(s""""delimiter":${encodeJson(v)},"""))
        s"""{"name":"stem","type":"block",${common}${extras}"inlines":${encodeInlines(s.inlines)},"location":${encodeLocation(s.location)}}"""
      case v: Verse =>
        val extras = new StringBuilder
        v.form.foreach(ve => extras.append(s""""form":${encodeJson(ve)},"""))
        v.delimiter.foreach(ve => extras.append(s""""delimiter":${encodeJson(ve)},"""))
        s"""{"name":"verse","type":"block",${common}${extras}"inlines":${encodeInlines(v.inlines)},"location":${encodeLocation(v.location)}}"""
      case s: Sidebar =>
        s"""{"name":"sidebar","type":"block",${common}"form":${encodeJson(s.form)},"delimiter":${encodeJson(s.delimiter)},"blocks":${encodeBlocks(s.blocks)},"location":${encodeLocation(s.location)}}"""
      case e: Example =>
        s"""{"name":"example","type":"block",${common}"form":${encodeJson(e.form)},"delimiter":${encodeJson(e.delimiter)},"blocks":${encodeBlocks(e.blocks)},"location":${encodeLocation(e.location)}}"""
      case a: Admonition =>
        s"""{"name":"admonition","type":"block",${common}"form":${encodeJson(a.form)},"delimiter":${encodeJson(a.delimiter)},"variant":${encodeJson(a.variant)},"blocks":${encodeBlocks(a.blocks)},"location":${encodeLocation(a.location)}}"""
      case o: Open =>
        s"""{"name":"open","type":"block",${common}"form":${encodeJson(o.form)},"delimiter":${encodeJson(o.delimiter)},"blocks":${encodeBlocks(o.blocks)},"location":${encodeLocation(o.location)}}"""
      case q: Quote =>
        s"""{"name":"quote","type":"block",${common}"form":${encodeJson(q.form)},"delimiter":${encodeJson(q.delimiter)},"blocks":${encodeBlocks(q.blocks)},"location":${encodeLocation(q.location)}}"""
      case l: List =>
        s"""{"name":"list","type":"block",${common}"variant":${encodeJson(l.variant)},"marker":${encodeJson(l.marker)},"items":${l.items.map(encodeBlock).mkString("[", ",", "]")},"location":${encodeLocation(l.location)}}"""
      case d: DList =>
        s"""{"name":"dlist","type":"block",${common}"marker":${encodeJson(d.marker)},"items":${d.items.map(encodeBlock).mkString("[", ",", "]")},"location":${encodeLocation(d.location)}}"""
      case li: ListItem =>
        val extras = new StringBuilder
        if li.blocks.nonEmpty then extras.append(s""""blocks":${encodeBlocks(li.blocks)},""")
        s"""{"name":"listItem","type":"block",${common}"marker":${encodeJson(li.marker)},"principal":${encodeInlines(li.principal)},${extras}"location":${encodeLocation(li.location)}}"""
      case di: DListItem =>
        val extras = new StringBuilder
        val termsStr = di.terms.map(encodeInlines).mkString("[", ",", "]")
        di.principal.foreach(v => extras.append(s""""principal":${encodeInlines(v)},"""))
        if di.blocks.nonEmpty then extras.append(s""""blocks":${encodeBlocks(di.blocks)},""")
        s"""{"name":"dlistItem","type":"block",${common}"marker":${encodeJson(di.marker)},"terms":$termsStr,${extras}"location":${encodeLocation(di.location)}}"""
      case b: Break =>
        s"""{"name":"break","type":"block",${common}"variant":${encodeJson(b.variant)},"location":${encodeLocation(b.location)}}"""
      case a: Audio =>
        val extras = new StringBuilder
        a.target.foreach(v => extras.append(s""""target":${encodeJson(v)},"""))
        s"""{"name":"audio","type":"block",${common}"form":${encodeJson(a.form)},${extras}"location":${encodeLocation(a.location)}}"""
      case v: Video =>
        val extras = new StringBuilder
        v.target.foreach(ve => extras.append(s""""target":${encodeJson(ve)},"""))
        s"""{"name":"video","type":"block",${common}"form":${encodeJson(v.form)},${extras}"location":${encodeLocation(v.location)}}"""
      case i: Image =>
        val extras = new StringBuilder
        i.target.foreach(v => extras.append(s""""target":${encodeJson(v)},"""))
        s"""{"name":"image","type":"block",${common}"form":${encodeJson(i.form)},${extras}"location":${encodeLocation(i.location)}}"""
      case t: Toc =>
        val extras = new StringBuilder
        t.target.foreach(v => extras.append(s""""target":${encodeJson(v)},"""))
        s"""{"name":"toc","type":"block",${common}"form":${encodeJson(t.form)},${extras}"location":${encodeLocation(t.location)}}"""

  private def encodeNode(node: Node): String = node match
    case d: Document =>
      val extras = new StringBuilder
      d.attributes.foreach { attrs =>
        val attrEntries = attrs.map { (k, v) =>
          val valStr = v.fold("null")(s => encodeJson(s))
          s"${encodeJson(k)}:$valStr"
        }.mkString("{", ",", "}")
        extras.append(s""""attributes":$attrEntries,""")
      }
      d.header.foreach(h => extras.append(s""""header":${encodeHeader(h)},"""))
      s"""{"name":"document","type":"block",${extras}"blocks":${encodeBlocks(d.blocks)},"location":${encodeLocation(d.location)}}"""
    case b: Block  => encodeBlock(b)
    case i: Inline => encodeInline(i)

  private def encodeHeader(h: Header): String =
    val sb = new StringBuilder("{")
    var first = true
    h.title.foreach { t =>
      if !first then sb.append(",")
      sb.append(s""""title":${encodeInlines(t)}""")
      first = false
    }
    if h.authors.nonEmpty then
      if !first then sb.append(",")
      val authorsStr = h.authors.map(a => Json.from(a)(using authorEncoder).toString).mkString("[", ",", "]")
      sb.append(s""""authors":$authorsStr""")
      first = false
    h.location.foreach { loc =>
      if !first then sb.append(",")
      sb.append(s""""location":${encodeLocation(loc)}""")
      first = false
    }
    sb.append("}")
    sb.toString

  // --- Decoding helpers ---

  private def toStr[A](e: Either[SchemaError, A]): Either[String, A] =
    e.left.map(_.toString)

  private def fieldStr(fields: Chunk[(String, Json)], key: String): Either[String, String] =
    fields
      .collectFirst { case (k, v) if k == key => toStr(v.as[String]) }
      .getOrElse(Left(s"Missing field: $key"))

  private def fieldStrOpt(fields: Chunk[(String, Json)], key: String): Either[String, Option[String]] =
    fields.collectFirst { case (k, v) if k == key => toStr(v.as[String]).map(Some(_)) } match
      case Some(r) => r
      case None    => Right(None)

  private def fieldInt(fields: Chunk[(String, Json)], key: String): Either[String, Int] =
    fields
      .collectFirst { case (k, v) if k == key => toStr(v.as[Int]) }
      .getOrElse(Left(s"Missing field: $key"))

  private def fieldLocation(fields: Chunk[(String, Json)], key: String): Either[String, Location] =
    fields
      .collectFirst { case (k, v) if k == key => toStr(locationDecoder.decode(v)) }
      .getOrElse(Left(s"Missing field: $key"))

  private def fieldLocationOpt(fields: Chunk[(String, Json)], key: String): Either[String, Option[Location]] =
    fields.collectFirst { case (k, v) if k == key => toStr(locationDecoder.decode(v)).map(Some(_)) } match
      case Some(r) => r
      case None    => Right(None)

  private def fieldInlines(fields: Chunk[(String, Json)], key: String): Either[String, Chunk[Inline]] =
    fields.collectFirst { case (k, v) if k == key => decodeInlines(v) } match
      case Some(r) => r
      case None    => Right(Chunk.empty)

  private def fieldInlinesOpt(fields: Chunk[(String, Json)], key: String): Either[String, Option[Chunk[Inline]]] =
    fields.collectFirst { case (k, v) if k == key => decodeInlines(v).map(Some(_)) } match
      case Some(r) => r
      case None    => Right(None)

  private def fieldBlocks(fields: Chunk[(String, Json)], key: String): Either[String, Chunk[Block]] =
    fields.collectFirst { case (k, v) if k == key => decodeBlockArray(v) } match
      case Some(r) => r
      case None    => Right(Chunk.empty)

  private def fieldMetadataOpt(fields: Chunk[(String, Json)], key: String): Either[String, Option[BlockMetadata]] =
    fields.collectFirst { case (k, v) if k == key => toStr(blockMetadataDecoder.decode(v)).map(Some(_)) } match
      case Some(r) => r
      case None    => Right(None)

  private def decodeInlines(json: Json): Either[String, Chunk[Inline]] =
    val elems = json.elements
    val results = elems.map(decodeInlineJson)
    val errors = results.collect { case Left(e) => e }
    if errors.nonEmpty then Left(errors.mkString("; "))
    else Right(results.collect { case Right(v) => v })

  private def decodeBlockArray(json: Json): Either[String, Chunk[Block]] =
    val elems = json.elements
    val results = elems.map(decodeBlockJson)
    val errors = results.collect { case Left(e) => e }
    if errors.nonEmpty then Left(errors.mkString("; "))
    else Right(results.collect { case Right(v) => v })

  private def decodeInlineJson(json: Json): Either[String, Inline] =
    val fields = json.fields
    for
      name <- fieldStr(fields, "name")
      result <- name match
        case "text" =>
          for
            value <- fieldStr(fields, "value")
            loc <- fieldLocation(fields, "location")
          yield Text(value, loc)
        case "charref" =>
          for
            value <- fieldStr(fields, "value")
            loc <- fieldLocation(fields, "location")
          yield CharRef(value, loc)
        case "raw" =>
          for
            value <- fieldStr(fields, "value")
            loc <- fieldLocation(fields, "location")
          yield Raw(value, loc)
        case "span" =>
          for
            variant <- fieldStr(fields, "variant")
            form <- fieldStr(fields, "form")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Span(variant, form, inlines, loc)
        case "ref" =>
          for
            variant <- fieldStr(fields, "variant")
            target <- fieldStr(fields, "target")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Ref(variant, target, inlines, loc)
        case other => Left(s"Unknown inline name: $other")
    yield result

  private def decodeBlockCommon(fields: Chunk[(String, Json)]): Either[String, (Option[String], Option[Chunk[Inline]], Option[Chunk[Inline]], Option[BlockMetadata])] =
    for
      id <- fieldStrOpt(fields, "id")
      title <- fieldInlinesOpt(fields, "title")
      reftext <- fieldInlinesOpt(fields, "reftext")
      metadata <- fieldMetadataOpt(fields, "metadata")
    yield (id, title, reftext, metadata)

  private def decodeBlockJson(json: Json): Either[String, Block] =
    val fields = json.fields
    for
      name <- fieldStr(fields, "name")
      common <- decodeBlockCommon(fields)
      (id, title, reftext, metadata) = common
      result <- name match
        case "section" =>
          for
            level <- fieldInt(fields, "level")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Section(id, title, reftext, metadata, level, blocks, loc)
        case "heading" =>
          for
            level <- fieldInt(fields, "level")
            loc <- fieldLocation(fields, "location")
          yield Heading(id, title, reftext, metadata, level, loc)
        case "paragraph" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Paragraph(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "listing" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Listing(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "literal" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Literal(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "pass" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Pass(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "stem" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Stem(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "verse" =>
          for
            form <- fieldStrOpt(fields, "form")
            delimiter <- fieldStrOpt(fields, "delimiter")
            inlines <- fieldInlines(fields, "inlines")
            loc <- fieldLocation(fields, "location")
          yield Verse(id, title, reftext, metadata, form, delimiter, inlines, loc)
        case "sidebar" =>
          for
            form <- fieldStr(fields, "form")
            delimiter <- fieldStr(fields, "delimiter")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Sidebar(id, title, reftext, metadata, form, delimiter, blocks, loc)
        case "example" =>
          for
            form <- fieldStr(fields, "form")
            delimiter <- fieldStr(fields, "delimiter")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Example(id, title, reftext, metadata, form, delimiter, blocks, loc)
        case "admonition" =>
          for
            form <- fieldStr(fields, "form")
            delimiter <- fieldStr(fields, "delimiter")
            variant <- fieldStr(fields, "variant")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Admonition(id, title, reftext, metadata, form, delimiter, variant, blocks, loc)
        case "open" =>
          for
            form <- fieldStr(fields, "form")
            delimiter <- fieldStr(fields, "delimiter")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Open(id, title, reftext, metadata, form, delimiter, blocks, loc)
        case "quote" =>
          for
            form <- fieldStr(fields, "form")
            delimiter <- fieldStr(fields, "delimiter")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield Quote(id, title, reftext, metadata, form, delimiter, blocks, loc)
        case "list" =>
          for
            variant <- fieldStr(fields, "variant")
            marker <- fieldStr(fields, "marker")
            items <- decodeListItems(fields)
            loc <- fieldLocation(fields, "location")
          yield List(id, title, reftext, metadata, variant, marker, items, loc)
        case "dlist" =>
          for
            marker <- fieldStr(fields, "marker")
            items <- decodeDListItems(fields)
            loc <- fieldLocation(fields, "location")
          yield DList(id, title, reftext, metadata, marker, items, loc)
        case "listItem" =>
          for
            marker <- fieldStr(fields, "marker")
            principal <- fieldInlines(fields, "principal")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield ListItem(id, title, reftext, metadata, marker, principal, blocks, loc)
        case "dlistItem" =>
          for
            marker <- fieldStr(fields, "marker")
            terms <- decodeTerms(fields)
            principal <- fieldInlinesOpt(fields, "principal")
            blocks <- fieldBlocks(fields, "blocks")
            loc <- fieldLocation(fields, "location")
          yield DListItem(id, title, reftext, metadata, marker, terms, principal, blocks, loc)
        case "break" =>
          for
            variant <- fieldStr(fields, "variant")
            loc <- fieldLocation(fields, "location")
          yield Break(id, title, reftext, metadata, variant, loc)
        case "audio" =>
          for
            form <- fieldStr(fields, "form")
            target <- fieldStrOpt(fields, "target")
            loc <- fieldLocation(fields, "location")
          yield Audio(id, title, reftext, metadata, form, target, loc)
        case "video" =>
          for
            form <- fieldStr(fields, "form")
            target <- fieldStrOpt(fields, "target")
            loc <- fieldLocation(fields, "location")
          yield Video(id, title, reftext, metadata, form, target, loc)
        case "image" =>
          for
            form <- fieldStr(fields, "form")
            target <- fieldStrOpt(fields, "target")
            loc <- fieldLocation(fields, "location")
          yield Image(id, title, reftext, metadata, form, target, loc)
        case "toc" =>
          for
            form <- fieldStr(fields, "form")
            target <- fieldStrOpt(fields, "target")
            loc <- fieldLocation(fields, "location")
          yield Toc(id, title, reftext, metadata, form, target, loc)
        case other => Left(s"Unknown block name: $other")
    yield result

  private def decodeListItems(fields: Chunk[(String, Json)]): Either[String, Chunk[ListItem]] =
    fields.collectFirst { case (k, v) if k == "items" =>
      val elems = v.elements
      val results = elems.map { elem =>
        decodeBlockJson(elem).flatMap {
          case li: ListItem => Right(li)
          case other        => Left(s"Expected ListItem, got ${other.name}")
        }
      }
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.mkString("; "))
      else Right(results.collect { case Right(v) => v })
    }.getOrElse(Left("Missing field: items"))

  private def decodeDListItems(fields: Chunk[(String, Json)]): Either[String, Chunk[DListItem]] =
    fields.collectFirst { case (k, v) if k == "items" =>
      val elems = v.elements
      val results = elems.map { elem =>
        decodeBlockJson(elem).flatMap {
          case di: DListItem => Right(di)
          case other         => Left(s"Expected DListItem, got ${other.name}")
        }
      }
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.mkString("; "))
      else Right(results.collect { case Right(v) => v })
    }.getOrElse(Left("Missing field: items"))

  private def decodeTerms(fields: Chunk[(String, Json)]): Either[String, Chunk[Chunk[Inline]]] =
    fields.collectFirst { case (k, v) if k == "terms" =>
      val elems = v.elements
      val results = elems.map(decodeInlines)
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.mkString("; "))
      else Right(results.collect { case Right(v) => v })
    }.getOrElse(Right(Chunk.empty))

  private def decodeNode(json: Json): Either[String, Node] =
    val fields = json.fields
    for
      name <- fieldStr(fields, "name")
      result <- name match
        case "document" => decodeDocument(fields)
        case n if isInlineName(n) => decodeInlineJson(json)
        case _ => decodeBlockJson(json)
    yield result

  private def isInlineName(name: String): Boolean =
    name == "text" || name == "charref" || name == "raw" || name == "span" || name == "ref"

  private def decodeDocument(fields: Chunk[(String, Json)]): Either[String, Document] =
    for
      blocks <- fieldBlocks(fields, "blocks")
      loc <- fieldLocation(fields, "location")
      attrs <- decodeAttributesOpt(fields)
      header <- decodeHeaderOpt(fields)
    yield Document(attrs, header, blocks, loc)

  private def decodeAttributesOpt(fields: Chunk[(String, Json)]): Either[String, Option[Map[String, Option[String]]]] =
    fields.collectFirst { case (k, v) if k == "attributes" =>
      val attrFields = v.fields
      val entries = attrFields.map { (k, v) =>
        val valueOpt = toStr(v.as[String]) match
          case Right(s) => Some(s)
          case Left(_)  => None
        (k, valueOpt)
      }
      Right(Some(entries.toMap))
    } match
      case Some(r) => r
      case None    => Right(None)

  private def decodeHeaderOpt(fields: Chunk[(String, Json)]): Either[String, Option[Header]] =
    fields.collectFirst { case (k, v) if k == "header" =>
      val hFields = v.fields
      for
        title <- fieldInlinesOpt(hFields, "title")
        authors <- decodeAuthors(hFields)
        loc <- fieldLocationOpt(hFields, "location")
      yield Some(Header(title, authors, loc))
    } match
      case Some(r) => r
      case None    => Right(None)

  private def decodeAuthors(fields: Chunk[(String, Json)]): Either[String, Chunk[Author]] =
    fields.collectFirst { case (k, v) if k == "authors" =>
      val elems = v.elements
      val results = elems.map(e => toStr(authorDecoder.decode(e)))
      val errors = results.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.mkString("; "))
      else Right(results.collect { case Right(v) => v })
    } match
      case Some(r) => r
      case None    => Right(Chunk.empty)

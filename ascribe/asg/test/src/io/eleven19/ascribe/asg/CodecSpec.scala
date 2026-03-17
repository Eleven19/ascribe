package io.eleven19.ascribe.asg

import zio.blocks.chunk.Chunk
import munit.FunSuite

class CodecSpec extends FunSuite:

  val loc: Location = Location(Position(1, 1), Position(1, 10))

  test("Text roundtrip") {
    val text = Text("hello", loc)
    val json = AsgCodecs.encode(text: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(text))
  }

  test("Paragraph with text roundtrip") {
    val para = Paragraph(
      inlines = Chunk(Text("hello", loc)),
      location = loc
    )
    val json = AsgCodecs.encode(para: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(para))
  }

  test("Document with paragraph roundtrip") {
    val doc = Document(
      blocks = Chunk(
        Paragraph(inlines = Chunk(Text("hello", loc)), location = loc)
      ),
      location = loc
    )
    val json = AsgCodecs.encode(doc: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(doc))
  }

  test("Text JSON contains name and type fields") {
    val text = Text("hello", loc)
    val json = AsgCodecs.encode(text: Node)
    assert(json.contains("\"name\""), s"JSON should contain name field: $json")
    assert(json.contains("\"text\""), s"JSON should contain text value: $json")
    assert(json.contains("\"type\""), s"JSON should contain type field: $json")
    assert(json.contains("\"string\""), s"JSON should contain string value: $json")
    assert(json.contains("\"value\""), s"JSON should contain value field: $json")
    assert(json.contains("\"hello\""), s"JSON should contain hello value: $json")
  }

  test("Span with strong variant roundtrip") {
    val span = Span(
      variant = "strong",
      form = "unconstrained",
      inlines = Chunk(Text("bold", loc)),
      location = loc
    )
    val json = AsgCodecs.encode(span: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(span))
  }

  test("List with items roundtrip") {
    val list = List(
      variant = "unordered",
      marker = "*",
      items = Chunk(
        ListItem(marker = "*", principal = Chunk(Text("item", loc)), location = loc)
      ),
      location = loc
    )
    val json = AsgCodecs.encode(list: Node)
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(list))
  }

  test("CharRef roundtrip") {
    val charRef = CharRef("&amp;", loc)
    val json = AsgCodecs.encode(charRef: Node)
    assert(json.contains("\"name\":\"charref\""), s"JSON should use 'charref' name: $json")
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(charRef))
  }

  test("DList with DListItem roundtrip") {
    val dlist = DList(
      marker = "::",
      items = Chunk(
        DListItem(
          marker = "::",
          terms = Chunk(Chunk(Text("term", loc))),
          principal = Some(Chunk(Text("definition", loc))),
          location = loc
        )
      ),
      location = loc
    )
    val json = AsgCodecs.encode(dlist: Node)
    assert(json.contains("\"name\":\"dlist\""), s"JSON should use 'dlist' name: $json")
    val decoded = AsgCodecs.decode(json)
    assertEquals(decoded, Right(dlist))
  }

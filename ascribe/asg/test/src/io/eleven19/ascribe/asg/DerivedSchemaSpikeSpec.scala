package io.eleven19.ascribe.asg

import kyo.{Chunk, Codec, Json, Schema}
import munit.FunSuite

class DerivedSchemaSpikeSpec extends FunSuite:

    private val loc: Location = Location(Position(1, 1), Position(1, 10))

    test("derived inline schema can use ASG name discriminator and variant names") {
        import DerivedSchemaSpikeSpec.given

        val json = Json.encode[Inline](CharRef("&amp;", loc))

        assertEquals(
            json,
            """{"name":"charref","value":"&amp;","location":[{"line":1,"col":1},{"line":1,"col":10}],"nodeType":"string"}"""
        )
    }

    test("derived concrete schema can rename nodeType to type and reuse custom location schema") {
        import DerivedSchemaSpikeSpec.given

        val json = Json.encode[CharRef](CharRef("&amp;", loc))

        assert(json.contains(""""location":[{"line":1,"col":1},{"line":1,"col":10}]"""), s"expected ASG location shape: $json")
        assert(json.contains("\"type\":\"string\""), s"expected renamed type field: $json")
        assert(!json.contains("\"nodeType\""), s"expected nodeType to be renamed away: $json")
    }

object DerivedSchemaSpikeSpec:

    given Schema[Position] = Schema[Position]
    given Schema[Location] = Schema.init[Location](
        writeFn = { (location, writer) =>
            writer.arrayStart(2)
            writePosition(location.start, writer)
            writePosition(location.end, writer)
            writer.arrayEnd()
        },
        readFn = { reader =>
            val _ = reader.arrayStart()
            require(reader.hasNextElement(), "Location must contain a start position")
            val start = readPosition(reader)
            require(reader.hasNextElement(), "Location must contain an end position")
            val end = readPosition(reader)
            reader.arrayEnd()
            Location(start, end)
        }
    )
    given Schema[Text]     = Schema[Text].rename("nodeType", "type")
    given Schema[CharRef]  = Schema[CharRef].rename("nodeType", "type")
    given Schema[Raw]      = Schema[Raw].rename("nodeType", "type")
    given Schema[Span]     = Schema[Span].rename("nodeType", "type")
    given Schema[Ref]      = Schema[Ref].rename("nodeType", "type")

    given Schema[Inline] =
        Schema[Inline]
            .discriminator("name")
            .variantNames(
                "Text"    -> "text",
                "CharRef" -> "charref",
                "Raw"     -> "raw",
                "Span"    -> "span",
                "Ref"     -> "ref"
            )

    private def writePosition(position: Position, writer: Codec.Writer): Unit =
        writer.objectStart("", if position.file.isDefined then 3 else 2)
        writer.field("line", 0)
        writer.int(position.line)
        writer.field("col", 1)
        writer.int(position.col)
        position.file.foreach { files =>
            writer.field("file", 2)
            writer.arrayStart(files.size)
            files.foreach(writer.string)
            writer.arrayEnd()
        }
        writer.objectEnd()

    private def readPosition(reader: Codec.Reader): Position =
        val _ = reader.objectStart()
        var line = 0
        var col  = 0
        var file = Option.empty[Chunk[String]]
        while reader.hasNextField() do
            reader.field() match
                case "line" =>
                    line = reader.int()
                case "col" =>
                    col = reader.int()
                case "file" =>
                    val _ = reader.arrayStart()
                    var files = Chunk.empty[String]
                    while reader.hasNextElement() do
                        files = files.append(reader.string())
                    reader.arrayEnd()
                    file = Some(files)
                case _ =>
                    reader.skip()
        reader.objectEnd()
        Position(line, col, file)

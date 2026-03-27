package io.eleven19.ascribe.asg

import zio.blocks.schema.Schema
import zio.blocks.schema.json.{DiscriminatorKind, JsonBinaryCodec, JsonBinaryCodecDeriver, NameMapper}

/** JSON codec for ASG nodes using Schema-derived encoding/decoding. The JSON format matches the AsciiDoc TCK's expected
  * ASG JSON schema with "name" as the type discriminator and "type" as the node category field.
  */
object AsgCodecs:

    private def mapCaseName(s: String): String = s match
        case "DList"     => "dlist"
        case "DListItem" => "dlistItem"
        case "CharRef"   => "charref"
        case other       => other.head.toLower.toString + other.tail

    private val codec: JsonBinaryCodec[Node] = summon[Schema[Node]].derive(
        JsonBinaryCodecDeriver
            .withDiscriminatorKind(DiscriminatorKind.Field("name"))
            .withCaseNameMapper(NameMapper.Custom(mapCaseName))
            .withTransientDefaultValue(true)
    )

    /** Encode an ASG Node to a JSON string. */
    def encode(node: Node): String = new String(codec.encode(node).toArray)

    /** Encode a sequence of Inline nodes as a JSON array. Used for inline-only TCK tests. */
    def encodeInlines(inlines: zio.blocks.chunk.Chunk[Inline]): String =
        "[" + inlines.map(i => encode(i: Node)).mkString(",") + "]"

    /** Decode a JSON string to an ASG Node. */
    def decode(json: String): Either[String, Node] =
        codec.decode(json.getBytes).left.map(_.toString)

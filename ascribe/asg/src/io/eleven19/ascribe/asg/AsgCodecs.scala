package io.eleven19.ascribe.asg

import kyo.Chunk
import kyo.Structure.Value

/** JSON codec for ASG nodes. The JSON format matches the AsciiDoc TCK ASG shape with "name" as the node
  * discriminator and "type" as the node category field.
  */
object AsgCodecs:

    /** Encode an ASG Node to a JSON string. */
    def encode(node: Node): String =
        AsgJson.encode(AsgWire.toValue(node))

    /** Encode a sequence of Inline nodes as a JSON array. Used for inline-only TCK tests. */
    def encodeInlines(inlines: Chunk[Inline]): String =
        AsgJson.encode(Value.Sequence(inlines.map(inline => AsgWire.toValue(inline: Node))))

    /** Decode a JSON string to an ASG Node. */
    def decode(json: String): Either[String, Node] =
        AsgJson.decode(json).flatMap(AsgWire.fromValue)

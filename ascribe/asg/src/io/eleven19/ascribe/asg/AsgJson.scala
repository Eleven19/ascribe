package io.eleven19.ascribe.asg

import kyo.{Chunk, Codec, Json}
import kyo.Structure.Value

private[asg] object AsgJson:

    def encode(value: Value): String =
        val writer = summon[Json].newWriter()
        write(value, writer)
        new String(writer.result().toArray, java.nio.charset.StandardCharsets.UTF_8)

    def decode(input: String): Either[String, Value] =
        Parser(input).parse()

    private def write(value: Value, writer: Codec.Writer): Unit =
        value match
            case Value.Record(fields) =>
                writer.objectStart("", fields.size)
                fields.zipWithIndex.foreach { case ((name, fieldValue), index) =>
                    writer.field(name, index)
                    write(fieldValue, writer)
                }
                writer.objectEnd()
            case Value.VariantCase(name, variantValue) =>
                writer.objectStart("", 1)
                writer.field(name, 0)
                write(variantValue, writer)
                writer.objectEnd()
            case Value.Sequence(elements) =>
                writer.arrayStart(elements.size)
                elements.foreach(write(_, writer))
                writer.arrayEnd()
            case Value.MapEntries(entries) =>
                writer.objectStart("", entries.size)
                entries.zipWithIndex.foreach {
                    case ((Value.Str(key), fieldValue), index) =>
                        writer.field(key, index)
                        write(fieldValue, writer)
                    case ((key, fieldValue), index) =>
                        writer.field(key.toString, index)
                        write(fieldValue, writer)
                }
                writer.objectEnd()
            case Value.Str(value) =>
                writer.string(value)
            case Value.Bool(value) =>
                writer.boolean(value)
            case Value.Integer(value) =>
                writer.long(value)
            case Value.Decimal(value) =>
                writer.double(value)
            case Value.BigNum(value) =>
                writer.bigDecimal(value)
            case Value.Null =>
                writer.nil()

    private final class Parser(input: String):
        private var index = 0

        def parse(): Either[String, Value] =
            try
                val value = parseValue()
                skipWhitespace()
                if index == input.length then Right(value)
                else Left(s"Unexpected trailing JSON at offset $index")
            catch case error: JsonParseError => Left(error.getMessage)

        private def parseValue(): Value =
            skipWhitespace()
            if index >= input.length then fail("Unexpected end of JSON")
            input.charAt(index) match
                case '{' => parseObject()
                case '[' => parseArray()
                case '"' => Value.Str(parseString())
                case 't' => expectLiteral("true", Value.Bool(true))
                case 'f' => expectLiteral("false", Value.Bool(false))
                case 'n' => expectLiteral("null", Value.Null)
                case '-' => parseNumber()
                case c if c >= '0' && c <= '9' => parseNumber()
                case c => fail(s"Unexpected JSON character '$c'")

        private def parseObject(): Value =
            expect('{')
            skipWhitespace()
            if consume('}') then Value.Record(Chunk.empty)
            else
                var fields = Chunk.empty[(String, Value)]
                var done   = false
                while !done do
                    skipWhitespace()
                    val name = parseString()
                    skipWhitespace()
                    expect(':')
                    fields = fields :+ (name -> parseValue())
                    skipWhitespace()
                    if consume('}') then done = true
                    else expect(',')
                Value.Record(fields)

        private def parseArray(): Value =
            expect('[')
            skipWhitespace()
            if consume(']') then Value.Sequence(Chunk.empty)
            else
                var values = Chunk.empty[Value]
                var done   = false
                while !done do
                    values = values :+ parseValue()
                    skipWhitespace()
                    if consume(']') then done = true
                    else expect(',')
                Value.Sequence(values)

        private def parseString(): String =
            expect('"')
            val builder = new StringBuilder
            while index < input.length && input.charAt(index) != '"' do
                val char = input.charAt(index)
                if char == '\\' then
                    index += 1
                    if index >= input.length then fail("Unterminated JSON string escape")
                    input.charAt(index) match
                        case '"'  => builder.append('"')
                        case '\\' => builder.append('\\')
                        case '/'  => builder.append('/')
                        case 'b'  => builder.append('\b')
                        case 'f'  => builder.append('\f')
                        case 'n'  => builder.append('\n')
                        case 'r'  => builder.append('\r')
                        case 't'  => builder.append('\t')
                        case 'u'  => builder.append(parseUnicodeEscape())
                        case c    => fail(s"Invalid JSON string escape '\\$c'")
                else builder.append(char)
                index += 1
            expect('"')
            builder.toString

        private def parseUnicodeEscape(): Char =
            if index + 4 >= input.length then fail("Incomplete unicode escape")
            val hex = input.substring(index + 1, index + 5)
            index += 4
            try Integer.parseInt(hex, 16).toChar
            catch case _: NumberFormatException => fail(s"Invalid unicode escape '\\u$hex'")

        private def parseNumber(): Value =
            val start = index
            val _ = consume('-')
            digits()
            val decimal =
                if consume('.') then
                    digits()
                    true
                else false
            val exponent =
                if index < input.length && (input.charAt(index) == 'e' || input.charAt(index) == 'E') then
                    index += 1
                    if index < input.length && (input.charAt(index) == '+' || input.charAt(index) == '-') then index += 1
                    digits()
                    true
                else false
            val text = input.substring(start, index)
            if decimal || exponent then
                try Value.Decimal(text.toDouble)
                catch case _: NumberFormatException => fail(s"Invalid JSON number '$text'")
            else
                try Value.Integer(text.toLong)
                catch
                    case _: NumberFormatException =>
                        try Value.BigNum(BigDecimal(text))
                        catch case _: NumberFormatException => fail(s"Invalid JSON number '$text'")

        private def digits(): Unit =
            val start = index
            while index < input.length && input.charAt(index).isDigit do index += 1
            if start == index then fail("Expected JSON number digit")

        private def expectLiteral(literal: String, value: Value): Value =
            if input.startsWith(literal, index) then
                index += literal.length
                value
            else fail(s"Expected '$literal'")

        private def consume(char: Char): Boolean =
            if index < input.length && input.charAt(index) == char then
                index += 1
                true
            else false

        private def expect(char: Char): Unit =
            if !consume(char) then fail(s"Expected '$char'")

        private def skipWhitespace(): Unit =
            while index < input.length && input.charAt(index).isWhitespace do index += 1

        private def fail(message: String): Nothing =
            throw JsonParseError(s"$message at offset $index")

    private final case class JsonParseError(message: String) extends RuntimeException(message)

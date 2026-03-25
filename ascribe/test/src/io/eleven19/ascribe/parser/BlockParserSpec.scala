package io.eleven19.ascribe.parser

import parsley.{Failure, Success}
import zio.test.*

import io.eleven19.ascribe.cst.{CstAttributeEntry, CstBlock}
import io.eleven19.ascribe.parser.BlockParser.attributeEntryBlock

object BlockParserSpec extends ZIOSpecDefault:
    private def parse(input: String) = attributeEntryBlock.parse(input)

    def spec = suite("BlockParser")(
        suite("attributeEntryBlock")(
            test("parses normal attribute entry") {
                parse(":my-attr: some value\n") match
                    case Success(CstAttributeEntry("my-attr", "some value", false)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses unset attribute entry :!name:") {
                parse(":!my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", true)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            },
            test("parses empty-value attribute entry") {
                parse(":my-attr:\n") match
                    case Success(CstAttributeEntry("my-attr", "", false)) => assertTrue(true)
                    case Success(other) => assertTrue(s"unexpected: $other" == "")
                    case Failure(msg)   => assertTrue(s"Expected Success but got: $msg" == "")
            }
        )
    )

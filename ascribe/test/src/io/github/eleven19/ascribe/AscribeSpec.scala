package io.github.eleven19.ascribe

import zio.test.*
import parsley.Success

object AscribeSpec extends ZIOSpecDefault:
    def spec = suite("Ascribe")(
        test("hello parses 'ascribe'") {
            assertTrue(Ascribe.hello.parse("ascribe") == Success("ascribe"))
        }
    )

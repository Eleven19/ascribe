package io.github.eleven19.ascribe

import munit.FunSuite
import parsley.Success

class AscribeITest extends FunSuite:
    test("hello parses 'ascribe'") {
        assertEquals(Ascribe.hello.parse("ascribe"), Success("ascribe"))
    }

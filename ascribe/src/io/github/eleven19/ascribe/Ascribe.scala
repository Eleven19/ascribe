package io.github.eleven19.ascribe

import parsley.Parsley
import parsley.character.string

object Ascribe:
    val hello: Parsley[String] = string("ascribe")

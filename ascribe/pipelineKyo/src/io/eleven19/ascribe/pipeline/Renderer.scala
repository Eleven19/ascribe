package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.Document
import kyo.*

/** A pluggable output format renderer.
  *
  * Renderers transform a parsed Document into a string representation. Different implementations produce different
  * formats (AsciiDoc, ASG JSON, HTML, etc.). The effect type `S` allows renderers to carry effects if needed.
  */
trait Renderer[S]:
    /** Render a document to a string in this format. */
    def render(document: Document): String < S

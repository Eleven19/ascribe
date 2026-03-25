package io.eleven19.ascribe.pipeline

import io.eleven19.ascribe.ast.Document
import io.eleven19.ascribe.asg.AsgCodecs
import io.eleven19.ascribe.bridge.AstToAsg
import kyo.<

/** Renders a Document to ASG JSON format via the bridge converter and ASG codec.
  *
  * This produces the same JSON format used by the AsciiDoc TCK.
  */
object AsgJsonRenderer extends Renderer[Any]:

    def render(document: Document): String < Any =
        val asgDoc = AstToAsg.convert(document)
        AsgCodecs.encode(asgDoc)

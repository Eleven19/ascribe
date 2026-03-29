package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.Document
import io.eleven19.ascribe.asg.AsgCodecs
import io.eleven19.ascribe.bridge.AstToAsg

/** Renders a Document to ASG JSON (TCK-compatible), same as the Kyo module. */
object AsgJsonRenderer extends Renderer:

    def render(document: Document): String =
        val asgDoc = AstToAsg.convert(document)
        AsgCodecs.encode(asgDoc)

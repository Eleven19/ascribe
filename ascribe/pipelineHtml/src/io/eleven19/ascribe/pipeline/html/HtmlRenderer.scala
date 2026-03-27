package io.eleven19.ascribe.pipeline.html

import io.eleven19.ascribe.ast.Document
import scalatags.Text.all.*

/** Renders an Ascribe [[Document]] to HTML using scalatags (no effect stack). */
object HtmlRenderer:

    def render(document: Document): Frag =
        html(
            head(tag("meta")(charset := "utf-8")),
            body(
                h1("Ascribe"),
                p("HTML output is a minimal scaffold; extend with real AST traversal as needed.")
            )
        )

package io.eleven19.ascribe.pipeline.ox

import io.eleven19.ascribe.ast.Document

/** Pluggable output format renderer (pure direct style). */
trait Renderer:
    def render(document: Document): String
